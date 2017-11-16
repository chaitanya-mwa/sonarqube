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
package org.sonarqube.tests.livemeasures;

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.build.SonarScanner;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.sonarqube.qa.util.Tester;
import org.sonarqube.tests.Category4Suite;
import org.sonarqube.ws.client.issue.DoTransitionRequest;
import org.sonarqube.ws.client.issue.SearchWsRequest;
import org.sonarqube.ws.client.measure.ComponentWsRequest;
import util.ItUtils;

import static java.lang.Integer.parseInt;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class LiveMeasuresTest {

  private static final String PROJECT_KEY = "LiveMeasuresTestExample";
  private static final String PROJECT_DIR_V1 = "livemeasures/LiveMeasuresTest-v1";
  private static final String PROJECT_DIR_V2 = "livemeasures/LiveMeasuresTest-v2";

  @ClassRule
  public static Orchestrator orchestrator = Category4Suite.ORCHESTRATOR;

  @Rule
  public Tester tester = new Tester(orchestrator);

  @Before
  public void resetData() {
    orchestrator.resetData();
  }

  @Test
  public void live_update_project_level_measures_on_issue_transition() {
    ItUtils.restoreProfile(orchestrator, getClass().getResource("/livemeasures/LiveMeasuresTest/one-bug-per-line-profile.xml"));
    orchestrator.getServer().provisionProject(PROJECT_KEY, "LiveMeasuresTestExample");
    orchestrator.getServer().associateProjectToQualityProfile(PROJECT_KEY, "xoo", "one-bug-per-line-profile");

    orchestrator.executeBuildQuietly(SonarScanner.create(ItUtils.projectDir(PROJECT_DIR_V1)));

    assertThat(numberOf("bugs")).as("Number of bugs before change").isEqualTo(2);

    String issueKey = tester.wsClient().issues().search(
      new SearchWsRequest().setResolved(false).setComponentKeys(singletonList(PROJECT_KEY))).getIssuesList().get(0).getKey();
    tester.wsClient().issues().doTransition(
      new DoTransitionRequest(issueKey, "falsepositive")
    );

    assertThat(numberOf("bugs")).as("Number of bugs after change").isEqualTo(1);

    orchestrator.executeBuildQuietly(SonarScanner.create(ItUtils.projectDir(PROJECT_DIR_V2)));

    assertThat(numberOf("bugs")).as("Number of bugs v2, before change").isEqualTo(2);
    assertThat(numberOfNew("new_bugs")).as("Number of new bugs, before change").isEqualTo(1);

    String issueKey2 = tester.wsClient().issues().search(
      new SearchWsRequest().setResolved(false).setComponentKeys(singletonList(PROJECT_KEY)).setSinceLeakPeriod(true)).getIssuesList().get(0).getKey();
    tester.wsClient().issues().doTransition(
      new DoTransitionRequest(issueKey2, "falsepositive")
    );

    assertThat(numberOf("bugs")).as("Number of bugs v2, after change").isEqualTo(1);
    assertThat(numberOfNew("new_bugs")).as("Number of new bugs, after change").isEqualTo(0);

    String issueKey3 = tester.wsClient().issues().search(
      new SearchWsRequest().setResolved(false).setComponentKeys(singletonList(PROJECT_KEY))).getIssuesList().get(0).getKey();
    tester.wsClient().issues().doTransition(
      new DoTransitionRequest(issueKey3, "falsepositive")
    );

    assertThat(numberOf("bugs")).as("Number of bugs v2, after change").isEqualTo(0);
    assertThat(numberOfNew("new_bugs")).as("Number of new bugs, after change").isEqualTo(0);
  }

  @Test
  public void live_update_project_level_measures_on_issue_type_change() {
    ItUtils.restoreProfile(orchestrator, getClass().getResource("/livemeasures/LiveMeasuresTest/one-bug-per-line-profile.xml"));
    orchestrator.getServer().provisionProject(PROJECT_KEY, "LiveMeasuresTestExample");
    orchestrator.getServer().associateProjectToQualityProfile(PROJECT_KEY, "xoo", "one-bug-per-line-profile");

    orchestrator.executeBuildQuietly(SonarScanner.create(ItUtils.projectDir(PROJECT_DIR_V1)));

    assertThat(numberOf("bugs")).as("Number of bugs before change").isEqualTo(1);
    assertThat(numberOf("code_smells")).as("Number of code_smells before change").isEqualTo(0);

    String issueKey = tester.wsClient().issues().search(new SearchWsRequest()).getIssuesList().get(0).getKey();
    tester.wsClient().issues().doTransition(
      new DoTransitionRequest(issueKey, "falsepositive")
    );

    assertThat(numberOf("bugs")).as("Number of bugs after change").isEqualTo(0);
    assertThat(numberOf("code_smells")).as("Number of code_smells before change").isEqualTo(1);

  }

  private int numberOf(String metricKey) {
    return parseInt(tester.wsClient().measures().component(
      new ComponentWsRequest()
        .setMetricKeys(singletonList(metricKey))
        .setComponent(PROJECT_KEY)
    ).getComponent().getMeasuresList().get(0).getValue());
  }

  private int numberOfNew(String metricKey) {
    return parseInt(tester.wsClient().measures().component(
      new ComponentWsRequest()
        .setMetricKeys(singletonList(metricKey))
        .setComponent(PROJECT_KEY)
    ).getComponent().getMeasuresList().get(0).getPeriods().getPeriodsValueList().get(0).getValue());
  }
}
