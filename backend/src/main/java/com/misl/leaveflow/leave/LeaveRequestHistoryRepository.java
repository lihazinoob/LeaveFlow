package com.misl.leaveflow.leave;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveRequestHistoryRepository extends JpaRepository<LeaveRequestHistory, Long> {

    @EntityGraph(attributePaths = "changedBy")
    List<LeaveRequestHistory> findByLeaveRequestIdOrderByChangedAtAscIdAsc(Long leaveRequestId);
}
