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
package org.sonar.api.server.rule;

import javax.annotation.Nullable;
import org.apache.commons.lang.StringUtils;
import org.sonar.api.server.rule.RulesDefinition.NewParam;

import static org.apache.commons.lang.StringUtils.defaultIfEmpty;

public class NewParamImpl implements NewParam {
  final String key;
  String name;
  String description;
  String defaultValue;
  RuleParamType type = RuleParamType.STRING;

  NewParamImpl(String key) {
    this.key = this.name = key;
  }

  @Override
  public String key() {
    return key;
  }

  @Override
  public NewParamImpl setName(@Nullable String s) {
    // name must never be null.
    this.name = StringUtils.defaultIfBlank(s, key);
    return this;
  }

  @Override
  public NewParamImpl setType(RuleParamType t) {
    this.type = t;
    return this;
  }

  /**
   * Plain-text description. Can be null. Max length is 4000 characters.
   */
  @Override
  public NewParamImpl setDescription(@Nullable String s) {
    this.description = StringUtils.defaultIfBlank(s, null);
    return this;
  }

  /**
   * Empty default value will be converted to null. Max length is 4000 characters.
   */
  @Override
  public NewParamImpl setDefaultValue(@Nullable String s) {
    this.defaultValue = defaultIfEmpty(s, null);
    return this;
  }
}
