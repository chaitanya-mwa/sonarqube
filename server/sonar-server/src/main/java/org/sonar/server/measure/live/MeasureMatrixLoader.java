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

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.Metric;
import org.sonar.api.server.ServerSide;
import org.sonar.core.util.stream.MoreCollectors;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.measure.LiveMeasureDto;
import org.sonar.db.metric.MetricDto;

@ServerSide
public class MeasureMatrixLoader {

  private final DbClient dbClient;
  private final MetricsDag metricsDag;

  public MeasureMatrixLoader(DbClient dbClient, MetricsDag metricsDag) {
    this.dbClient = dbClient;
    this.metricsDag = metricsDag;
  }

  public MeasureMatrix load(DbSession dbSession, ComponentDto component, Collection<String> issueCountMetricKeys) {
    List<MetricDto> metrics = dbClient.metricDao().selectByKeys(dbSession,
      /* TODO restrict list */CoreMetrics.getMetrics().stream().map(Metric::getKey).collect(MoreCollectors.toArrayList()));
    Map<Integer, MetricDto> metricsPerId = metrics
      .stream()
      .collect(MoreCollectors.uniqueIndex(MetricDto::getId));

    List<ComponentDto> bottomUpComponents = new ArrayList<>();
    bottomUpComponents.addAll(dbClient.componentDao().selectAncestors(dbSession, component));
    bottomUpComponents.add(component);
    Collections.reverse(bottomUpComponents);

    MeasureMatrix matrix = new MeasureMatrix(bottomUpComponents, metrics);
    List<LiveMeasureDto> dbMeasures = dbClient.liveMeasureDao().selectByComponentUuids(dbSession,
      Lists.transform(bottomUpComponents, ComponentDto::uuid),
      metricsPerId.keySet());
    matrix.init(dbMeasures);

    return matrix;
  }
}
