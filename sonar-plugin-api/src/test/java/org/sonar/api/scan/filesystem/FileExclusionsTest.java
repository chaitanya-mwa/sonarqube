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
package org.sonar.api.scan.filesystem;

import org.junit.Test;
import org.sonar.api.config.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonar.api.CoreProperties.PROJECT_EXCLUSIONS_PROPERTY;
import static org.sonar.api.CoreProperties.PROJECT_INCLUSIONS_PROPERTY;
import static org.sonar.api.CoreProperties.PROJECT_TEST_EXCLUSIONS_PROPERTY;
import static org.sonar.api.CoreProperties.PROJECT_TEST_INCLUSIONS_PROPERTY;

public class FileExclusionsTest {
  @Test
  public void ignore_inclusion_of_world() {
    Configuration config = mock(Configuration.class);
    when(config.getStringArray(PROJECT_INCLUSIONS_PROPERTY)).thenReturn(new String[] {"**/*"});
    when(config.getStringArray(PROJECT_TEST_INCLUSIONS_PROPERTY)).thenReturn(new String[] {"**/*"});
    assertThat(new FileExclusions(config).sourceInclusions()).isEmpty();
    assertThat(new FileExclusions(config).testInclusions()).isEmpty();
  }

  @Test
  public void load_inclusions() {
    Configuration config = mock(Configuration.class);
    when(config.getStringArray(PROJECT_INCLUSIONS_PROPERTY)).thenReturn(new String[] {"**/*Foo.java"});
    when(config.getStringArray(PROJECT_TEST_INCLUSIONS_PROPERTY)).thenReturn(new String[] {"**/*FooTest.java"});
    FileExclusions moduleExclusions = new FileExclusions(config);

    assertThat(moduleExclusions.sourceInclusions()).containsOnly("**/*Foo.java");
    assertThat(moduleExclusions.testInclusions()).containsOnly("**/*FooTest.java");
  }

  @Test
  public void load_exclusions() {
    Configuration config = mock(Configuration.class);
    when(config.getStringArray(anyString())).thenReturn(new String[0]);
    when(config.getStringArray(PROJECT_EXCLUSIONS_PROPERTY)).thenReturn(new String[] {"**/*Foo.java"});
    when(config.getStringArray(PROJECT_TEST_EXCLUSIONS_PROPERTY)).thenReturn(new String[] {"**/*FooTest.java"});
    FileExclusions moduleExclusions = new FileExclusions(config);

    assertThat(moduleExclusions.sourceInclusions()).isEmpty();
    assertThat(moduleExclusions.sourceExclusions()).containsOnly("**/*Foo.java");
    assertThat(moduleExclusions.testInclusions()).isEmpty();
    assertThat(moduleExclusions.testExclusions()).containsOnly("**/*FooTest.java");
  }
}
