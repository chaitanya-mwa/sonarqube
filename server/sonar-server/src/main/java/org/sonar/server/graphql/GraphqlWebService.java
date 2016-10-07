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

import com.google.gson.Gson;
import graphql.GraphQL;
import java.io.Reader;
import org.apache.commons.io.IOUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.RequestHandler;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonarqube.ws.MediaTypes;

public class GraphqlWebService implements WebService, RequestHandler {

  private final GraphQL schema = Schema.create();

  @Override
  public void define(Context context) {
    NewController controller = context.createController("graphql");
    NewAction action = controller
      .createAction("do");
    action.setPost(true);
    action.setHandler(this);
    controller.done();
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    response.setHeader("Access-Control-Allow-Origin", "*");
    Reader json = request.getBody();
    JSONObject jsonObj = null;
    try {
      jsonObj = (JSONObject) new JSONParser().parse(json);
    } catch (ParseException e) {
      e.printStackTrace();
    }
    RequestContext requestContext = new RequestContext(request.localConnector());
    Object data = schema.execute((String) jsonObj.get("query"), requestContext).getData();
    String dataJson = new Gson().toJson(data);
    String output = "{\"data\":" + dataJson + "}";
    IOUtils.write(output, response.stream().output());
    response.stream().setMediaType(MediaTypes.JSON);
  }
}
