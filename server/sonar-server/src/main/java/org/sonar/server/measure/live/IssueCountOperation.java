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

public class IssueCountOperation {
  private final String metricKey;
  private final double valueIncrement;
  private final double leakVariationIncrement;
  private final long issueCreatedAt;

  public IssueCountOperation(String metricKey, double valueIncrement, double leakVariationIncrement, long issueCreatedAt) {
    this.metricKey = metricKey;
    this.valueIncrement = valueIncrement;
    this.leakVariationIncrement = leakVariationIncrement;
    this.issueCreatedAt = issueCreatedAt;
  }

  public String getMetricKey() {
    return metricKey;
  }

  public double getValueIncrement() {
    return valueIncrement;
  }

  public double getLeakVariationIncrement() {
    return leakVariationIncrement;
  }

  public long getIssueCreatedAt() {
    return issueCreatedAt;
  }
}
