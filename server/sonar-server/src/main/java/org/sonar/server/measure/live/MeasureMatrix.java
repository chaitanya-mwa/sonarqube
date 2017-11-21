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
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import org.sonar.core.util.stream.MoreCollectors;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.measure.LiveMeasureDto;
import org.sonar.db.metric.MetricDto;
import org.sonar.server.computation.task.projectanalysis.qualitymodel.RatingGrid;

import static java.util.Objects.requireNonNull;

public class MeasureMatrix {

  // component uuid -> metric key -> measure
  private final Table<String, String, MeasureCell> table;

  // direction is from file to project
  private final List<ComponentDto> bottomUpComponents;

  private final Map<String, MetricDto> metricBysKeys;

  public MeasureMatrix(List<ComponentDto> bottomUpComponents, Collection<MetricDto> metrics) {
    this.bottomUpComponents = bottomUpComponents;
    this.metricBysKeys = metrics
      .stream()
      .collect(MoreCollectors.uniqueIndex(MetricDto::getKey));
    this.table = ArrayTable.create(Lists.transform(bottomUpComponents, ComponentDto::uuid), metricBysKeys.keySet());
  }

  public void init(List<LiveMeasureDto> dbMeasures) {
    Map<Integer, MetricDto> metricsByIds = metricBysKeys.values()
      .stream()
      .collect(MoreCollectors.uniqueIndex(MetricDto::getId));
    for (LiveMeasureDto dbMeasure : dbMeasures) {
      table.put(dbMeasure.getComponentUuid(), metricsByIds.get(dbMeasure.getMetricId()).getKey(), new MeasureCell(dbMeasure, false));
    }
  }

  public ComponentDto getProject() {
    return bottomUpComponents.get(bottomUpComponents.size() - 1);
  }

  public Stream<ComponentDto> getBottomUpComponents() {
    return bottomUpComponents.stream();
  }

  public void setValue(ComponentDto component, String metricKey, double value) {
    changeCell(component, metricKey, m -> {
      Double initialValue = m.getValue();
      if (initialValue != null && Double.compare(initialValue, value) == 0) {
        return false;
      }
      Double initialVariation = m.getVariation();
      if (initialValue != null && initialVariation != null) {
        double leakInitialValue = initialValue - initialVariation;
        m.setVariation(value - leakInitialValue);
      }
      m.setValue(value);
      return true;
    });
  }

  public void setValue(ComponentDto component, String metricKey, String value) {
    changeCell(component, metricKey, m -> {
      if (Objects.equals(m.getDataAsString(), value)) {
        return false;
      }
      m.setData(value);
      return true;
    });
  }

  public void setValue(ComponentDto component, String metricKey, RatingGrid.Rating value) {
    changeCell(component, metricKey, m -> {
      if (Objects.equals(m.getDataAsString(), value.name())) {
        return false;
      }
      m.setData(value.name());
      m.setValue((double) value.getIndex());
      return true;
    });
  }

  public void setVariation(ComponentDto component, String metricKey, double variation) {
    changeCell(component, metricKey, c -> {
      if (c.getVariation() != null && Double.compare(c.getVariation(), variation) == 0) {
        return false;
      }
      c.setVariation(variation);
      return true;
    });
  }

  public void setVariation(ComponentDto component, String metricKey, RatingGrid.Rating variation) {
    setVariation(component, metricKey, (double) variation.getIndex());
  }

  public Stream<LiveMeasureDto> getTouched() {
    return table.values()
      .stream()
      .filter(Objects::nonNull)
      .filter(MeasureCell::isTouched)
      .map(MeasureCell::getDto);
  }

  private void changeCell(ComponentDto component, String metricKey, Function<LiveMeasureDto, Boolean> changer) {
    MetricDto metric = requireNonNull(metricBysKeys.get(metricKey), "Metric " + metricKey + " not loaded");
    MeasureCell cell = table.get(component.uuid(), metricKey);
    if (cell == null) {
      LiveMeasureDto measure = LiveMeasureDto.create()
        .setComponentUuid(component.uuid())
        .setProjectUuid(component.projectUuid())
        .setMetricId(metric.getId());
      cell = new MeasureCell(measure, true);
      table.put(component.uuid(), metricKey, cell);
      changer.apply(cell.getDto());
    } else if (changer.apply(cell.getDto())) {
      cell.setTouched(true);
    }
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
