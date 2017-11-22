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
package org.sonar.server.measure.live;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.sonar.api.issue.Issue;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.rule.Severity;
import org.sonar.api.rules.RuleType;
import org.sonar.api.utils.log.Loggers;
import org.sonar.api.utils.log.Profiler;
import org.sonar.core.util.stream.MoreCollectors;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.SnapshotDto;
import org.sonar.server.computation.task.projectanalysis.qualitymodel.RatingGrid;

import static java.util.Collections.emptyList;
import static org.sonar.api.rule.Severity.BLOCKER;
import static org.sonar.api.rule.Severity.CRITICAL;
import static org.sonar.api.rule.Severity.INFO;
import static org.sonar.api.rule.Severity.MAJOR;
import static org.sonar.api.rule.Severity.MINOR;
import static org.sonar.server.computation.task.projectanalysis.qualitymodel.RatingGrid.Rating.A;
import static org.sonar.server.computation.task.projectanalysis.qualitymodel.RatingGrid.Rating.B;
import static org.sonar.server.computation.task.projectanalysis.qualitymodel.RatingGrid.Rating.C;
import static org.sonar.server.computation.task.projectanalysis.qualitymodel.RatingGrid.Rating.D;
import static org.sonar.server.computation.task.projectanalysis.qualitymodel.RatingGrid.Rating.E;

public class LiveMeasureComputerImpl implements LiveMeasureComputer {

  // duplication of ReliabilityAndSecurityRatingMeasuresVisitor
  private static final Map<String, RatingGrid.Rating> RATING_BY_SEVERITY = ImmutableMap.of(
    BLOCKER, E,
    CRITICAL, D,
    MAJOR, C,
    MINOR, B,
    INFO, A);

  private final DbClient dbClient;
  private final MeasureMatrixLoader matrixLoader;
  private final LiveQualityGateComputer qualityGateComputer;

  public LiveMeasureComputerImpl(DbClient dbClient, MeasureMatrixLoader matrixLoader, LiveQualityGateComputer qualityGateComputer) {
    this.dbClient = dbClient;
    this.matrixLoader = matrixLoader;
    this.qualityGateComputer = qualityGateComputer;
  }

  @Override
  public void refresh(DbSession dbSession, ComponentDto component) {
    Profiler largeProfiler = Profiler.create(Loggers.get(getClass()));
    largeProfiler.start();
    Profiler profiler = Profiler.create(Loggers.get(getClass()));
    profiler.start();
    Optional<SnapshotDto> lastAnalysis = dbClient.snapshotDao().selectLastAnalysisByRootComponentUuid(dbSession, component.projectUuid());
    if (!lastAnalysis.isPresent()) {
      // project has been deleted at the same time ?
      return;
    }
    Optional<Long> beginningOfLeakPeriod = lastAnalysis.map(SnapshotDto::getPeriodDate);
    profiler.stopInfo("- load last snapshot");

    profiler.start();
    MeasureMatrix matrix = matrixLoader.load(dbSession, component, /* TODO */emptyList());
    profiler.stopInfo("- load matrix");

    matrix.getBottomUpComponents().forEach(c -> {
      profiler.start();
      Profiler smallProfiler = Profiler.create(Loggers.get(getClass())).start();
      IssueCounter issueCounter = new IssueCounter(dbClient.issueDao().selectGroupsOfComponentTreeOnLeak(dbSession, c, beginningOfLeakPeriod.orElse(Long.MAX_VALUE)));
      smallProfiler.stopInfo("-- select all issues");
      matrix.setValue(c, CoreMetrics.CODE_SMELLS_KEY, issueCounter.countUnresolvedByType(RuleType.CODE_SMELL, false));
      matrix.setValue(c, CoreMetrics.BUGS_KEY, issueCounter.countUnresolvedByType(RuleType.BUG, false));
      matrix.setValue(c, CoreMetrics.VULNERABILITIES_KEY, issueCounter.countUnresolvedByType(RuleType.VULNERABILITY, false));

      matrix.setValue(c, CoreMetrics.VIOLATIONS_KEY, issueCounter.countUnresolved(false));
      matrix.setValue(c, CoreMetrics.BLOCKER_VIOLATIONS_KEY, issueCounter.countUnresolvedBySeverity(Severity.BLOCKER, false));
      matrix.setValue(c, CoreMetrics.CRITICAL_VIOLATIONS_KEY, issueCounter.countUnresolvedBySeverity(Severity.CRITICAL, false));
      matrix.setValue(c, CoreMetrics.MAJOR_VIOLATIONS_KEY, issueCounter.countUnresolvedBySeverity(Severity.MAJOR, false));
      matrix.setValue(c, CoreMetrics.MINOR_VIOLATIONS_KEY, issueCounter.countUnresolvedBySeverity(Severity.MINOR, false));
      matrix.setValue(c, CoreMetrics.INFO_VIOLATIONS_KEY, issueCounter.countUnresolvedBySeverity(Severity.INFO, false));

      matrix.setValue(c, CoreMetrics.FALSE_POSITIVE_ISSUES_KEY, issueCounter.countByResolution(Issue.RESOLUTION_FALSE_POSITIVE, false));
      matrix.setValue(c, CoreMetrics.WONT_FIX_ISSUES_KEY, issueCounter.countByResolution(Issue.RESOLUTION_WONT_FIX, false));
      matrix.setValue(c, CoreMetrics.OPEN_ISSUES_KEY, issueCounter.countByStatus(Issue.STATUS_OPEN, false));
      matrix.setValue(c, CoreMetrics.REOPENED_ISSUES_KEY, issueCounter.countByStatus(Issue.STATUS_REOPENED, false));
      matrix.setValue(c, CoreMetrics.CONFIRMED_ISSUES_KEY, issueCounter.countByStatus(Issue.STATUS_CONFIRMED, false));

      matrix.setValue(c, CoreMetrics.TECHNICAL_DEBT_KEY, issueCounter.effortOfUnresolved(RuleType.CODE_SMELL, false));
      matrix.setValue(c, CoreMetrics.RELIABILITY_REMEDIATION_EFFORT_KEY, issueCounter.effortOfUnresolved(RuleType.BUG, false));
      matrix.setValue(c, CoreMetrics.SECURITY_REMEDIATION_EFFORT_KEY, issueCounter.effortOfUnresolved(RuleType.VULNERABILITY, false));

      // TODO new_technical_debt, sqale_rating, new_maintainability_rating, sqale_debt_ratio, new_sqale_debt_ratio,
      // effort_to_reach_maintainability_rating_a
      matrix.setValue(c, CoreMetrics.RELIABILITY_RATING_KEY, RATING_BY_SEVERITY.get(issueCounter.getMaxSeverityOfUnresolved(RuleType.BUG, false).orElse(Severity.INFO)));
      matrix.setValue(c, CoreMetrics.SECURITY_RATING_KEY, RATING_BY_SEVERITY.get(issueCounter.getMaxSeverityOfUnresolved(RuleType.VULNERABILITY, false).orElse(Severity.INFO)));

      if (beginningOfLeakPeriod.isPresent()) {
        matrix.setVariation(c, CoreMetrics.NEW_CODE_SMELLS_KEY, issueCounter.countUnresolvedByType(RuleType.CODE_SMELL, true));
        matrix.setVariation(c, CoreMetrics.NEW_BUGS_KEY, issueCounter.countUnresolvedByType(RuleType.BUG, true));
        matrix.setVariation(c, CoreMetrics.NEW_VULNERABILITIES_KEY, issueCounter.countUnresolvedByType(RuleType.VULNERABILITY, true));

        matrix.setVariation(c, CoreMetrics.NEW_VIOLATIONS_KEY, issueCounter.countUnresolved(true));
        matrix.setVariation(c, CoreMetrics.NEW_BLOCKER_VIOLATIONS_KEY, issueCounter.countUnresolvedBySeverity(Severity.BLOCKER, true));
        matrix.setVariation(c, CoreMetrics.NEW_CRITICAL_VIOLATIONS_KEY, issueCounter.countUnresolvedBySeverity(Severity.CRITICAL, true));
        matrix.setVariation(c, CoreMetrics.NEW_MAJOR_VIOLATIONS_KEY, issueCounter.countUnresolvedBySeverity(Severity.MAJOR, true));
        matrix.setVariation(c, CoreMetrics.NEW_MINOR_VIOLATIONS_KEY, issueCounter.countUnresolvedBySeverity(Severity.MINOR, true));
        matrix.setVariation(c, CoreMetrics.NEW_INFO_VIOLATIONS_KEY, issueCounter.countUnresolvedBySeverity(Severity.INFO, true));

        matrix.setVariation(c, CoreMetrics.NEW_TECHNICAL_DEBT_KEY, issueCounter.effortOfUnresolved(RuleType.CODE_SMELL, true));
        matrix.setVariation(c, CoreMetrics.NEW_RELIABILITY_REMEDIATION_EFFORT_KEY, issueCounter.effortOfUnresolved(RuleType.BUG, true));
        matrix.setVariation(c, CoreMetrics.NEW_SECURITY_REMEDIATION_EFFORT_KEY, issueCounter.effortOfUnresolved(RuleType.VULNERABILITY, true));

        matrix.setVariation(c, CoreMetrics.NEW_RELIABILITY_RATING_KEY, RATING_BY_SEVERITY.get(issueCounter.getMaxSeverityOfUnresolved(RuleType.BUG, true).orElse(Severity.INFO)));
        matrix.setVariation(c, CoreMetrics.NEW_SECURITY_RATING_KEY,
          RATING_BY_SEVERITY.get(issueCounter.getMaxSeverityOfUnresolved(RuleType.VULNERABILITY, true).orElse(Severity.INFO)));
      }
      profiler.stopInfo("- compute component " + c.name());
    });

    profiler.start();
    // persist the measures that have been created or updated
    AtomicInteger touched = new AtomicInteger();
    matrix.getTouched().forEach(m -> {
      dbClient.liveMeasureDao().insertOrUpdate(dbSession, m);
      touched.incrementAndGet();
    });
    profiler.stopInfo("- persist " + touched.get() + " touched measures");

    profiler.start();
    ComponentDto project = matrix.getProject();
    qualityGateComputer.recalculateQualityGate(dbSession, project,
      matrix.getTouched().filter(measure -> measure.getComponentUuid().equals(project.uuid())).collect(MoreCollectors.toList()));
    profiler.stopInfo("compute gate");

    dbSession.commit();

    largeProfiler.stopInfo("------- Live measures refreshed -------------");
  }
}
