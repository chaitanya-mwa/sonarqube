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
package org.sonar.db.issue;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class IssueGroup {
  private int ruleType;
  private String severity;
  @Nullable
  private String resolution;
  private String status;
  private double effort;
  private long count;
  private boolean inLeak;

  public int getRuleType() {
    return ruleType;
  }

  public String getSeverity() {
    return severity;
  }

  @CheckForNull
  public String getResolution() {
    return resolution;
  }

  public String getStatus() {
    return status;
  }

  public double getEffort() {
    return effort;
  }

  public long getCount() {
    return count;
  }

  public boolean isInLeak() {
    return inLeak;
  }
}
