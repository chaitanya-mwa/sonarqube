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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.server.rule.RulesDefinition.ExtendedRepository;
import org.sonar.api.server.rule.RulesDefinition.NewRepository;
import org.sonar.api.server.rule.RulesDefinition.Repository;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;

/**
 * Instantiated by core but not by plugins, except for their tests.
 */
public class RulesDefinitionContext implements RulesDefinition.Context {
  private final Map<String, Repository> repositoriesByKey = new HashMap<>();
  String currentPluginKey;

  /**
   * New builder for {@link org.sonar.api.server.rule.RulesDefinitionImpl.Repository}.
   * <br>
   * A plugin can add rules to a repository that is defined then executed by another plugin. For instance
   * the FbContrib plugin contributes to the Findbugs plugin rules. In this case no need
   * to execute {@link org.sonar.api.server.rule.RulesDefinitionImpl.NewRepository#setName(String)}
   */
  public NewRepository createRepository(String key, String language) {
    return new NewRepositoryImpl(this, key, language);
  }

  /**
   * @deprecated since 5.2. Simply use {@link #createRepository(String, String)}
   */
  @Deprecated
  public NewRepository extendRepository(String key, String language) {
    return createRepository(key, language);
  }

  @CheckForNull
  public Repository repository(String key) {
    return repositoriesByKey.get(key);
  }

  public List<Repository> repositories() {
    return unmodifiableList(new ArrayList<>(repositoriesByKey.values()));
  }

  /**
   * @deprecated returns empty list since 5.2. Concept of "extended repository" was misleading and not valuable. Simply declare
   * repositories and use {@link #repositories()}. See http://jira.sonarsource.com/browse/SONAR-6709
   */
  @Deprecated
  public List<ExtendedRepository> extendedRepositories(String repositoryKey) {
    return emptyList();
  }

  /**
   * @deprecated returns empty list since 5.2. Concept of "extended repository" was misleading and not valuable. Simply declare
   * repositories and use {@link #repositories()}. See http://jira.sonarsource.com/browse/SONAR-6709
   */
  @Deprecated
  public List<ExtendedRepository> extendedRepositories() {
    return emptyList();
  }

  void registerRepository(NewRepositoryImpl newRepository) {
    Repository existing = repositoriesByKey.get(newRepository.key());
    if (existing != null) {
      String existingLanguage = existing.language();
      checkState(existingLanguage.equals(newRepository.language),
        "The rule repository '%s' must not be defined for two different languages: %s and %s",
        newRepository.key, existingLanguage, newRepository.language);
    }
    repositoriesByKey.put(newRepository.key, new RepositoryImpl(newRepository, existing));
  }

  public void setCurrentPluginKey(@Nullable String pluginKey) {
    this.currentPluginKey = pluginKey;
  }

}
