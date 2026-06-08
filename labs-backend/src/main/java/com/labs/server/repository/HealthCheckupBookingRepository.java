package com.labs.server.repository;

import com.labs.server.entity.CheckupBookingStatus;
import com.labs.server.entity.HealthCheckupBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface HealthCheckupBookingRepository extends JpaRepository<HealthCheckupBooking, UUID> {

    List<HealthCheckupBooking> findByHospital_IdOrderByScheduledDateDescCreatedAtDesc(UUID hospitalId);

    List<HealthCheckupBooking> findByHospital_IdAndScheduledDateOrderByScheduledTimeAsc(UUID hospitalId, LocalDate date);

    List<HealthCheckupBooking> findByHospital_IdAndStatusOrderByScheduledDateDesc(UUID hospitalId, CheckupBookingStatus status);

    List<HealthCheckupBooking> findByPatient_IdOrderByScheduledDateDesc(Integer patientId);

    // Matches both legacy "HCP-YYYY-NNNN" and prefixed "NNNN-HCP-YYYY-NNNN" formats.
    @Query("SELECT b.bookingNumber FROM HealthCheckupBooking b WHERE b.hospital.id = :hospitalId AND b.bookingNumber LIKE CONCAT('%HCP-', :year, '-%')")
    List<String> findBookingNumbersForYear(@Param("hospitalId") UUID hospitalId, @Param("year") String year);

    long countByHospital_IdAndScheduledDate(UUID hospitalId, LocalDate date);

    long countByHospital_IdAndStatus(UUID hospitalId, CheckupBookingStatus status);

    @Query("SELECT COUNT(b) FROM HealthCheckupBooking b " +
           "WHERE b.hospital.id = :hospitalId " +
           "AND b.scheduledDate = :date " +
           "AND b.status <> com.labs.server.entity.CheckupBookingStatus.CANCELLED")
    long countByHospital_IdAndScheduledDateExcludingCancelled(
            @Param("hospitalId") UUID hospitalId,
            @Param("date") LocalDate date);
}
