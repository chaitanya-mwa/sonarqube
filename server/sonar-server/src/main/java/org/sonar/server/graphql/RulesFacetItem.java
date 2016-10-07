package org.sonar.server.graphql;

public class RulesFacetItem {

  private final String ruleKey;
  private final long count;

  public RulesFacetItem(String ruleKey, long count) {
    this.ruleKey = ruleKey;
    this.count = count;
  }

  public String getRuleKey() {
    return ruleKey;
  }

  public long getCount() {
    return count;
  }
}
