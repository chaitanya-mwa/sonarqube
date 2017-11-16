/*
 * SonarQube
 * Copyright (C) 2009-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.measure.ws;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.server.ws.Change;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.web.UserRole;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.SnapshotDto;
import org.sonar.db.measure.CurrentMeasureDto;
import org.sonar.db.metric.MetricDto;
import org.sonar.server.component.ComponentFinder;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.user.UserSession;
import org.sonarqube.ws.WsMeasures;
import org.sonarqube.ws.WsMeasures.ComponentWsResponse;
import org.sonarqube.ws.client.measure.ComponentWsRequest;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.sonar.core.util.Uuids.UUID_EXAMPLE_01;
import static org.sonar.server.component.ComponentFinder.ParamNames.COMPONENT_ID_AND_COMPONENT;
import static org.sonar.server.component.ComponentFinder.ParamNames.DEVELOPER_ID_AND_KEY;
import static org.sonar.server.measure.ws.ComponentDtoToWsComponent.componentDtoToWsComponent;
import static org.sonar.server.measure.ws.MeasuresWsParametersBuilder.createAdditionalFieldsParameter;
import static org.sonar.server.measure.ws.MeasuresWsParametersBuilder.createDeveloperParameters;
import static org.sonar.server.measure.ws.MeasuresWsParametersBuilder.createMetricKeysParameter;
import static org.sonar.server.measure.ws.MetricDtoToWsMetric.metricDtoToWsMetric;
import static org.sonar.server.measure.ws.SnapshotDtoToWsPeriods.snapshotToWsPeriods;
import static org.sonar.server.ws.KeyExamples.KEY_BRANCH_EXAMPLE_001;
import static org.sonar.server.ws.KeyExamples.KEY_PROJECT_EXAMPLE_001;
import static org.sonar.server.ws.WsUtils.checkRequest;
import static org.sonar.server.ws.WsUtils.writeProtobuf;
import static org.sonarqube.ws.client.measure.MeasuresWsParameters.ACTION_COMPONENT;
import static org.sonarqube.ws.client.measure.MeasuresWsParameters.ADDITIONAL_METRICS;
import static org.sonarqube.ws.client.measure.MeasuresWsParameters.ADDITIONAL_PERIODS;
import static org.sonarqube.ws.client.measure.MeasuresWsParameters.DEPRECATED_PARAM_COMPONENT_ID;
import static org.sonarqube.ws.client.measure.MeasuresWsParameters.DEPRECATED_PARAM_COMPONENT_KEY;
import static org.sonarqube.ws.client.measure.MeasuresWsParameters.PARAM_ADDITIONAL_FIELDS;
import static org.sonarqube.ws.client.measure.MeasuresWsParameters.PARAM_BRANCH;
import static org.sonarqube.ws.client.measure.MeasuresWsParameters.PARAM_COMPONENT;
import static org.sonarqube.ws.client.measure.MeasuresWsParameters.PARAM_DEVELOPER_ID;
import static org.sonarqube.ws.client.measure.MeasuresWsParameters.PARAM_DEVELOPER_KEY;
import static org.sonarqube.ws.client.measure.MeasuresWsParameters.PARAM_METRIC_KEYS;

public class ComponentAction implements MeasuresWsAction {
  private final DbClient dbClient;
  private final ComponentFinder componentFinder;
  private final UserSession userSession;

  public ComponentAction(DbClient dbClient, ComponentFinder componentFinder, UserSession userSession) {
    this.dbClient = dbClient;
    this.componentFinder = componentFinder;
    this.userSession = userSession;
  }

  @Override
  public void define(WebService.NewController context) {
    WebService.NewAction action = context.createAction(ACTION_COMPONENT)
      .setDescription(format("Return component with specified measures. The %s or the %s parameter must be provided.<br>" +
        "Requires the following permission: 'Browse' on the project of specified component.",
        DEPRECATED_PARAM_COMPONENT_ID, PARAM_COMPONENT))
      .setResponseExample(getClass().getResource("component-example.json"))
      .setSince("5.4")
      .setChangelog(
        new Change("6.6", "the response field id is deprecated. Use key instead."),
        new Change("6.6", "the response field refId is deprecated. Use refKey instead."))
      .setHandler(this);

    action.createParam(PARAM_COMPONENT)
      .setDescription("Component key")
      .setExampleValue(KEY_PROJECT_EXAMPLE_001)
      .setDeprecatedKey(DEPRECATED_PARAM_COMPONENT_KEY, "6.6");

    action.createParam(DEPRECATED_PARAM_COMPONENT_ID)
      .setDescription("Component id")
      .setExampleValue(UUID_EXAMPLE_01)
      .setDeprecatedSince("6.6");

    action.createParam(PARAM_BRANCH)
      .setDescription("Branch key")
      .setExampleValue(KEY_BRANCH_EXAMPLE_001)
      .setInternal(true)
      .setSince("6.6");

    createMetricKeysParameter(action);
    createAdditionalFieldsParameter(action);
    createDeveloperParameters(action);
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    ComponentWsResponse componentWsResponse = doHandle(toComponentWsRequest(request));
    writeProtobuf(componentWsResponse, request, response);
  }

  private ComponentWsResponse doHandle(ComponentWsRequest request) {
    try (DbSession dbSession = dbClient.openSession(false)) {
      ComponentDto component = loadComponent(dbSession, request);
      Long developerId = searchDeveloperId(dbSession, request);
      Optional<ComponentDto> refComponent = getReferenceComponent(dbSession, component);
      checkPermissions(component);
      SnapshotDto analysis = dbClient.snapshotDao().selectLastAnalysisByRootComponentUuid(dbSession, component.projectUuid()).orElse(null);
      List<MetricDto> metrics = searchMetrics(dbSession, request);
      List<WsMeasures.Period> periods = snapshotToWsPeriods(analysis);
      List<CurrentMeasureDto> measures = searchMeasures(dbSession, component, analysis, metrics, developerId);

      return buildResponse(request, component, refComponent, measures, metrics, periods);
    }
  }

  private ComponentDto loadComponent(DbSession dbSession, ComponentWsRequest request) {
    String componentKey = request.getComponent();
    String componentId = request.getComponentId();
    String branch = request.getBranch();
    checkArgument(componentId == null || branch == null, "'%s' and '%s' parameters cannot be used at the same time", DEPRECATED_PARAM_COMPONENT_ID, PARAM_BRANCH);
    return branch == null
      ? componentFinder.getByUuidOrKey(dbSession, componentId, componentKey, COMPONENT_ID_AND_COMPONENT)
      : componentFinder.getByKeyAndBranch(dbSession, componentKey, branch);
  }

  @CheckForNull
  private Long searchDeveloperId(DbSession dbSession, ComponentWsRequest request) {
    if (request.getDeveloperId() == null && request.getDeveloperKey() == null) {
      return null;
    }

    return componentFinder.getByUuidOrKey(dbSession, request.getDeveloperId(), request.getDeveloperKey(), DEVELOPER_ID_AND_KEY).getId();
  }

  private Optional<ComponentDto> getReferenceComponent(DbSession dbSession, ComponentDto component) {
    if (component.getCopyResourceUuid() == null) {
      return Optional.absent();
    }

    return dbClient.componentDao().selectByUuid(dbSession, component.getCopyResourceUuid());
  }

  private static ComponentWsResponse buildResponse(ComponentWsRequest request, ComponentDto component, Optional<ComponentDto> refComponent, List<CurrentMeasureDto> measures,
    List<MetricDto> metrics, List<WsMeasures.Period> periods) {
    ComponentWsResponse.Builder response = ComponentWsResponse.newBuilder();
    Map<Integer, MetricDto> metricsById = Maps.uniqueIndex(metrics, MetricDto::getId);
    Map<MetricDto, CurrentMeasureDto> measuresByMetric = new HashMap<>();
    for (CurrentMeasureDto measure : measures) {
      MetricDto metric = metricsById.get(measure.getMetricId());
      measuresByMetric.put(metric, measure);
    }
    if (refComponent.isPresent()) {
      response.setComponent(componentDtoToWsComponent(component, measuresByMetric, singletonMap(refComponent.get().uuid(), refComponent.get())));
    } else {
      response.setComponent(componentDtoToWsComponent(component, measuresByMetric, emptyMap()));
    }

    List<String> additionalFields = request.getAdditionalFields();
    if (additionalFields != null) {
      if (additionalFields.contains(ADDITIONAL_METRICS)) {
        for (MetricDto metric : metrics) {
          response.getMetricsBuilder().addMetrics(metricDtoToWsMetric(metric));
        }
      }
      if (additionalFields.contains(ADDITIONAL_PERIODS)) {
        response.getPeriodsBuilder().addAllPeriods(periods);
      }
    }

    return response.build();
  }

  private List<MetricDto> searchMetrics(DbSession dbSession, ComponentWsRequest request) {
    List<MetricDto> metrics = dbClient.metricDao().selectByKeys(dbSession, request.getMetricKeys());
    if (metrics.size() < request.getMetricKeys().size()) {
      List<String> foundMetricKeys = Lists.transform(metrics, MetricDto::getKey);
      Set<String> missingMetricKeys = Sets.difference(
        new LinkedHashSet<>(request.getMetricKeys()),
        new LinkedHashSet<>(foundMetricKeys));

      throw new NotFoundException(format("The following metric keys are not found: %s", Joiner.on(", ").join(missingMetricKeys)));
    }

    return metrics;
  }

  private List<CurrentMeasureDto> searchMeasures(DbSession dbSession, ComponentDto component, @Nullable SnapshotDto analysis, List<MetricDto> metrics, @Nullable Long developerId) {
    if (analysis == null) {
      return emptyList();
    }

    List<Integer> metricIds = Lists.transform(metrics, MetricDto::getId);
    return dbClient.currentMeasureDao().selectByComponentUuids(dbSession, singletonList(component.uuid()), metricIds);
  }

  private static ComponentWsRequest toComponentWsRequest(Request request) {
    ComponentWsRequest componentWsRequest = new ComponentWsRequest()
      .setComponentId(request.param(DEPRECATED_PARAM_COMPONENT_ID))
      .setComponent(request.param(PARAM_COMPONENT))
      .setBranch(request.param(PARAM_BRANCH))
      .setAdditionalFields(request.paramAsStrings(PARAM_ADDITIONAL_FIELDS))
      .setMetricKeys(request.mandatoryParamAsStrings(PARAM_METRIC_KEYS))
      .setDeveloperId(request.param(PARAM_DEVELOPER_ID))
      .setDeveloperKey(request.param(PARAM_DEVELOPER_KEY));
    checkRequest(!componentWsRequest.getMetricKeys().isEmpty(), "At least one metric key must be provided");
    return componentWsRequest;
  }

  private void checkPermissions(ComponentDto baseComponent) {
    userSession.checkComponentPermission(UserRole.USER, baseComponent);
  }
}
