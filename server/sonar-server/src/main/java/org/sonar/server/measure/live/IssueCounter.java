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
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import org.sonar.api.rules.RuleType;
import org.sonar.db.issue.IssueGroup;
import org.sonar.db.rule.SeverityUtil;

public class IssueCounter {

  private final Collection<IssueGroup> groups;

  public IssueCounter(Collection<IssueGroup> groups) {
    this.groups = groups;
  }

  public Optional<String> getMaxSeverityOfUnresolved(RuleType ruleType) {
    OptionalInt max = groups.stream()
      .filter(g -> g.getResolution() == null)
      .filter(g -> g.getRuleType() == ruleType.getDbConstant())
      .mapToInt(g -> SeverityUtil.getOrdinalFromSeverity(g.getSeverity()))
      .max();
    if (max.isPresent()) {
      return Optional.of(SeverityUtil.getSeverityFromOrdinal(max.getAsInt()));
    }
    return Optional.empty();
  }

  public double effortOfUnresolved(RuleType type) {
    int typeAsInt = type.getDbConstant();
    return groups.stream()
      .filter(g -> g.getResolution() == null)
      .filter(g -> typeAsInt == g.getRuleType())
      .mapToDouble(IssueGroup::getEffort)
      .sum();
  }

  public long countUnresolvedBySeverity(String severity) {
    return groups.stream()
      .filter(g -> g.getResolution() == null)
      .filter(g -> severity.equals(g.getSeverity()))
      .mapToLong(IssueGroup::getCount)
      .sum();
  }

  public long countByResolution(String resolution) {
    return groups.stream()
      .filter(g -> Objects.equals(resolution, g.getResolution()))
      .mapToLong(IssueGroup::getCount)
      .sum();
  }

  public long countUnresolvedByType(RuleType type) {
    int typeAsInt = type.getDbConstant();
    return groups.stream()
      .filter(g -> g.getResolution() == null)
      .filter(g -> typeAsInt == g.getRuleType())
      .mapToLong(IssueGroup::getCount)
      .sum();
  }

  public long countByStatus(String status) {
    return groups.stream()
      .filter(g -> Objects.equals(status, g.getStatus()))
      .mapToLong(IssueGroup::getCount)
      .sum();
  }

  public long countUnresolved() {
    return groups.stream()
      .filter(g -> g.getResolution() == null)
      .mapToLong(IssueGroup::getCount)
      .sum();
  }
}
