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
}
