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

import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.concurrent.Immutable;
import org.sonar.api.rule.RuleStatus;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.debt.DebtRemediationFunction;
import org.sonar.api.server.rule.RulesDefinition.Param;
import org.sonar.api.server.rule.RulesDefinition.Repository;
import org.sonar.api.server.rule.RulesDefinition.Rule;

import static java.lang.String.format;
import static java.util.Collections.unmodifiableList;

@Immutable
public class RuleImpl implements Rule {
  private final String pluginKey;
  private final Repository repository;
  private final String repoKey;
  private final String key;
  private final String name;
  private final RuleType type;
  private final String htmlDescription;
  private final String markdownDescription;
  private final String internalKey;
  private final String severity;
  private final boolean template;
  private final DebtRemediationFunction debtRemediationFunction;
  private final String gapDescription;
  private final Set<String> tags;
  private final Map<String, Param> params;
  private final RuleStatus status;
  private final boolean activatedByDefault;

  RuleImpl(Repository repository, NewRuleImpl newRule) {
    this.pluginKey = newRule.pluginKey;
    this.repository = repository;
    this.repoKey = newRule.repoKey;
    this.key = newRule.key;
    this.name = newRule.name;
    this.htmlDescription = newRule.htmlDescription;
    this.markdownDescription = newRule.markdownDescription;
    this.internalKey = newRule.internalKey;
    this.severity = newRule.severity;
    this.template = newRule.template;
    this.status = newRule.status;
    this.debtRemediationFunction = newRule.debtRemediationFunction;
    this.gapDescription = newRule.gapDescription;
    this.type = newRule.type == null ? RuleTagsToTypeConverter.convert(newRule.tags) : newRule.type;
    this.tags = ImmutableSortedSet.copyOf(Sets.difference(newRule.tags, RuleTagsToTypeConverter.RESERVED_TAGS));
    Map<String, Param> paramsBuilder = new HashMap<>();
    for (NewParamImpl newParam : newRule.paramsByKey.values()) {
      paramsBuilder.put(newParam.key, new ParamImpl(newParam));
    }
    this.params = Collections.unmodifiableMap(paramsBuilder);
    this.activatedByDefault = newRule.activatedByDefault;
  }

  @Override
  public Repository repository() {
    return repository;
  }

  /**
   * @since 6.6 the plugin the rule was declared in
   */
  @CheckForNull
  @Override
  public String pluginKey() {
    return pluginKey;
  }

  @Override
  public String key() {
    return key;
  }

  @Override
  public String name() {
    return name;
  }

  /**
   * @see NewRuleImpl#setType(RuleType)
   * @since 5.5
   */
  @Override
  public RuleType type() {
    return type;
  }

  @Override
  public String severity() {
    return severity;
  }

  @CheckForNull
  @Override
  public String htmlDescription() {
    return htmlDescription;
  }

  @CheckForNull
  @Override
  public String markdownDescription() {
    return markdownDescription;
  }

  @Override
  public boolean template() {
    return template;
  }

  /**
   * Should this rule be enabled by default. For example in SonarLint standalone.
   *
   * @since 6.0
   */
  @Override
  public boolean activatedByDefault() {
    return activatedByDefault;
  }

  @Override
  public RuleStatus status() {
    return status;
  }

  /**
   * @see #type()
   * @deprecated in 5.5. SQALE Quality Model is replaced by SonarQube Quality Model. {@code null} is
   * always returned. See https://jira.sonarsource.com/browse/MMF-184
   */
  @CheckForNull
  @Deprecated
  @Override
  public String debtSubCharacteristic() {
    return null;
  }

  @CheckForNull
  @Override
  public DebtRemediationFunction debtRemediationFunction() {
    return debtRemediationFunction;
  }

  /**
   * @deprecated since 5.5, replaced by {@link #gapDescription()}
   */
  @Deprecated
  @CheckForNull
  @Override
  public String effortToFixDescription() {
    return gapDescription();
  }

  @CheckForNull
  @Override
  public String gapDescription() {
    return gapDescription;
  }

  @CheckForNull
  @Override
  public Param param(String key) {
    return params.get(key);
  }

  @Override
  public List<Param> params() {
    return unmodifiableList(new ArrayList<>(params.values()));
  }

  @Override
  public Set<String> tags() {
    return tags;
  }

  /**
   * @see NewRuleImpl#setInternalKey(String)
   */
  @CheckForNull
  @Override
  public String internalKey() {
    return internalKey;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RuleImpl other = (RuleImpl) o;
    return key.equals(other.key) && repoKey.equals(other.repoKey);
  }

  @Override
  public int hashCode() {
    int result = repoKey.hashCode();
    result = 31 * result + key.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return format("[repository=%s, key=%s]", repoKey, key);
  }
}
