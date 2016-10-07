package it.graphql;

import com.sonar.orchestrator.Orchestrator;
import org.junit.ClassRule;
import org.junit.Test;

public class GraphqlTest {
  @ClassRule
  public static final Orchestrator ORCHESTRATOR = Orchestrator.builderEnv()
    .setOrchestratorProperty("javaVersion", "LATEST_RELEASE").addPlugin("java")
    .setOrchestratorProperty("orchestrator.keepDatabase", "true")
    .build();

  @Test
  public void poc_graphql() {
    System.out.println("breakpoint");
  }
}
