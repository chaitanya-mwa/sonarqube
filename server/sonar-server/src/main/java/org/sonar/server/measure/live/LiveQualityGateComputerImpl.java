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
package org.sonar.server.measure.live;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.commons.lang.StringUtils;
import org.sonar.core.util.stream.MoreCollectors;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.measure.LiveMeasureDto;
import org.sonar.db.metric.MetricDto;
import org.sonar.db.qualitygate.QualityGateConditionDto;
import org.sonar.db.qualitygate.QualityGateDto;
import org.sonar.server.computation.task.projectanalysis.measure.Measure;
import org.sonar.server.computation.task.projectanalysis.qualitygate.EvaluationResult;
import org.sonar.server.computation.task.projectanalysis.qualitygate.QualityGateStatus;
import org.sonar.server.qualitygate.QualityGateFinder;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.sonar.api.measures.CoreMetrics.ALERT_STATUS_KEY;
import static org.sonar.api.measures.CoreMetrics.QUALITY_GATE_DETAILS_KEY;

public class LiveQualityGateComputerImpl implements LiveQualityGateComputer {

  private final DbClient dbClient;
  private final QualityGateFinder qualityGateFinder;

  public LiveQualityGateComputerImpl(DbClient dbClient, QualityGateFinder qualityGateFinder) {
    this.dbClient = dbClient;
    this.qualityGateFinder = qualityGateFinder;
  }

  @Override
  public void recalculateQualityGate(DbSession dbSession, ComponentDto project, Collection<LiveMeasureDto> modifiedMeasures) {
    Optional<QualityGateFinder.QualityGateData> qualityGateOptional = qualityGateFinder.getQualityGate(dbSession, project.getId());
    if (!qualityGateOptional.isPresent()) {
      return;
    }
    QualityGateDto qualityGate = qualityGateOptional.get().getQualityGate();

    Collection<QualityGateConditionDto> conditions = dbClient.gateConditionDao().selectForQualityGate(dbSession, qualityGate.getId());

    Set<Integer> modifiedMetricIds = modifiedMeasures.stream().map(LiveMeasureDto::getMetricId).collect(Collectors.toSet());
    Set<Integer> unmodifiedMetricIds = conditions.stream().map(QualityGateConditionDto::getMetricId)
      .map(l -> (int) (long) l)
      .filter(metricId -> !modifiedMetricIds.contains(metricId))
      .collect(Collectors.toSet());
    List<MetricDto> metricDtos1 = dbClient.metricDao().selectByKeys(dbSession, asList(ALERT_STATUS_KEY, QUALITY_GATE_DETAILS_KEY));
    List<MetricDto> metricDtos2 = dbClient.metricDao().selectByIds(dbSession, addToSet(modifiedMetricIds, unmodifiedMetricIds));
    Map<Integer, MetricDto> metricDtosPerMetricId = Stream.concat(metricDtos1.stream(), metricDtos2.stream()).collect(MoreCollectors.uniqueIndex(MetricDto::getId));
    Map<String, MetricDto> metricDtosPerMetricKey = Stream.concat(metricDtos1.stream(), metricDtos2.stream()).collect(MoreCollectors.uniqueIndex(MetricDto::getKey));
    MetricDto qualityGateStatusMetric = metricDtosPerMetricKey.get(ALERT_STATUS_KEY);
    MetricDto qualityGateDetailsMetric = metricDtosPerMetricKey.get(QUALITY_GATE_DETAILS_KEY);

    List<LiveMeasureDto> unmodifiedLiveMeasureDtos = dbClient.liveMeasureDao().selectByComponentUuids(dbSession, singletonList(project.uuid()), addToSet(addToSet(unmodifiedMetricIds, qualityGateStatusMetric.getId()), qualityGateDetailsMetric.getId()));
    Map<Integer, LiveMeasureDto> modifiedLiveMeasuresPerMetricId = modifiedMeasures.stream().collect(MoreCollectors.uniqueIndex(LiveMeasureDto::getMetricId));
    Map<Integer, LiveMeasureDto> unmodifiedLiveMeasuresPerMetricId = unmodifiedLiveMeasureDtos.stream().collect(MoreCollectors.uniqueIndex(LiveMeasureDto::getMetricId));

    QualityGateDetailsDataBuilder builder = new QualityGateDetailsDataBuilder();

    conditions.stream()
      .forEach(condition -> {
        condition.setMetricKey(metricDtosPerMetricId.get((int) condition.getMetricId()).getKey());
        EvaluationResult evaluationResult = getEvaluationResult(condition, modifiedMetricIds, metricDtosPerMetricId, modifiedLiveMeasuresPerMetricId, unmodifiedLiveMeasuresPerMetricId, dbSession);
        MetricEvaluationResult evaluationResultWithMetric = new MetricEvaluationResult(evaluationResult, condition);
        builder.addEvaluatedCondition(evaluationResultWithMetric);
      });

    Measure.Level globalLevel = builder.getGlobalLevel();
    String globalLevelName = convert(globalLevel).name();

    LiveMeasureDto qualityGateStatusMeasure = unmodifiedLiveMeasuresPerMetricId.get(qualityGateStatusMetric.getId());
    if (qualityGateStatusMeasure == null) {
      LiveMeasureDto newQualityGateStatusMeasure = LiveMeasureDto.create()
        .setData(globalLevelName)
        .setComponentUuid(project.uuid())
        .setProjectUuid(project.uuid())
        .setMetricId(qualityGateStatusMetric.getId());
      dbClient.liveMeasureDao().insert(dbSession, newQualityGateStatusMeasure);
    } else {
      qualityGateStatusMeasure.setData(globalLevelName);
      dbClient.liveMeasureDao().update(dbSession, qualityGateStatusMeasure);
    }

    String jsonDetails = new QualityGateDetailsData(globalLevel, builder.getEvaluatedConditions(), builder.isIgnoredConditions()).toJson();
    LiveMeasureDto qualityGateDetailsMeasure = unmodifiedLiveMeasuresPerMetricId.get(qualityGateDetailsMetric.getId());
    if (qualityGateDetailsMeasure == null) {
      LiveMeasureDto newQualityGateDetailsMeasure = LiveMeasureDto.create()
        .setData(jsonDetails)
        .setComponentUuid(project.uuid())
        .setProjectUuid(project.uuid())
        .setMetricId(qualityGateDetailsMetric.getId());
      dbClient.liveMeasureDao().insert(dbSession, newQualityGateDetailsMeasure);
    } else {
      qualityGateDetailsMeasure.setData(jsonDetails);
      dbClient.liveMeasureDao().update(dbSession, qualityGateDetailsMeasure);
    }
  }

  private EvaluationResult getEvaluationResult(QualityGateConditionDto condition, Set<Integer> modifiedMetricIds, Map<Integer, MetricDto> metricDtosPerMetricId, Map<Integer, LiveMeasureDto> modifiedLiveMeasuresPerMetricId, Map<Integer, LiveMeasureDto> unmodifiedLiveMeasuresPerMetricId, DbSession dbSession) {
    int metricId = (int) condition.getMetricId();
    EvaluationResult evaluationResult;
    if (modifiedMetricIds.contains(metricId)) {
      MetricDto metricDto = metricDtosPerMetricId.get(metricId);
      LiveMeasureDto modifiedMeasure = modifiedLiveMeasuresPerMetricId.get(metricId);
      evaluationResult = new LiveConditionEvaluator().evaluate(metricDto, condition, modifiedMeasure);
      modifiedMeasure.setGateStatus(convert(evaluationResult.getLevel()).name());
      modifiedMeasure.setGateText("FIXME quality gate text");
      dbClient.liveMeasureDao().update(dbSession, modifiedMeasure);
    } else {
      LiveMeasureDto unmodifiedMeasure = unmodifiedLiveMeasuresPerMetricId.get(metricId);
      if (unmodifiedMeasure == null) {
        evaluationResult = new EvaluationResult(Measure.Level.OK, null);
      } else {
        evaluationResult = new EvaluationResult(convertLevel(unmodifiedMeasure.getGateStatus()), unmodifiedMeasure.getTextValue());
      }
    }
    return evaluationResult;
  }

  private QualityGateStatus convert(Measure.Level level) {
    if (level == Measure.Level.ERROR) {
      return QualityGateStatus.ERROR;
    }
    if (level == Measure.Level.WARN) {
      return QualityGateStatus.WARN;
    }
    return QualityGateStatus.OK;
  }

  private Measure.Level convertLevel(String gateStatus) {
    if (Measure.Level.ERROR.name().equals(gateStatus)) {
      return Measure.Level.ERROR;
    }
    if (Measure.Level.WARN.name().equals(gateStatus)) {
      return Measure.Level.WARN;
    }
    return Measure.Level.OK;
  }

  private QualityGateStatus convert(String gateStatus) {
    if (QualityGateStatus.ERROR.name().equals(gateStatus)) {
      return QualityGateStatus.ERROR;
    }
    if (QualityGateStatus.WARN.name().equals(gateStatus)) {
        return QualityGateStatus.WARN;
    }
    return QualityGateStatus.OK;
  }

  private <X> Set<X> addToSet(Set<X> set, X item) {
    HashSet<X> copy = new HashSet<>(set);
    copy.add(item);
    return copy;
  }

  private <X> Set<X> addToSet(Set<X> a, Set<X> b) {
    HashSet<X> copy = new HashSet<>(a);
    copy.addAll(b);
    return copy;
  }

  public static final class QualityGateDetailsDataBuilder {
    private Measure.Level globalLevel = Measure.Level.OK;
    private List<String> labels = new ArrayList<>();
    private List<EvaluatedCondition> evaluatedConditions = new ArrayList<>();
    private boolean ignoredConditions;

    public Measure.Level getGlobalLevel() {
      return globalLevel;
    }

    public void addLabel(@Nullable String label) {
      if (StringUtils.isNotBlank(label)) {
        labels.add(label);
      }
    }

    public List<String> getLabels() {
      return labels;
    }

    public void addEvaluatedCondition(MetricEvaluationResult metricEvaluationResult) {
      Measure.Level level = metricEvaluationResult.evaluationResult.getLevel();
      if (Measure.Level.WARN == level && this.globalLevel != Measure.Level.ERROR) {
        globalLevel = Measure.Level.WARN;

      } else if (Measure.Level.ERROR == level) {
        globalLevel = Measure.Level.ERROR;
      }
      evaluatedConditions.add(
        new EvaluatedCondition(metricEvaluationResult.condition, level, metricEvaluationResult.evaluationResult.getValue()));
    }

    public List<EvaluatedCondition> getEvaluatedConditions() {
      return evaluatedConditions;
    }

    public boolean isIgnoredConditions() {
      return ignoredConditions;
    }

    public QualityGateDetailsDataBuilder setIgnoredConditions(boolean ignoredConditions) {
      this.ignoredConditions = ignoredConditions;
      return this;
    }
  }

  public static class MetricEvaluationResult {
    final EvaluationResult evaluationResult;
    final QualityGateConditionDto condition;

    public MetricEvaluationResult(EvaluationResult evaluationResult, QualityGateConditionDto condition) {
      this.evaluationResult = evaluationResult;
      this.condition = condition;
    }
  }
}
