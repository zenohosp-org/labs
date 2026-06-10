package com.labs.server.repository;

import com.labs.server.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    List<Invoice> findAllByAdmission_IdOrderByCreatedAtDesc(UUID admissionId);

    @Query("""
        SELECT CASE WHEN COUNT(ii) > 0 THEN TRUE ELSE FALSE END
        FROM InvoiceItem ii
        WHERE ii.labOrderId = :labOrderId
        """)
    boolean existsItemByLabOrderId(@Param("labOrderId") Long labOrderId);

    @Query("""
        SELECT CASE WHEN COUNT(ii) > 0 THEN TRUE ELSE FALSE END
        FROM InvoiceItem ii
        WHERE ii.radiologyOrderId = :radiologyOrderId
        """)
    boolean existsItemByRadiologyOrderId(@Param("radiologyOrderId") Long radiologyOrderId);

    /**
     * Used by checkup auto-bill to skip if a booking has already been billed
     * (the InvoiceItem table doesn't carry a booking_id FK, so the booking's
     * own {@code invoice_id} field is authoritative).
     */
    boolean existsByIdAndAdmission_Id(UUID id, UUID admissionId);

    /**
     * Returns invoices that carry a given radiology line item. Used by the
     * queue/reports DTO to surface live payment status without a frontend
     * round-trip. Order is most-recent-first so a one-row pick is safe.
     */
    @Query("""
        SELECT DISTINCT inv FROM Invoice inv JOIN inv.items ii
        WHERE ii.radiologyOrderId = :radiologyOrderId
        ORDER BY inv.createdAt DESC
        """)
    List<Invoice> findByRadiologyOrderId(@Param("radiologyOrderId") Long radiologyOrderId);
}
