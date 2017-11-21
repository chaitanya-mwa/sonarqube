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

import java.util.Optional;
import javax.annotation.CheckForNull;
import org.apache.commons.lang.StringUtils;
import org.sonar.db.measure.LiveMeasureDto;
import org.sonar.db.metric.MetricDto;
import org.sonar.db.qualitygate.QualityGateConditionDto;
import org.sonar.server.computation.task.projectanalysis.measure.Measure;
import org.sonar.server.computation.task.projectanalysis.metric.Metric;
import org.sonar.server.computation.task.projectanalysis.qualitygate.EvaluationResult;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Optional.of;

public final class LiveConditionEvaluator {

  /**
   * Evaluates the condition for the specified measure
   */
  public EvaluationResult evaluate(MetricDto metric, QualityGateConditionDto condition, LiveMeasureDto measure) {
    checkArgument(!metric.getValueType().toString().equals(Metric.MetricType.DATA.toString()), "Conditions on MetricType DATA are not supported");

    Comparable measureComparable = parseMeasure(condition, metric, measure);
    if (measureComparable == null) {
      return new EvaluationResult(Measure.Level.OK, null);
    }

    return evaluateCondition(condition, metric, measureComparable, Measure.Level.ERROR)
      .orElseGet(() -> evaluateCondition(condition, metric, measureComparable, Measure.Level.WARN)
        .orElseGet(() -> new EvaluationResult(Measure.Level.OK, measureComparable)));
  }

  private static Optional<EvaluationResult> evaluateCondition(QualityGateConditionDto condition, MetricDto metric, Comparable<?> measureComparable, Measure.Level alertLevel) {
    String conditionValue = getValueToEval(condition, alertLevel);
    if (StringUtils.isEmpty(conditionValue)) {
      return Optional.empty();
    }

    try {
      Comparable conditionComparable = parseConditionValue(metric, conditionValue);
      if (doesReachThresholds(measureComparable, conditionComparable, condition)) {
        return of(new EvaluationResult(alertLevel, measureComparable));
      }
      return Optional.empty();
    } catch (NumberFormatException badValueFormat) {
      throw new IllegalArgumentException(String.format(
        "Quality Gate: Unable to parse value '%s' to compare against %s",
        conditionValue, metric.getKey()));
    }
  }

  private static String getValueToEval(QualityGateConditionDto condition, Measure.Level alertLevel) {
    if (Measure.Level.ERROR.equals(alertLevel)) {
      return condition.getErrorThreshold();
    } else if (Measure.Level.WARN.equals(alertLevel)) {
      return condition.getWarningThreshold();
    } else {
      throw new IllegalStateException(alertLevel.toString());
    }
  }

  private static boolean doesReachThresholds(Comparable measureValue, Comparable criteriaValue, QualityGateConditionDto condition) {
    int comparison = measureValue.compareTo(criteriaValue);
    switch (condition.getOperator()) {
      case "EQ":
        return comparison == 0;
      case "NE":
        return comparison != 0;
      case "GT":
        return comparison > 0;
      case "LT":
        return comparison < 0;
      default:
        throw new IllegalArgumentException(String.format("Unsupported operator '%s'", condition.getOperator()));
    }
  }

  private static Comparable parseConditionValue(MetricDto metric, String value) {
    switch (metric.getValueType()) {
      case "BOOLEAN":
        return Integer.parseInt(value) == 1;
      case "INT":
        return parseInteger(value);
      case "LONG":
        return Long.parseLong(value);
      case "DOUBLE":
        return Double.parseDouble(value);
      case "STRING":
      case "LEVEL":
        return value;
      default:
        throw new IllegalArgumentException(String.format("Unsupported value type %s. Can not convert condition value", metric.getValueType()));
    }
  }

  private static Comparable<Integer> parseInteger(String value) {
    return value.contains(".") ? Integer.parseInt(value.substring(0, value.indexOf('.'))) : Integer.parseInt(value);
  }

  @CheckForNull
  private static Comparable parseMeasure(QualityGateConditionDto condition, MetricDto metric, LiveMeasureDto measure) {
    if (condition.getPeriod() != null) {
      return parseMeasureFromVariation(condition, metric, measure);
    }
    switch (metric.getValueType()) {
      case "BOOLEAN":
        return measure.getValue() == 1;
      case "INT":
        return (int) (double) measure.getValue();
      case "LONG":
        return (long) (double) measure.getValue();
      case "DOUBLE":
        return measure.getValue();
      case "NO_VALUE":
      case "STRING":
      case "LEVEL":
      default:
        throw new IllegalArgumentException("Conditions are not supported for metric type " + metric.getValueType());
    }
  }

  @CheckForNull
  private static Comparable parseMeasureFromVariation(QualityGateConditionDto condition, MetricDto metric, LiveMeasureDto measure) {
    Optional<Double> periodValue = getPeriodValue(measure);
    if (periodValue.isPresent()) {
      switch (metric.getValueType()) {
        case "BOOLEAN":
          return periodValue.get().intValue() == 1;
        case "INT":
          return periodValue.get().intValue();
        case "LONG":
          return periodValue.get().longValue();
        case "DOUBLE":
          return periodValue.get();
        case "NO_VALUE":
        case "STRING":
        case "LEVEL":
        default:
          throw new IllegalArgumentException("Period conditions are not supported for metric type " + metric.getValueType());
      }
    }
    return null;
  }

  private static Optional<Double> getPeriodValue(LiveMeasureDto measure) {
    return measure.getVariation() == null ? Optional.empty() : Optional.of(measure.getVariation());
  }

}
