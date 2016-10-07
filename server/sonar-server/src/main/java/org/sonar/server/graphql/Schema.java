/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
package org.sonar.server.graphql;

import graphql.GraphQL;
import graphql.Scalars;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeReference;
import java.util.List;
import org.sonar.api.server.ws.LocalConnector;
import org.sonarqube.ws.Common;
import org.sonarqube.ws.Issues;
import org.sonarqube.ws.Rules;
import org.sonarqube.ws.WsComponents;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.WsClientFactories;
import org.sonarqube.ws.client.component.ShowWsRequest;
import org.sonarqube.ws.client.issue.SearchWsRequest;

import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;
import static java.util.Arrays.asList;
import static org.apache.commons.lang.StringUtils.isNotEmpty;

public class Schema {

  public static GraphQL create() {

    GraphQLObjectType componentType = newComponentType();

    GraphQLObjectType ruleType = newRuleType();

    GraphQLObjectType issueType = newIssueType(componentType, ruleType);

    GraphQLObjectType issueSearchType = newIssueSearchType(issueType, ruleType);

    GraphQLObjectType queryType = newObject()
      .name("sonarQubeQuery")
      .field(newFieldDefinition()
        .type(ruleType)
        .name("rule")
        .argument(GraphQLArgument.newArgument()
          .name("key")
          .type(new GraphQLNonNull(GraphQLString))
          .build())
        .dataFetcher(env -> {
          RequestContext ctx = getContext(env);
          String ruleKey = env.getArgument("key");
          Rules.Rule rule = ctx.getCached(ruleKey, k -> searchRule(getContext(env).getLocalConnector(), k));
          return rule;
        })
        .build())

      .field(newFieldDefinition()
        .type(issueSearchType)
        .name("issueSearch")
        .argument(GraphQLArgument.newArgument()
          .name("severity")
          .type(GraphQLString)
          .build())
        .argument(GraphQLArgument.newArgument()
          .name("type")
          .type(GraphQLString)
          .build())

        .dataFetcher(env -> {
          RequestContext ctx = getContext(env);
          SearchWsRequest searchRequest = new SearchWsRequest();
          String severity = env.getArgument("severity");
          if (isNotEmpty(severity)) {
            searchRequest.setSeverities(asList(severity));
          }
          String type = env.getArgument("type");
          if (isNotEmpty(type)) {
            searchRequest.setTypes(asList(type));
          }
          searchRequest.setFacets(asList("rules"));

          Issues.SearchWsResponse wsResponse = searchIssues(ctx.getLocalConnector(), searchRequest);
          List<Issues.Issue> issues = wsResponse.getIssuesList();
          // TODO could be optimized by loading facets only if needed
          return new IssueSearchResult(issues, wsResponse.getTotal(), wsResponse.getFacets().getFacets(0).getValuesList());
        })
        .build())

      .field(newFieldDefinition()
        .type(issueType)
        .name("issue")
        .dataFetcher(env -> searchIssues(getContext(env).getLocalConnector(), new SearchWsRequest()).getIssues(0))
        .build())
      .build();

    GraphQLSchema schema = GraphQLSchema.newSchema()
      .query(queryType)
      .build();

    return new GraphQL(schema);
  }

  private static GraphQLObjectType newIssueSearchType(GraphQLObjectType issueType, GraphQLObjectType ruleType) {
    return newObject()
      .name("IssueSearch")
      .field(newFieldDefinition()
        .name("issues")
        .type(new GraphQLList(issueType))
        .build())
      .field(newFieldDefinition()
        .name("rulesFacet")
        .type(new GraphQLList(newRuleFacetItemType(ruleType)))
        .build())
      .field(newFieldDefinition()
        .name("total")
        .type(Scalars.GraphQLLong)
        .build())
      .build();
  }

  private static GraphQLObjectType newIssueType(GraphQLObjectType componentType, GraphQLObjectType ruleType) {
    return newObject()
      .name("Issue")
      .description("A SonarQube issue")
      .field(newFieldDefinition()
        .name("key")
        .description("The key.")
        .type(GraphQLString)
        .build())
      .field(newFieldDefinition()
        .name("message")
        .description("The msg")
        .type(GraphQLString)
        .build())
      .field(newFieldDefinition()
        .name("severity")
        .description("The severity")
        .type(GraphQLString)
        .build())
      .field(newFieldDefinition()
        .name("type")
        .type(GraphQLString)
        .build())
      .field(newFieldDefinition()
        .name("component")
        .type(componentType)
        .dataFetcher(env -> {
          RequestContext ctx = getContext(env);
          Issues.Issue issue = (Issues.Issue) env.getSource();
          WsComponents.Component component = ctx.getCached(issue.getComponent(), k -> searchComponent(ctx.getLocalConnector(), k));
          return component;
        })
        .build())
      .field(newFieldDefinition()
        .name("project")
        .type(componentType)
        .dataFetcher(env -> {
          RequestContext ctx = getContext(env);
          Issues.Issue issue = (Issues.Issue) env.getSource();
          WsComponents.Component component = ctx.getCached(issue.getProject(), k -> searchComponent(ctx.getLocalConnector(), k));
          return component;
        })
        .build())
      .field(newFieldDefinition()
        .name("rule")
        .type(ruleType)
        .dataFetcher(env -> {
          RequestContext ctx = getContext(env);
          String ruleKey = ((Issues.Issue) env.getSource()).getRule();
          Rules.Rule rule = ctx.getCached(ruleKey, k -> searchRule(getContext(env).getLocalConnector(), k));
          return rule;
        })
        .build())
      .build();
  }

  private static GraphQLObjectType newRuleType() {
    return newObject()
      .name("Rule")
      .field(newFieldDefinition()
        .name("key")
        .type(GraphQLString)
        .build())
      .field(newFieldDefinition()
        .name("name")
        .type(GraphQLString)
        .build())
      .field(newFieldDefinition()
        .name("issues")
        .type(new GraphQLList(new GraphQLTypeReference("Issue")))
        .dataFetcher(env -> {
          RequestContext ctx = getContext(env);
          Rules.Rule rule = (Rules.Rule) env.getSource();
          List<Issues.Issue> issues = searchIssues(ctx.getLocalConnector(), new SearchWsRequest().setRules(asList(rule.getKey()))).getIssuesList();
          return issues;
        })
        .build())
      .build();
  }

  private static GraphQLObjectType newComponentType() {
    return newObject()
      .name("Component")
      .field(newFieldDefinition()
        .name("key")
        .type(GraphQLString)
        .build())
      .field(newFieldDefinition()
        .name("name")
        .type(GraphQLString)
        .build())
      .field(newFieldDefinition()
        .name("description")
        .type(GraphQLString)
        .build())
      .field(newFieldDefinition()
        .name("qualifier")
        .type(GraphQLString)
        .build())
      .build();
  }

  private static GraphQLObjectType newRuleFacetItemType(GraphQLObjectType ruleType) {
    return newObject()
      .name("ruleFacetItem")
      .field(newFieldDefinition()
        .name("rule")
        .type(ruleType)
        .dataFetcher(env -> {
          RequestContext ctx = getContext(env);
          String ruleKey = ((Common.FacetValue) env.getSource()).getVal();
          Rules.Rule rule = ctx.getCached(ruleKey, k -> searchRule(getContext(env).getLocalConnector(), k));
          return rule;
        })
        .build())
      .field(newFieldDefinition()
        .name("count")
        .type(Scalars.GraphQLLong)
        .build())
      .build();
  }

  public static Issues.SearchWsResponse searchIssues(LocalConnector localConnector, SearchWsRequest request) {
    System.out.println("HTTP ----- issues ");
    WsClient wsClient = WsClientFactories.getLocal().newClient(localConnector);
    return wsClient.issues().search(request);
  }

  public static Rules.Rule searchRule(LocalConnector localConnector, String key) {
    System.out.println("HTTP ----- rule " + key);
    WsClient wsClient = WsClientFactories.getLocal().newClient(localConnector);
    return wsClient.rules().search(new org.sonarqube.ws.client.rule.SearchWsRequest().setRuleKey(key)).getRules(0);
  }

  public static WsComponents.Component searchComponent(LocalConnector localConnector, String key) {
    System.out.println("HTTP ----- component " + key);
    WsClient wsClient = WsClientFactories.getLocal().newClient(localConnector);
    ShowWsRequest req = new ShowWsRequest().setKey(key);
    return wsClient.components().show(req).getComponent();
  }

  private static RequestContext getContext(DataFetchingEnvironment env) {
    return (RequestContext) env.getContext();
  }
}
