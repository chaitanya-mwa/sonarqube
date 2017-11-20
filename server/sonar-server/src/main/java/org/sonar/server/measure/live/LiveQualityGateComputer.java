package org.sonar.server.measure.live;

import java.util.Collection;
import org.sonar.db.DbSession;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.measure.LiveMeasureDto;

public interface LiveQualityGateComputer {

  void recalculateQualityGate(DbSession dbSession, ComponentDto project, Collection<LiveMeasureDto> modifiedMeasures);

}
