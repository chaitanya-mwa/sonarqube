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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.sonar.api.server.ws.LocalConnector;

public class RequestContext {

  private final Map<String, Object> cache = new HashMap();
  private final LocalConnector localConnector;

  public RequestContext(LocalConnector localConnector) {
    this.localConnector = localConnector;
  }

  public LocalConnector getLocalConnector() {
    return localConnector;
  }

  public <T> T getCached(String key, Function<String, T> loader) {
    T result = (T)cache.get(key);
    if (result == null) {
      result = loader.apply(key);
      cache.put(key, result);
    }
    return result;
  }
}
