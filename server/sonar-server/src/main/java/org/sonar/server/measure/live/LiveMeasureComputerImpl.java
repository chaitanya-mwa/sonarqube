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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.sonar.core.util.stream.MoreCollectors;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.SnapshotDto;
import org.sonar.db.measure.LiveMeasureDto;
import org.sonar.db.metric.MetricDto;

public class LiveMeasureComputerImpl implements LiveMeasureComputer {

  private final DbClient dbClient;

  public LiveMeasureComputerImpl(DbClient dbClient) {
    this.dbClient = dbClient;
  }

  @Override
  public void refresh(DbSession dbSession, ComponentDto component, Collection<DiffOperation> diffOperations) {
    if (diffOperations.isEmpty()) {
      return;
    }

    Optional<SnapshotDto> lastAnalysis = dbClient.snapshotDao().selectLastAnalysisByRootComponentUuid(dbSession, component.projectUuid());
    if (!lastAnalysis.isPresent()) {
      // project has been deleted at the same time ?
      return;
    }
    Long beginningOfLeakPeriod = lastAnalysis.get().getPeriodDate();

    Set<String> metricKeys = diffOperations.stream().map(DiffOperation::getMetricKey).collect(MoreCollectors.toHashSet());
    Map<String, Integer> metricIdsPerKeys = dbClient.metricDao().selectByKeys(dbSession, metricKeys).stream()
      .collect(MoreCollectors.uniqueIndex(MetricDto::getKey, MetricDto::getId));

    List<String> componentUuids = new ArrayList<>();
    componentUuids.add(component.uuid());
    componentUuids.addAll(component.getUuidPathAsList());

    List<LiveMeasureDto> dbMeasures = dbClient.liveMeasureDao().selectByComponentUuids(dbSession, componentUuids, metricIdsPerKeys.values());
    for (LiveMeasureDto m : dbMeasures) {
      for (DiffOperation diffOperation : diffOperations) {
        if (m.getMetricId()==metricIdsPerKeys.get(diffOperation.getMetricKey())) {
          m.setValue(sum(m.getValue(), diffOperation.getValueIncrement()));
          if (beginningOfLeakPeriod == null || diffOperation.getIssueCreatedAt() >= beginningOfLeakPeriod) {
            m.setVariation(sum(m.getVariation(), diffOperation.getLeakVariationIncrement()));
          }
        }
      }
      dbClient.liveMeasureDao().update(dbSession, m);
    }
    dbSession.commit();
  }

  private static double sum(@Nullable Double d1, double d2) {
    return d1 == null ? d2 : (d1 + d2);
  }

}
