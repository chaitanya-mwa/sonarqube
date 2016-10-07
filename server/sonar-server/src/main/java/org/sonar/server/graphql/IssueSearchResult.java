package org.sonar.server.graphql;

import java.util.Collection;
import org.sonarqube.ws.Common;
import org.sonarqube.ws.Issues;

public class IssueSearchResult {

  private final Collection<Issues.Issue> issues;
  private final long total;
  private final Collection<Common.FacetValue> rulesFacet;

  public IssueSearchResult(Collection<Issues.Issue> issues, long total, Collection<Common.FacetValue> rulesFacet) {
    this.issues = issues;
    this.total = total;
    this.rulesFacet = rulesFacet;
  }

  public Collection<Issues.Issue> getIssues() {
    return issues;
  }

  public long getTotal() {
    return total;
  }

  public Collection<Common.FacetValue> getRulesFacet() {
    return rulesFacet;
  }
}
