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

import java.util.Collection;
import java.util.Optional;
import org.sonar.core.util.stream.MoreCollectors;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.SnapshotDto;

import static java.util.Collections.emptyList;

public class LiveMeasureComputerImpl implements LiveMeasureComputer {

  private final DbClient dbClient;
  private final MeasureMatrixLoader matrixLoader;
  private final LiveQualityGateComputer qualityGateComputer;

  public LiveMeasureComputerImpl(DbClient dbClient, MeasureMatrixLoader matrixLoader, LiveQualityGateComputer qualityGateComputer) {
    this.dbClient = dbClient;
    this.matrixLoader = matrixLoader;
    this.qualityGateComputer = qualityGateComputer;
  }

  @Override
  public void refresh(DbSession dbSession, ComponentDto component, Collection<IssueCountOperation> issueCountOperations) {
    if (issueCountOperations.isEmpty()) {
      return;
    }

    Optional<SnapshotDto> lastAnalysis = dbClient.snapshotDao().selectLastAnalysisByRootComponentUuid(dbSession, component.projectUuid());
    if (!lastAnalysis.isPresent()) {
      // project has been deleted at the same time ?
      return;
    }
    Optional<Long> beginningOfLeakPeriod = lastAnalysis.map(SnapshotDto::getPeriodDate);

    MeasureMatrix matrix = matrixLoader.load(dbSession, component, /* TODO */emptyList());

    matrix.getBottomUpComponentUuids().forEach(componentUuid -> {
      for (IssueCountOperation op : issueCountOperations) {
        matrix.incrementValue(componentUuid, op.getMetricKey(), op.getValueIncrement());
        if (!beginningOfLeakPeriod.isPresent() || op.getIssueCreatedAt() >= beginningOfLeakPeriod.get()) {
          matrix.incrementVariation(componentUuid, op.getMetricKey(), op.getLeakVariationIncrement());
        }
      }
    });

    // persist the measures that have been created or updated
    matrix.getTouched().forEach(m -> dbClient.liveMeasureDao().insertOrUpdate(dbSession, m));

    ComponentDto project = dbClient.componentDao().selectByUuid(dbSession, component.projectUuid()).get();
    qualityGateComputer.recalculateQualityGate(dbSession, project, matrix.getTouched().collect(MoreCollectors.toList()));

    dbSession.commit();
  }
}
