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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.lang.StringUtils;
import org.sonar.api.server.rule.RulesDefinition.NewRepository;
import org.sonar.api.server.rule.RulesDefinition.NewRule;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.stream.Collectors.toList;

public class NewRepositoryImpl implements NewRepository {
  private final RulesDefinitionContext context;
  final String key;
  String language;
  String name;
  final Map<String, NewRuleImpl> newRules = new HashMap<>();

  NewRepositoryImpl(RulesDefinitionContext context, String key, String language) {
    this.context = context;
    this.key = this.name = key;
    this.language = language;
  }

  @Override
  public String key() {
    return key;
  }

  @Override
  public NewRepositoryImpl setName(@Nullable String s) {
    if (StringUtils.isNotEmpty(s)) {
      this.name = s;
    }
    return this;
  }

  @Override
  public NewRule createRule(String ruleKey) {
    checkArgument(!newRules.containsKey(ruleKey), "The rule '%s' of repository '%s' is declared several times", ruleKey, key);
    NewRuleImpl newRule = new NewRuleImpl(context.currentPluginKey, key, ruleKey);
    newRules.put(ruleKey, newRule);
    return newRule;
  }

  @CheckForNull
  @Override
  public NewRule rule(String ruleKey) {
    return newRules.get(ruleKey);
  }

  @Override
  public Collection<NewRule> rules() {
    return newRules.values().stream().map(NewRule.class::cast).collect(toList());
  }

  @Override
  public void done() {
    // note that some validations can be done here, for example for
    // verifying that at least one rule is declared

    context.registerRepository(this);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("NewRepository{");
    sb.append("key='").append(key).append('\'');
    sb.append(", language='").append(language).append('\'');
    sb.append('}');
    return sb.toString();
  }
}
