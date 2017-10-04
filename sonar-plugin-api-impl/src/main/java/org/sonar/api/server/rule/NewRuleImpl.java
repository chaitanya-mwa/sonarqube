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

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.io.IOUtils;
import org.sonar.api.rule.RuleStatus;
import org.sonar.api.rule.Severity;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.debt.DebtRemediationFunction;
import org.sonar.api.server.debt.internal.DefaultDebtRemediationFunctions;
import org.sonar.api.server.rule.RulesDefinition.DebtRemediationFunctions;
import org.sonar.api.server.rule.RulesDefinition.NewParam;
import org.sonar.api.server.rule.RulesDefinition.NewRule;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.apache.commons.lang.StringUtils.trimToNull;

public class NewRuleImpl implements NewRule {
  final String pluginKey;
  final String repoKey;
  final String key;
  RuleType type;
  String name;
  String htmlDescription;
  String markdownDescription;
  String internalKey;
  String severity = Severity.MAJOR;
  boolean template;
  RuleStatus status = RuleStatus.defaultStatus();
  DebtRemediationFunction debtRemediationFunction;
  String gapDescription;
  final Set<String> tags = new TreeSet<>();
  final Map<String, NewParamImpl> paramsByKey = new HashMap<>();
  private final DebtRemediationFunctions functions;
  boolean activatedByDefault;

  NewRuleImpl(@Nullable String pluginKey, String repoKey, String key) {
    this.pluginKey = pluginKey;
    this.repoKey = repoKey;
    this.key = key;
    this.functions = new DefaultDebtRemediationFunctions(repoKey, key);
  }

  @Override
  public String key() {
    return this.key;
  }

  /**
   * Required rule name
   */
  @Override
  public NewRuleImpl setName(String s) {
    this.name = trimToNull(s);
    return this;
  }

  @Override
  public NewRuleImpl setTemplate(boolean template) {
    this.template = template;
    return this;
  }

  /**
   * Should this rule be enabled by default. For example in SonarLint standalone.
   *
   * @since 6.0
   */
  @Override
  public NewRuleImpl setActivatedByDefault(boolean activatedByDefault) {
    this.activatedByDefault = activatedByDefault;
    return this;
  }

  @Override
  public NewRuleImpl setSeverity(String s) {
    checkArgument(Severity.ALL.contains(s), "Severity of rule %s is not correct: %s", this, s);
    this.severity = s;
    return this;
  }

  /**
   * The type as defined by the SonarQube Quality Model.
   * <br>
   * When a plugin does not define rule type, then it is deduced from
   * tags:
   * <ul>
   * <li>if the rule has the "bug" tag then type is {@link RuleType#BUG}</li>
   * <li>if the rule has the "security" tag then type is {@link RuleType#VULNERABILITY}</li>
   * <li>if the rule has both tags "bug" and "security", then type is {@link RuleType#BUG}</li>
   * <li>default type is {@link RuleType#CODE_SMELL}</li>
   * </ul>
   * Finally the "bug" and "security" tags are considered as reserved. They
   * are silently removed from the final state of definition.
   *
   * @since 5.5
   */
  @Override
  public NewRuleImpl setType(RuleType t) {
    this.type = t;
    return this;
  }

  /**
   * The optional description, in HTML format, has no max length. It's exclusive with markdown description
   * (see {@link #setMarkdownDescription(String)})
   */
  @Override
  public NewRuleImpl setHtmlDescription(@Nullable String s) {
    checkState(markdownDescription == null, "RuleImpl '%s' already has a Markdown description", this);
    this.htmlDescription = trimToNull(s);
    return this;
  }

  /**
   * Load description from a file available in classpath. Example : <code>setHtmlDescription(getClass().getResource("/myrepo/Rule1234.html")</code>
   */
  @Override
  public NewRuleImpl setHtmlDescription(@Nullable URL classpathUrl) {
    if (classpathUrl != null) {
      try {
        setHtmlDescription(IOUtils.toString(classpathUrl, UTF_8));
      } catch (IOException e) {
        throw new IllegalStateException("Fail to read: " + classpathUrl, e);
      }
    } else {
      this.htmlDescription = null;
    }
    return this;
  }

  /**
   * The optional description, in a restricted Markdown format, has no max length. It's exclusive with HTML description
   * (see {@link #setHtmlDescription(String)})
   */
  @Override
  public NewRuleImpl setMarkdownDescription(@Nullable String s) {
    checkState(htmlDescription == null, "RuleImpl '%s' already has an HTML description", this);
    this.markdownDescription = trimToNull(s);
    return this;
  }

  /**
   * Load description from a file available in classpath. Example : {@code setMarkdownDescription(getClass().getResource("/myrepo/Rule1234.md")}
   */
  @Override
  public NewRuleImpl setMarkdownDescription(@Nullable URL classpathUrl) {
    if (classpathUrl != null) {
      try {
        setMarkdownDescription(IOUtils.toString(classpathUrl, UTF_8));
      } catch (IOException e) {
        throw new IllegalStateException("Fail to read: " + classpathUrl, e);
      }
    } else {
      this.markdownDescription = null;
    }
    return this;
  }

  /**
   * Default value is {@link org.sonar.api.rule.RuleStatus#READY}. The value
   * {@link org.sonar.api.rule.RuleStatus#REMOVED} is not accepted and raises an
   * {@link java.lang.IllegalArgumentException}.
   */
  @Override
  public NewRuleImpl setStatus(RuleStatus status) {
    checkArgument(RuleStatus.REMOVED != status, "Status 'REMOVED' is not accepted on rule '%s'", this);
    this.status = status;
    return this;
  }

  /**
   * SQALE sub-characteristic. See http://www.sqale.org
   *
   * @see org.sonar.api.server.rule.RulesDefinitionImpl.SubCharacteristics for constant values
   * @see #setType(RuleType)
   * @deprecated in 5.5. SQALE Quality Model is replaced by SonarQube Quality Model. This method does nothing.
   * See https://jira.sonarsource.com/browse/MMF-184
   */
  @Deprecated
  @Override
  public NewRuleImpl setDebtSubCharacteristic(@Nullable String s) {
    return this;
  }

  /**
   * Factory of {@link org.sonar.api.server.debt.DebtRemediationFunction}
   */
  @Override
  public DebtRemediationFunctions debtRemediationFunctions() {
    return functions;
  }

  /**
   * @see #debtRemediationFunctions()
   */
  @Override
  public NewRuleImpl setDebtRemediationFunction(@Nullable DebtRemediationFunction fn) {
    this.debtRemediationFunction = fn;
    return this;
  }

  /**
   * @deprecated since 5.5, replaced by {@link #setGapDescription(String)}
   */
  @Deprecated
  @Override
  public NewRuleImpl setEffortToFixDescription(@Nullable String s) {
    return setGapDescription(s);
  }

  /**
   * For rules that use LINEAR or LINEAR_OFFSET remediation functions, the meaning
   * of the function parameter (= "gap") must be set. This description
   * explains what 1 point of "gap" represents for the rule.
   * <br>
   * Example: for the "Insufficient condition coverage", this description for the
   * remediation function gap multiplier/base effort would be something like
   * "Effort to test one uncovered condition".
   */
  @Override
  public NewRuleImpl setGapDescription(@Nullable String s) {
    this.gapDescription = s;
    return this;
  }

  /**
   * Create a parameter with given unique key. Max length of key is 128 characters.
   */
  @Override
  public NewParam createParam(String paramKey) {
    checkArgument(!paramsByKey.containsKey(paramKey), "The parameter '%s' is declared several times on the rule %s", paramKey, this);
    NewParamImpl param = new NewParamImpl(paramKey);
    paramsByKey.put(paramKey, param);
    return param;
  }

  @CheckForNull
  @Override
  public NewParam param(String paramKey) {
    return paramsByKey.get(paramKey);
  }

  @Override
  public Collection<NewParam> params() {
    return paramsByKey.values().stream().map(NewParam.class::cast).collect(toList());
  }

  /**
   * @see RuleTagFormat
   */
  @Override
  public NewRuleImpl addTags(String... list) {
    for (String tag : list) {
      RuleTagFormat.validate(tag);
      tags.add(tag);
    }
    return this;
  }

  /**
   * @see RuleTagFormat
   */
  @Override
  public NewRuleImpl setTags(String... list) {
    tags.clear();
    addTags(list);
    return this;
  }

  /**
   * Optional key that can be used by the rule engine. Not displayed
   * in webapp. For example the Java Checkstyle plugin feeds this field
   * with the internal path ("Checker/TreeWalker/AnnotationUseStyle").
   */
  @Override
  public NewRuleImpl setInternalKey(@Nullable String s) {
    this.internalKey = s;
    return this;
  }

  void validate() {
    if (isEmpty(name)) {
      throw new IllegalStateException(format("Name of rule %s is empty", this));
    }
    if (isEmpty(htmlDescription) && isEmpty(markdownDescription)) {
      throw new IllegalStateException(format("One of HTML description or Markdown description must be defined for rule %s", this));
    }
  }

  @Override
  public String toString() {
    return format("[repository=%s, key=%s]", repoKey, key);
  }
}
