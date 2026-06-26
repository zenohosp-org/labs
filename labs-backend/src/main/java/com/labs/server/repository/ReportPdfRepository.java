package com.labs.server.repository;

import com.labs.server.entity.ReportPdf;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportPdfRepository extends JpaRepository<ReportPdf, Long> {

    List<ReportPdf> findByLabOrderIdOrderByVersionDesc(Long labOrderId);

    Optional<ReportPdf> findFirstByLabOrderIdOrderByVersionDesc(Long labOrderId);

    /** Live (non-revoked) lookup for the public verify portal. */
    Optional<ReportPdf> findByVerifyTokenAndRevokedFalse(String verifyToken);

    /** Any token match (revoked or not) — public verify endpoint uses this to surface "revoked" status explicitly. */
    Optional<ReportPdf> findByVerifyToken(String verifyToken);

    long countByLabOrderId(Long labOrderId);
}
