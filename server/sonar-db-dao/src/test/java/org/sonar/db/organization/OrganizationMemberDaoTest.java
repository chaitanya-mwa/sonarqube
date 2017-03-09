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

package org.sonar.db.organization;

import java.util.Map;
import org.apache.ibatis.exceptions.PersistenceException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

public class OrganizationMemberDaoTest {
  @Rule
  public final DbTester db = DbTester.create().setDisableDefaultOrganization(true);
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private DbClient dbClient = db.getDbClient();
  private DbSession dbSession = db.getSession();

  private OrganizationMemberDao underTest = dbClient.organizationMemberDao();

  @Test
  public void insert() {
    underTest.insert(dbSession, create("O_1", 256));

    Map<String, Object> result = db.selectFirst(dbSession, "select organization_uuid as \"organizationUuid\", user_id as \"userId\" from organization_members");

    assertThat(result).containsOnly(entry("organizationUuid", "O_1"), entry("userId", 256L));
  }

  @Test
  public void fail_insert_if_no_organization_uuid() {
    expectedException.expect(PersistenceException.class);

    underTest.insert(dbSession, create(null, 256));
  }

  @Test
  public void fail_insert_if_no_user_id() {
    expectedException.expect(PersistenceException.class);

    underTest.insert(dbSession, create("O_1", null));
  }

  @Test
  public void fail_if_organization_member_already_exist() {
    underTest.insert(dbSession, create("O_1", 256));
    expectedException.expect(PersistenceException.class);

    underTest.insert(dbSession, create("O_1", 256));
  }

  private OrganizationMemberDto create(String organizationUuid, Integer userId) {
    return new OrganizationMemberDto()
      .setOrganizationUuid(organizationUuid)
      .setUserId(userId);
  }
}