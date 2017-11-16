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
import java.util.List;
import org.sonar.core.util.stream.MoreCollectors;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.measure.LiveMeasureDto;
import org.sonar.db.metric.MetricDto;

import static java.util.Arrays.asList;

public class LiveMeasureComputerImpl implements LiveMeasureComputer {

  private final DbClient dbClient;

  public LiveMeasureComputerImpl(DbClient dbClient) {
    this.dbClient = dbClient;
  }

  @Override
  public void refresh(DbSession dbSession, ComponentDto component, DiffOperation diffOperation) {
    List<MetricDto> metrics = dbClient.metricDao().selectByKeys(dbSession, asList(diffOperation.getMetricKey()));

    List<String> uuids = new ArrayList<>();
    uuids.add(component.uuid());
    uuids.addAll(component.getUuidPathAsList());

    List<LiveMeasureDto> dbMeasures = dbClient.liveMeasureDao().selectByComponentUuids(dbSession, uuids, toMetricIds(metrics));

    dbMeasures.stream()
      .filter(m -> m.getValue() != null)
      .forEach(m -> {
        m.setValue(m.getValue() + diffOperation.getDiff());
        dbClient.liveMeasureDao().update(dbSession, m);
      });
    dbSession.commit();
  }

  private List<Integer> toMetricIds(List<MetricDto> metrics) {
    return metrics.stream().map(MetricDto::getId).collect(MoreCollectors.toArrayList(metrics.size()));
  }

}
