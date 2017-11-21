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
import org.sonar.core.util.stream.MoreCollectors;
import org.sonar.api.issue.Issue;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.rule.Severity;
import org.sonar.api.rules.RuleType;
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
    Optional<SnapshotDto> lastAnalysis = dbClient.snapshotDao().selectLastAnalysisByRootComponentUuid(dbSession, component.projectUuid());
    if (!lastAnalysis.isPresent()) {
      // project has been deleted at the same time ?
      return;
    }
    Optional<Long> beginningOfLeakPeriod = lastAnalysis.map(SnapshotDto::getPeriodDate);

    MeasureMatrix matrix = matrixLoader.load(dbSession, component, /* TODO */emptyList());

    matrix.getBottomUpComponents().forEach(c -> {
      IssueCounter issueCounter = new IssueCounter(dbClient.issueDao().selectGroupsOfComponentTree(dbSession, c));
      matrix.setValue(c, CoreMetrics.CODE_SMELLS_KEY, issueCounter.countByType(RuleType.CODE_SMELL));
      matrix.setValue(c, CoreMetrics.BUGS_KEY, issueCounter.countByType(RuleType.BUG));
      matrix.setValue(c, CoreMetrics.VULNERABILITIES_KEY, issueCounter.countByType(RuleType.VULNERABILITY));

      matrix.setValue(c, CoreMetrics.VIOLATIONS_KEY, issueCounter.countAll());
      matrix.setValue(c, CoreMetrics.BLOCKER_VIOLATIONS_KEY, issueCounter.countBySeverity(Severity.BLOCKER));
      matrix.setValue(c, CoreMetrics.CRITICAL_VIOLATIONS_KEY, issueCounter.countBySeverity(Severity.CRITICAL));
      matrix.setValue(c, CoreMetrics.MAJOR_VIOLATIONS_KEY, issueCounter.countBySeverity(Severity.MAJOR));
      matrix.setValue(c, CoreMetrics.MINOR_VIOLATIONS_KEY, issueCounter.countBySeverity(Severity.MINOR));
      matrix.setValue(c, CoreMetrics.INFO_VIOLATIONS_KEY, issueCounter.countBySeverity(Severity.INFO));

      matrix.setValue(c, CoreMetrics.FALSE_POSITIVE_ISSUES_KEY, issueCounter.countByResolution(Issue.RESOLUTION_FALSE_POSITIVE));
      matrix.setValue(c, CoreMetrics.WONT_FIX_ISSUES_KEY, issueCounter.countByResolution(Issue.RESOLUTION_WONT_FIX));
      matrix.setValue(c, CoreMetrics.OPEN_ISSUES_KEY, issueCounter.countByStatus(Issue.STATUS_OPEN));
      matrix.setValue(c, CoreMetrics.REOPENED_ISSUES_KEY, issueCounter.countByStatus(Issue.STATUS_REOPENED));
      matrix.setValue(c, CoreMetrics.CONFIRMED_ISSUES_KEY, issueCounter.countByStatus(Issue.STATUS_CONFIRMED));

      matrix.setValue(c, CoreMetrics.TECHNICAL_DEBT_KEY, issueCounter.effort(RuleType.CODE_SMELL));
      matrix.setValue(c, CoreMetrics.RELIABILITY_REMEDIATION_EFFORT_KEY, issueCounter.effort(RuleType.BUG));
      matrix.setValue(c, CoreMetrics.SECURITY_REMEDIATION_EFFORT_KEY, issueCounter.effort(RuleType.VULNERABILITY));

      // TODO new_technical_debt, sqale_rating, new_maintainability_rating, sqale_debt_ratio ?, new_sqale_debt_ratio ?, effort_to_reach_maintainability_rating_a
      matrix.setValue(c, CoreMetrics.RELIABILITY_RATING_KEY, RATING_BY_SEVERITY.get(issueCounter.getMaxSeverity(RuleType.BUG).orElse(Severity.INFO)));
      matrix.setValue(c, CoreMetrics.SECURITY_RATING_KEY, RATING_BY_SEVERITY.get(issueCounter.getMaxSeverity(RuleType.VULNERABILITY).orElse(Severity.INFO)));

      if (beginningOfLeakPeriod.isPresent()) {
        IssueCounter issueLeakCounter = new IssueCounter(dbClient.issueDao().selectGroupsOfComponentTreeOnLeak(dbSession, c, beginningOfLeakPeriod.get()));
        matrix.setVariation(c, CoreMetrics.NEW_CODE_SMELLS_KEY, issueLeakCounter.countByType(RuleType.CODE_SMELL));
        matrix.setVariation(c, CoreMetrics.NEW_BUGS_KEY, issueLeakCounter.countByType(RuleType.BUG));
        matrix.setVariation(c, CoreMetrics.NEW_VULNERABILITIES_KEY, issueLeakCounter.countByType(RuleType.VULNERABILITY));

        matrix.setVariation(c, CoreMetrics.NEW_VIOLATIONS_KEY, issueLeakCounter.countAll());
        matrix.setVariation(c, CoreMetrics.NEW_BLOCKER_VIOLATIONS_KEY, issueLeakCounter.countBySeverity(Severity.BLOCKER));
        matrix.setVariation(c, CoreMetrics.NEW_CRITICAL_VIOLATIONS_KEY, issueLeakCounter.countBySeverity(Severity.CRITICAL));
        matrix.setVariation(c, CoreMetrics.NEW_MAJOR_VIOLATIONS_KEY, issueLeakCounter.countBySeverity(Severity.MAJOR));
        matrix.setVariation(c, CoreMetrics.NEW_MINOR_VIOLATIONS_KEY, issueLeakCounter.countBySeverity(Severity.MINOR));
        matrix.setVariation(c, CoreMetrics.NEW_INFO_VIOLATIONS_KEY, issueLeakCounter.countBySeverity(Severity.INFO));

        matrix.setVariation(c, CoreMetrics.NEW_TECHNICAL_DEBT_KEY, issueLeakCounter.effort(RuleType.CODE_SMELL));
        matrix.setVariation(c, CoreMetrics.NEW_RELIABILITY_REMEDIATION_EFFORT_KEY, issueLeakCounter.effort(RuleType.BUG));
        matrix.setVariation(c, CoreMetrics.NEW_SECURITY_REMEDIATION_EFFORT_KEY, issueLeakCounter.effort(RuleType.VULNERABILITY));


        matrix.setVariation(c, CoreMetrics.NEW_RELIABILITY_RATING_KEY, RATING_BY_SEVERITY.get(issueLeakCounter.getMaxSeverity(RuleType.BUG).orElse(Severity.INFO)));
        matrix.setVariation(c, CoreMetrics.NEW_SECURITY_RATING_KEY, RATING_BY_SEVERITY.get(issueLeakCounter.getMaxSeverity(RuleType.VULNERABILITY).orElse(Severity.INFO)));
      }
    });

    // persist the measures that have been created or updated
    matrix.getTouched().forEach(m -> dbClient.liveMeasureDao().insertOrUpdate(dbSession, m));

    ComponentDto project = dbClient.componentDao().selectByUuid(dbSession, component.projectUuid()).get();
    qualityGateComputer.recalculateQualityGate(dbSession, project, matrix.getTouched().filter(measure -> measure.getComponentUuid().equals(project.uuid())).collect(MoreCollectors.toList()));

    dbSession.commit();
  }
}
