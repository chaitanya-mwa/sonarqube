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
package org.sonar.db.measure;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.apache.ibatis.session.ResultHandler;
import org.sonar.api.utils.System2;
import org.sonar.core.util.Uuids;
import org.sonar.db.Dao;
import org.sonar.db.DbSession;
import org.sonar.db.component.ComponentDto;

import static org.sonar.db.DatabaseUtils.executeLargeInputs;

public class LiveMeasureDao implements Dao {

  private final System2 system2;

  public LiveMeasureDao(System2 system2) {
    this.system2 = system2;
  }

  public List<LiveMeasureDto> selectByComponentUuids(DbSession dbSession, Collection<String> largeComponentUuids, Collection<Integer> metricIds) {
    if (largeComponentUuids.isEmpty() || metricIds.isEmpty()) {
      return Collections.emptyList();
    }

    return executeLargeInputs(
      largeComponentUuids,
      componentUuids -> mapper(dbSession).selectByComponentUuids(componentUuids, metricIds));
  }

  public void selectTreeByQuery(DbSession dbSession, ComponentDto baseComponent, MeasureTreeQuery query, ResultHandler<LiveMeasureDto> resultHandler) {
    if (query.returnsEmpty()) {
      return;
    }
    mapper(dbSession).selectTreeByQuery(query, baseComponent.uuid(), query.getUuidPath(baseComponent), resultHandler);
  }

  public void insert(DbSession dbSession, LiveMeasureDto dto) {
    if (dto.getUuid() != null) {
      throw new IllegalArgumentException("Inserting a LiveMeasureDto that has already a uuid");
    }
    dto.setUuid(Uuids.create());
    mapper(dbSession).insert(dto, system2.now());
  }

  public boolean update(DbSession dbSession, LiveMeasureDto dto) {
    return mapper(dbSession).update(dto, system2.now()) == 1;
  }

  public void insertOrUpdate(DbSession dbSession, LiveMeasureDto dto) {
    if (!update(dbSession, dto)) {
      insert(dbSession, dto);
    }
  }

  public void deleteByProjectUuid(DbSession dbSession, String projectUuid) {
    mapper(dbSession).deleteByProjectUuid(projectUuid);
  }

  private static LiveMeasureMapper mapper(DbSession dbSession) {
    return dbSession.getMapper(LiveMeasureMapper.class);
  }
}
