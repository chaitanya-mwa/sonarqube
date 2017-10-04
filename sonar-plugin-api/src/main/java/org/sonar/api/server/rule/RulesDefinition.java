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

import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import org.sonar.api.ExtensionPoint;
import org.sonar.api.ce.ComputeEngineSide;
import org.sonar.api.rule.RuleStatus;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.ServerSide;
import org.sonar.api.server.debt.DebtRemediationFunction;
import org.sonarsource.api.sonarlint.SonarLintSide;

/**
 * Defines some coding rules of the same repository. For example the Java Findbugs plugin provides an implementation of
 * this extension point in order to define the rules that it supports.
 * <br>
 * This interface replaces the deprecated class org.sonar.api.rules.RuleRepository.
 * <br>
 * <h3>How to use</h3>
 * <pre>
 * public class MyJsRulesDefinition implements RulesDefinition {
 *
 *   {@literal @}Override
 *   public void define(Context context) {
 *     NewRepository repository = context.createRepository("my_js", "js").setName("My Javascript Analyzer");
 *
 *     // define a rule programmatically. Note that rules
 *     // could be loaded from files (JSON, XML, ...)
 *     NewRule x1Rule = repository.createRule("x1")
 *      .setName("No empty line")
 *      .setHtmlDescription("Generate an issue on empty lines")
 *
 *      // optional tags
 *      .setTags("style", "stupid")
 *
 *     // optional status. Default value is READY.
 *     .setStatus(RuleStatus.BETA)
 *
 *     // default severity when the rule is activated on a Quality profile. Default value is MAJOR.
 *     .setSeverity(Severity.MINOR);
 *
 *     // optional type for SonarQube Quality Model. Default is RulesDefinition.Type.CODE_SMELL.
 *     .setType(RulesDefinition.Type.BUG)
 *
 *     x1Rule
 *       .setDebtRemediationFunction(x1Rule.debtRemediationFunctions().linearWithOffset("1h", "30min"));
 *
 *     x1Rule.createParam("acceptWhitespace")
 *       .setDefaultValue("false")
 *       .setType(RuleParamType.BOOLEAN)
 *       .setDescription("Accept whitespaces on the line");
 *
 *     // don't forget to call done() to finalize the definition
 *     repository.done();
 *   }
 * }
 * </pre>
 * <br>
 * If rules are declared in a XML file with the standard SonarQube format (see
 * {@link org.sonar.api.server.rule.RulesDefinitionXmlLoader}), then it can be loaded by using :
 * <br>
 * <pre>
 * public class MyJsRulesDefinition implements RulesDefinition {
 *
 *   private final RulesDefinitionXmlLoader xmlLoader;
 *
 *   public MyJsRulesDefinition(RulesDefinitionXmlLoader xmlLoader) {
 *     this.xmlLoader = xmlLoader;
 *   }
 *
 *   {@literal @}Override
 *   public void define(Context context) {
 *     NewRepository repository = context.createRepository("my_js", "js").setName("My Javascript Analyzer");
 *     // see javadoc of RulesDefinitionXmlLoader for the format
 *     xmlLoader.load(repository, getClass().getResourceAsStream("/path/to/rules.xml"));
 *     repository.done();
 *   }
 * }
 * </pre>
 * <br>
 * In the above example, XML file must contain name and description of each rule. If it's not the case, then the
 * (deprecated) English bundles can be used :
 * <br>
 * <pre>
 * public class MyJsRulesDefinition implements RulesDefinition {
 *
 *   private final RulesDefinitionXmlLoader xmlLoader;
 *   private final RulesDefinitionI18nLoader i18nLoader;
 *
 *   public MyJsRulesDefinition(RulesDefinitionXmlLoader xmlLoader, RulesDefinitionI18nLoader i18nLoader) {
 *     this.xmlLoader = xmlLoader;
 *     this.i18nLoader = i18nLoader;
 *   }
 *
 *   {@literal @}Override
 *   public void define(Context context) {
 *     NewRepository repository = context.createRepository("my_js", "js").setName("My Javascript Analyzer");
 *     xmlLoader.load(repository, getClass().getResourceAsStream("/path/to/rules.xml"), "UTF-8");
 *     i18nLoader.load(repository);
 *     repository.done();
 *   }
 * }
 * </pre>
 *
 * @since 4.3
 */
@ServerSide
@ComputeEngineSide
@SonarLintSide
@ExtensionPoint
public interface RulesDefinition {

  /**
   * Default sub-characteristics of technical debt model. See http://www.sqale.org
   *
   * @deprecated in 5.5. SQALE Quality Model is replaced by SonarQube Quality Model.
   * See https://jira.sonarsource.com/browse/MMF-184
   */
  @Deprecated
  final class SubCharacteristics {
    /**
     * Related to characteristic REUSABILITY
     */
    public static final String MODULARITY = "MODULARITY";

    /**
     * Related to characteristic REUSABILITY
     */
    public static final String TRANSPORTABILITY = "TRANSPORTABILITY";

    /**
     * Related to characteristic PORTABILITY
     */
    public static final String COMPILER_RELATED_PORTABILITY = "COMPILER_RELATED_PORTABILITY";

    /**
     * Related to characteristic PORTABILITY
     */
    public static final String HARDWARE_RELATED_PORTABILITY = "HARDWARE_RELATED_PORTABILITY";

    /**
     * Related to characteristic PORTABILITY
     */
    public static final String LANGUAGE_RELATED_PORTABILITY = "LANGUAGE_RELATED_PORTABILITY";

    /**
     * Related to characteristic PORTABILITY
     */
    public static final String OS_RELATED_PORTABILITY = "OS_RELATED_PORTABILITY";

    /**
     * Related to characteristic PORTABILITY
     */
    public static final String SOFTWARE_RELATED_PORTABILITY = "SOFTWARE_RELATED_PORTABILITY";

    /**
     * Related to characteristic PORTABILITY
     */
    public static final String TIME_ZONE_RELATED_PORTABILITY = "TIME_ZONE_RELATED_PORTABILITY";

    /**
     * Related to characteristic MAINTAINABILITY
     */
    public static final String READABILITY = "READABILITY";

    /**
     * Related to characteristic MAINTAINABILITY
     */
    public static final String UNDERSTANDABILITY = "UNDERSTANDABILITY";

    /**
     * Related to characteristic SECURITY
     */
    public static final String API_ABUSE = "API_ABUSE";

    /**
     * Related to characteristic SECURITY
     */
    public static final String ERRORS = "ERRORS";

    /**
     * Related to characteristic SECURITY
     */
    public static final String INPUT_VALIDATION_AND_REPRESENTATION = "INPUT_VALIDATION_AND_REPRESENTATION";

    /**
     * Related to characteristic SECURITY
     */
    public static final String SECURITY_FEATURES = "SECURITY_FEATURES";

    /**
     * Related to characteristic EFFICIENCY
     */
    public static final String CPU_EFFICIENCY = "CPU_EFFICIENCY";

    /**
     * Related to characteristic EFFICIENCY
     */
    public static final String MEMORY_EFFICIENCY = "MEMORY_EFFICIENCY";

    /**
     * Related to characteristic EFFICIENCY
     */
    public static final String NETWORK_USE = "NETWORK_USE";

    /**
     * Related to characteristic CHANGEABILITY
     */
    public static final String ARCHITECTURE_CHANGEABILITY = "ARCHITECTURE_CHANGEABILITY";

    /**
     * Related to characteristic CHANGEABILITY
     */
    public static final String DATA_CHANGEABILITY = "DATA_CHANGEABILITY";

    /**
     * Related to characteristic CHANGEABILITY
     */
    public static final String LOGIC_CHANGEABILITY = "LOGIC_CHANGEABILITY";

    /**
     * Related to characteristic RELIABILITY
     */
    public static final String ARCHITECTURE_RELIABILITY = "ARCHITECTURE_RELIABILITY";

    /**
     * Related to characteristic RELIABILITY
     */
    public static final String DATA_RELIABILITY = "DATA_RELIABILITY";

    /**
     * Related to characteristic RELIABILITY
     */
    public static final String EXCEPTION_HANDLING = "EXCEPTION_HANDLING";

    /**
     * Related to characteristic RELIABILITY
     */
    public static final String FAULT_TOLERANCE = "FAULT_TOLERANCE";

    /**
     * Related to characteristic RELIABILITY
     */
    public static final String INSTRUCTION_RELIABILITY = "INSTRUCTION_RELIABILITY";

    /**
     * Related to characteristic RELIABILITY
     */
    public static final String LOGIC_RELIABILITY = "LOGIC_RELIABILITY";

    /**
     * Related to characteristic RELIABILITY
     */
    public static final String RESOURCE_RELIABILITY = "RESOURCE_RELIABILITY";

    /**
     * Related to characteristic RELIABILITY
     */
    public static final String SYNCHRONIZATION_RELIABILITY = "SYNCHRONIZATION_RELIABILITY";

    /**
     * Related to characteristic RELIABILITY
     */
    public static final String UNIT_TESTS = "UNIT_TESTS";

    /**
     * Related to characteristic TESTABILITY
     */
    public static final String INTEGRATION_TESTABILITY = "INTEGRATION_TESTABILITY";

    /**
     * Related to characteristic TESTABILITY
     */
    public static final String UNIT_TESTABILITY = "UNIT_TESTABILITY";

    /**
     * Related to characteristic ACCESSIBILITY
     */
    public static final String USABILITY_ACCESSIBILITY = "USABILITY_ACCESSIBILITY";

    /**
     * Related to characteristic ACCESSIBILITY
     */
    public static final String USABILITY_COMPLIANCE = "USABILITY_COMPLIANCE";

    /**
     * Related to characteristic ACCESSIBILITY
     */
    public static final String USABILITY_EASE_OF_USE = "USABILITY_EASE_OF_USE";

    /**
     * Related to characteristic REUSABILITY
     */
    public static final String REUSABILITY_COMPLIANCE = "REUSABILITY_COMPLIANCE";

    /**
     * Related to characteristic PORTABILITY
     */
    public static final String PORTABILITY_COMPLIANCE = "PORTABILITY_COMPLIANCE";

    /**
     * Related to characteristic MAINTAINABILITY
     */
    public static final String MAINTAINABILITY_COMPLIANCE = "MAINTAINABILITY_COMPLIANCE";

    /**
     * Related to characteristic SECURITY
     */
    public static final String SECURITY_COMPLIANCE = "SECURITY_COMPLIANCE";

    /**
     * Related to characteristic EFFICIENCY
     */
    public static final String EFFICIENCY_COMPLIANCE = "EFFICIENCY_COMPLIANCE";

    /**
     * Related to characteristic CHANGEABILITY
     */
    public static final String CHANGEABILITY_COMPLIANCE = "CHANGEABILITY_COMPLIANCE";

    /**
     * Related to characteristic RELIABILITY
     */
    public static final String RELIABILITY_COMPLIANCE = "RELIABILITY_COMPLIANCE";

    /**
     * Related to characteristic TESTABILITY
     */
    public static final String TESTABILITY_COMPLIANCE = "TESTABILITY_COMPLIANCE";

    private SubCharacteristics() {
      // only constants
    }
  }

  interface Context {

    /**
     * New builder for {@link org.sonar.api.server.rule.RulesDefinition.Repository}.
     * <br>
     * A plugin can add rules to a repository that is defined then executed by another plugin. For instance
     * the FbContrib plugin contributes to the Findbugs plugin rules. In this case no need
     * to execute {@link org.sonar.api.server.rule.RulesDefinition.NewRepository#setName(String)}
     */
    NewRepository createRepository(String key, String language);

    /**
     * @deprecated since 5.2. Simply use {@link #createRepository(String, String)}
     */
    @Deprecated
    NewRepository extendRepository(String key, String language);

    @CheckForNull
    Repository repository(String key);

    List<Repository> repositories();

    /**
     * @deprecated returns empty list since 5.2. Concept of "extended repository" was misleading and not valuable. Simply declare
     * repositories and use {@link #repositories()}. See http://jira.sonarsource.com/browse/SONAR-6709
     */
    @Deprecated
    List<ExtendedRepository> extendedRepositories(String repositoryKey);

    /**
     * @deprecated returns empty list since 5.2. Concept of "extended repository" was misleading and not valuable. Simply declare
     * repositories and use {@link #repositories()}. See http://jira.sonarsource.com/browse/SONAR-6709
     */
    @Deprecated
    List<ExtendedRepository> extendedRepositories();

  }

  interface NewExtendedRepository {
    /**
     * Create a rule with specified key. Max length of key is 200 characters. Key must be unique
     * among the repository
     *
     * @throws IllegalArgumentException is key is not unique. Note a warning was logged up to version 5.4 (included)
     */
    NewRule createRule(String ruleKey);

    @CheckForNull
    NewRule rule(String ruleKey);

    Collection<NewRule> rules();

    String key();

    void done();
  }

  interface NewRepository extends NewExtendedRepository {
    NewRepository setName(String s);
  }

  interface ExtendedRepository {
    String key();

    String language();

    @CheckForNull
    Rule rule(String ruleKey);

    List<Rule> rules();
  }

  interface Repository extends ExtendedRepository {
    String name();
  }

  @Immutable

  /**
   * Factory of {@link org.sonar.api.server.debt.DebtRemediationFunction}.
   */
  interface DebtRemediationFunctions {

    /**
     * Shortcut for {@code create(Type.LINEAR, gap multiplier, null)}.
     *
     * @param gapMultiplier the duration to fix one issue. See {@link DebtRemediationFunction} for details about format.
     * @see org.sonar.api.server.debt.DebtRemediationFunction.Type#LINEAR
     */
    DebtRemediationFunction linear(String gapMultiplier);

    /**
     * Shortcut for {@code create(Type.LINEAR_OFFSET, gap multiplier, base effort)}.
     *
     * @param gapMultiplier duration to fix one point of complexity. See {@link DebtRemediationFunction} for details and format.
     * @param baseEffort    duration to make basic analysis. See {@link DebtRemediationFunction} for details and format.
     * @see org.sonar.api.server.debt.DebtRemediationFunction.Type#LINEAR_OFFSET
     */
    DebtRemediationFunction linearWithOffset(String gapMultiplier, String baseEffort);

    /**
     * Shortcut for {@code create(Type.CONSTANT_ISSUE, null, base effort)}.
     *
     * @param baseEffort cost per issue. See {@link DebtRemediationFunction} for details and format.
     * @see org.sonar.api.server.debt.DebtRemediationFunction.Type#CONSTANT_ISSUE
     */
    DebtRemediationFunction constantPerIssue(String baseEffort);

    /**
     * Flexible way to create a {@link DebtRemediationFunction}. An unchecked exception is thrown if
     * coefficient and/or offset are not valid according to the given @{code type}.
     *
     * @since 5.3
     */
    DebtRemediationFunction create(DebtRemediationFunction.Type type, @Nullable String gapMultiplier, @Nullable String baseEffort);
  }

  interface NewRule {

    String key();

    /**
     * Required rule name
     */
    NewRule setName(String s);

    NewRule setTemplate(boolean template);

    /**
     * Should this rule be enabled by default. For example in SonarLint standalone.
     *
     * @since 6.0
     */
    NewRule setActivatedByDefault(boolean activatedByDefault);

    NewRule setSeverity(String s);

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
    NewRule setType(RuleType t);

    /**
     * The optional description, in HTML format, has no max length. It's exclusive with markdown description
     * (see {@link #setMarkdownDescription(String)})
     */
    NewRule setHtmlDescription(@Nullable String s);

    /**
     * Load description from a file available in classpath. Example : <code>setHtmlDescription(getClass().getResource("/myrepo/Rule1234.html")</code>
     */
    NewRule setHtmlDescription(@Nullable URL classpathUrl);

    /**
     * The optional description, in a restricted Markdown format, has no max length. It's exclusive with HTML description
     * (see {@link #setHtmlDescription(String)})
     */
    NewRule setMarkdownDescription(@Nullable String s);

    /**
     * Load description from a file available in classpath. Example : {@code setMarkdownDescription(getClass().getResource("/myrepo/Rule1234.md")}
     */
    NewRule setMarkdownDescription(@Nullable URL classpathUrl);

    /**
     * Default value is {@link org.sonar.api.rule.RuleStatus#READY}. The value
     * {@link org.sonar.api.rule.RuleStatus#REMOVED} is not accepted and raises an
     * {@link java.lang.IllegalArgumentException}.
     */
    NewRule setStatus(RuleStatus status);

    /**
     * SQALE sub-characteristic. See http://www.sqale.org
     *
     * @see org.sonar.api.server.rule.RulesDefinition.SubCharacteristics for constant values
     * @see #setType(RuleType)
     * @deprecated in 5.5. SQALE Quality Model is replaced by SonarQube Quality Model. This method does nothing.
     * See https://jira.sonarsource.com/browse/MMF-184
     */
    @Deprecated
    NewRule setDebtSubCharacteristic(@Nullable String s);

    /**
     * Factory of {@link org.sonar.api.server.debt.DebtRemediationFunction}
     */
    DebtRemediationFunctions debtRemediationFunctions();

    /**
     * @see #debtRemediationFunctions()
     */
    NewRule setDebtRemediationFunction(@Nullable DebtRemediationFunction fn);

    /**
     * @deprecated since 5.5, replaced by {@link #setGapDescription(String)}
     */
    @Deprecated
    NewRule setEffortToFixDescription(@Nullable String s);

    /**
     * For rules that use LINEAR or LINEAR_OFFSET remediation functions, the meaning
     * of the function parameter (= "gap") must be set. This description
     * explains what 1 point of "gap" represents for the rule.
     * <br>
     * Example: for the "Insufficient condition coverage", this description for the
     * remediation function gap multiplier/base effort would be something like
     * "Effort to test one uncovered condition".
     */
    NewRule setGapDescription(@Nullable String s);

    /**
     * Create a parameter with given unique key. Max length of key is 128 characters.
     */
    NewParam createParam(String paramKey);

    @CheckForNull
    NewParam param(String paramKey);

    Collection<NewParam> params();

    /**
     * @see RuleTagFormat
     */
    NewRule addTags(String... list);

    /**
     * @see RuleTagFormat
     */
    NewRule setTags(String... list);

    /**
     * Optional key that can be used by the rule engine. Not displayed
     * in webapp. For example the Java Checkstyle plugin feeds this field
     * with the internal path ("Checker/TreeWalker/AnnotationUseStyle").
     */
    NewRule setInternalKey(@Nullable String s);

  }

  @Immutable
  interface Rule {

    Repository repository();

    /**
     * @since 6.6 the plugin the rule was declared in
     */
    @CheckForNull
    String pluginKey();

    String key();

    String name();

    /**
     * @see NewRule#setType(RuleType)
     * @since 5.5
     */
    RuleType type();

    String severity();

    @CheckForNull
    String htmlDescription();

    @CheckForNull
    String markdownDescription();

    boolean template();

    /**
     * Should this rule be enabled by default. For example in SonarLint standalone.
     *
     * @since 6.0
     */
    boolean activatedByDefault();

    RuleStatus status();

    /**
     * @see #type()
     * @deprecated in 5.5. SQALE Quality Model is replaced by SonarQube Quality Model. {@code null} is
     * always returned. See https://jira.sonarsource.com/browse/MMF-184
     */
    @CheckForNull
    @Deprecated
    String debtSubCharacteristic();

    @CheckForNull
    DebtRemediationFunction debtRemediationFunction();

    /**
     * @deprecated since 5.5, replaced by {@link #gapDescription()}
     */
    @Deprecated
    @CheckForNull
    String effortToFixDescription();

    @CheckForNull
    String gapDescription();

    @CheckForNull
    Param param(String key);

    List<Param> params();

    Set<String> tags();

    /**
     * @see RulesDefinition.NewRule#setInternalKey(String)
     */
    @CheckForNull
    String internalKey();

  }

  interface NewParam {
    String key();

    NewParam setName(@Nullable String s);

    NewParam setType(RuleParamType t);

    /**
     * Plain-text description. Can be null. Max length is 4000 characters.
     */
    NewParam setDescription(@Nullable String s);

    /**
     * Empty default value will be converted to null. Max length is 4000 characters.
     */
    NewParam setDefaultValue(@Nullable String s);
  }

  @Immutable
  interface Param {

    String key();

    String name();

    @Nullable
    String description();

    @Nullable
    String defaultValue();

    RuleParamType type();
  }

  /**
   * This method is executed when server is started.
   */
  void define(Context context);

}
