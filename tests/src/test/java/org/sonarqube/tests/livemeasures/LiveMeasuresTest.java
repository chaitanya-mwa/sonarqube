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
import java.util.List;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.sonarqube.qa.util.Tester;
import org.sonarqube.tests.Category4Suite;
import org.sonarqube.ws.WsMeasures;
import org.sonarqube.ws.client.issue.SearchWsRequest;
import org.sonarqube.ws.client.measure.ComponentWsRequest;
import util.ItUtils;

import static java.lang.Integer.parseInt;
import static java.util.Arrays.stream;
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

    assertThat(numberOfBugs()).isEqualTo(2);
    assertThat(numberOfNewBugs()).isEqualTo(0);

    assertThat(numberOfIssueResults(r -> r.setSinceLeakPeriod(false))).isEqualTo(2);
    assertThat(numberOfIssueResults(r -> r.setSinceLeakPeriod(true))).isEqualTo(0);// (fails with actual=2)

    orchestrator.executeBuildQuietly(SonarScanner.create(ItUtils.projectDir(PROJECT_DIR_V2)));

    assertThat(numberOfBugs()).isEqualTo(3);
    assertThat(numberOfNewBugs()).isEqualTo(1);

    assertThat(numberOfIssueResults(r -> r.setSinceLeakPeriod(false))).isEqualTo(2);// (fails with actual=3)
    assertThat(numberOfIssueResults(r -> r.setSinceLeakPeriod(true))).isEqualTo(1);// (fails with actual=3)
  }

  @SafeVarargs
  private final int numberOfIssueResults(Consumer<SearchWsRequest>... consumers) {
    SearchWsRequest request = new SearchWsRequest().setComponentKeys(singletonList(PROJECT_KEY));
    stream(consumers).forEach(c -> c.accept(request));
    return tester.wsClient().issues().search(
      request).getIssuesList().size();
  }

  private int numberOfBugs() {
    return parseInt(tester.wsClient().measures().component(
      new ComponentWsRequest()
        .setMetricKeys(singletonList("bugs"))
        .setComponent(PROJECT_KEY)
    ).getComponent().getMeasuresList().get(0).getValue());
  }

  private int numberOfNewBugs() {
    List<WsMeasures.Measure> measuresList = tester.wsClient().measures().component(
      new ComponentWsRequest()
        .setMetricKeys(singletonList("new_bugs"))
        .setComponent(PROJECT_KEY)
    ).getComponent().getMeasuresList();
    if (measuresList.isEmpty()) {
      return 0;
    }
    return parseInt(measuresList.get(0).getPeriods().getPeriodsValueList().get(0).getValue());
  }
}
