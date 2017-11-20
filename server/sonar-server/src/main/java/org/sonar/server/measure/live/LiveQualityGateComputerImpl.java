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

import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonar.core.util.stream.MoreCollectors;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.measure.LiveMeasureDto;
import org.sonar.db.metric.MetricDto;
import org.sonar.db.qualitygate.QualityGateConditionDto;
import org.sonar.server.computation.task.projectanalysis.qualitygate.QualityGateStatus;

import static java.util.Collections.singletonList;
import static org.sonar.api.measures.CoreMetrics.ALERT_STATUS_KEY;

public class LiveQualityGateComputerImpl implements LiveQualityGateComputer {

  private final DbClient dbClient;

  public LiveQualityGateComputerImpl(DbClient dbClient) {
    this.dbClient = dbClient;
  }

  public void recalculateQualityGate(DbSession dbSession, ComponentDto project, Collection<LiveMeasureDto> modifiedMeasures) {
    Optional<Long> qGateId = dbClient.projectQgateAssociationDao().selectQGateIdByComponentId(dbSession, project.getId());
    if (!qGateId.isPresent()) {
      // No quality gate? No calculation to do!
      return;
    }

    Collection<QualityGateConditionDto> conditions = dbClient.gateConditionDao().selectForQualityGate(dbSession, qGateId.get());

    Set<Integer> modifiedMetricIds = modifiedMeasures.stream().map(LiveMeasureDto::getMetricId).collect(Collectors.toSet());
    Set<Integer> unmodifiedMetricIds = conditions.stream().map(QualityGateConditionDto::getMetricId)
      .filter(metricId -> !modifiedMetricIds.contains(metricId))
      .map(l -> (int) (long) l).collect(Collectors.toSet());
    MetricDto qualityGateStatusMetric = dbClient.metricDao().selectByKey(dbSession, ALERT_STATUS_KEY);

    List<LiveMeasureDto> unmodifiedLiveMeasureDtos = dbClient.liveMeasureDao().selectByComponentUuids(dbSession, singletonList(project.uuid()), addToSet(unmodifiedMetricIds, qualityGateStatusMetric.getId()));
    ImmutableMap<Integer, LiveMeasureDto> modifiedLiveMeasuresPerMetricId = modifiedMeasures.stream().collect(MoreCollectors.uniqueIndex(LiveMeasureDto::getMetricId));
    ImmutableMap<Integer, LiveMeasureDto> unmodifiedLiveMeasuresPerMetricId = unmodifiedLiveMeasureDtos.stream().collect(MoreCollectors.uniqueIndex(LiveMeasureDto::getMetricId));
    LiveMeasureDto qualityGateStatusMeasure = unmodifiedLiveMeasuresPerMetricId.get(qualityGateStatusMetric.getId());
    if (qualityGateStatusMeasure == null) {
      throw new IllegalStateException("Expected exactly one quality gate status for component " + project.getKey());
    }

    conditions.stream()
      .map(condition -> {
        if (modifiedMetricIds.contains((int) condition.getMetricId())) {
          return recalculateQualityGateCondition(condition, modifiedLiveMeasuresPerMetricId.get((int) condition.getMetricId()));
        }
        return unmodifiedLiveMeasuresPerMetricId.get((int) condition.getMetricId()).getAlertStatus();
      });
  }

  private <X> Set<X> addToSet(Set<X> set, X item) {
    HashSet<X> copy = new HashSet<>(set);
    copy.add(item);
    return copy;
  }

  private String recalculateQualityGateCondition(QualityGateConditionDto condition, LiveMeasureDto value) {
    return QualityGateStatus.ERROR.name(); // FIXME implement
  }
}
