package com.labs.server.repository;

import com.labs.server.entity.ReportDispatchJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportDispatchJobRepository extends JpaRepository<ReportDispatchJob, Long> {

    List<ReportDispatchJob> findByLabOrderIdOrderByQueuedAtDesc(Long labOrderId);

    List<ReportDispatchJob> findByReportPdfIdOrderByQueuedAtDesc(Long reportPdfId);

    /** Adapter pickup query — phase-5b workers will pull QUEUED rows for the channel they handle. */
    List<ReportDispatchJob> findTop20ByChannelAndStatusOrderByQueuedAtAsc(String channel, String status);
}
