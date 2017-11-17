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

import com.google.common.collect.ArrayTable;
import com.google.common.collect.Table;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.sonar.core.util.stream.MoreCollectors;
import org.sonar.db.measure.LiveMeasureDto;
import org.sonar.db.metric.MetricDto;

import static java.util.Objects.requireNonNull;

public class MeasureMatrix {

  // component uuid -> metric key -> measure
  private final Table<String, String, MeasureCell> table;

  // direction is from file to project
  private final List<String> bottomUpComponentUuids;

  private final Map<String, MetricDto> metricBysKeys;

  public MeasureMatrix(List<String> bottomUpComponentUuids, Collection<MetricDto> metrics) {
    this.bottomUpComponentUuids = bottomUpComponentUuids;
    this.metricBysKeys = metrics
      .stream()
      .collect(MoreCollectors.uniqueIndex(MetricDto::getKey));
    this.table = ArrayTable.create(bottomUpComponentUuids, metricBysKeys.keySet());
  }

  public void init(List<LiveMeasureDto> dbMeasures) {
    Map<Integer, MetricDto> metricsByIds = metricBysKeys.values()
      .stream()
      .collect(MoreCollectors.uniqueIndex(MetricDto::getId));
    for (LiveMeasureDto dbMeasure : dbMeasures) {
      table.put(dbMeasure.getComponentUuid(), metricsByIds.get(dbMeasure.getMetricId()).getKey(), new MeasureCell(dbMeasure, false));
    }
  }

  public Stream<String> getBottomUpComponentUuids() {
    return bottomUpComponentUuids.stream();
  }

  public void incrementValue(String componentUuid, String metricKey, double value) {
    doIncrement(componentUuid, metricKey, c -> c.setValue(sum(c.getValue(), value)));
  }

  public void incrementVariation(String componentUuid, String metricKey, double variation) {
    doIncrement(componentUuid, metricKey, c -> c.setVariation(sum(c.getVariation(), variation)));
  }

  public Stream<LiveMeasureDto> getTouched() {
    return table.values().stream().filter(MeasureCell::isTouched).map(MeasureCell::getDto);
  }

  private void doIncrement(String componentUuid, String metricKey, Consumer<LiveMeasureDto> consumer) {
    MetricDto metric = requireNonNull(metricBysKeys.get(metricKey), "Metric " + metricKey + " not loaded");
    MeasureCell cell = table.get(componentUuid, metricKey);
    if (cell == null) {
      LiveMeasureDto measure = LiveMeasureDto.create()
        .setComponentUuid(componentUuid)
        .setProjectUuid(bottomUpComponentUuids.get(bottomUpComponentUuids.size() - 1))
        .setMetricId(metric.getId());
      cell = new MeasureCell(measure, true);
      table.put(componentUuid, metricKey, cell);
    } else {
      cell.setTouched(true);
    }
    consumer.accept(cell.getDto());
  }

  private static double sum(@Nullable Double d1, double d2) {
    return d1 == null ? d2 : (d1 + d2);
  }

  private static class MeasureCell {
    private final LiveMeasureDto dto;
    private boolean touched = false;

    public MeasureCell(LiveMeasureDto dto, boolean touched) {
      this.dto = dto;
      this.touched = touched;
    }

    public LiveMeasureDto getDto() {
      return dto;
    }

    public boolean isTouched() {
      return touched;
    }

    public void setTouched(boolean touched) {
      this.touched = touched;
    }
  }
}
