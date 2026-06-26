package com.labs.server.service;

import com.labs.server.entity.CheckupBookingStatus;
import com.labs.server.entity.Doctor;
import com.labs.server.entity.HealthCheckupBooking;
import com.labs.server.entity.HealthCheckupResult;
import com.labs.server.entity.HealthPackage;
import com.labs.server.entity.HealthPackageTest;
import com.labs.server.entity.Hospital;
import com.labs.server.entity.PackageCategory;
import com.labs.server.entity.Patient;
import com.labs.server.repository.DoctorRepository;
import com.labs.server.repository.HealthCheckupBookingRepository;
import com.labs.server.repository.HealthPackageRepository;
import com.labs.server.repository.HospitalRepository;
import com.labs.server.repository.PatientRepository;
import com.labs.server.util.HospitalIdPrefix;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mirror of HMS {@code HealthCheckupService} — state machine, booking-number
 * generation, auto-billing on COMPLETED, and the inner request DTOs.
 */
@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class HealthCheckupService {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckupService.class);

    private final HealthPackageRepository packageRepo;
    private final HealthCheckupBookingRepository bookingRepo;
    private final HospitalRepository hospitalRepo;
    private final PatientRepository patientRepo;
    private final DoctorRepository doctorRepo;

    @Lazy
    private final CheckupBillingService billingService;

    // State machine: terminal states (COMPLETED, CANCELLED, NO_SHOW) cannot
    // exit except via reschedule (CANCELLED/NO_SHOW → SCHEDULED).
    private static final Map<CheckupBookingStatus, Set<CheckupBookingStatus>> ALLOWED_TRANSITIONS = Map.of(
            CheckupBookingStatus.SCHEDULED, EnumSet.of(
                    CheckupBookingStatus.CHECKED_IN,
                    CheckupBookingStatus.IN_PROGRESS,
                    CheckupBookingStatus.COMPLETED,
                    CheckupBookingStatus.CANCELLED,
                    CheckupBookingStatus.NO_SHOW),
            CheckupBookingStatus.CHECKED_IN, EnumSet.of(
                    CheckupBookingStatus.IN_PROGRESS,
                    CheckupBookingStatus.COMPLETED,
                    CheckupBookingStatus.CANCELLED,
                    CheckupBookingStatus.NO_SHOW),
            CheckupBookingStatus.IN_PROGRESS, EnumSet.of(
                    CheckupBookingStatus.COMPLETED,
                    CheckupBookingStatus.CANCELLED),
            CheckupBookingStatus.COMPLETED, EnumSet.noneOf(CheckupBookingStatus.class),
            CheckupBookingStatus.CANCELLED, EnumSet.of(CheckupBookingStatus.SCHEDULED),
            CheckupBookingStatus.NO_SHOW, EnumSet.of(CheckupBookingStatus.SCHEDULED));

    private static final Set<CheckupBookingStatus> EDITABLE_RESULT_STATES = EnumSet.of(
            CheckupBookingStatus.SCHEDULED,
            CheckupBookingStatus.CHECKED_IN,
            CheckupBookingStatus.IN_PROGRESS);

    private static void assertSameHospital(UUID expected, UUID actual, String label) {
        if (expected == null || actual == null || !expected.equals(actual)) {
            throw new RuntimeException(label + " does not belong to this hospital");
        }
    }

    private HealthCheckupBooking loadBookingForTenant(UUID bookingId, UUID hospitalId) {
        HealthCheckupBooking booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
        assertSameHospital(hospitalId, booking.getHospital().getId(), "Booking");
        return booking;
    }

    // ── Package management ────────────────────────────────────────────────

    public List<HealthPackage> getPackages(UUID hospitalId, boolean activeOnly) {
        return activeOnly
                ? packageRepo.findActiveByHospitalId(hospitalId)
                : packageRepo.findByHospitalId(hospitalId);
    }

    @Transactional
    public HealthPackage savePackage(UUID hospitalId, PackageRequest req) {
        Hospital hospital = hospitalRepo.getReferenceById(hospitalId);
        HealthPackage pkg = req.getId() != null
                ? packageRepo.findById(req.getId()).orElse(new HealthPackage())
                : new HealthPackage();
        if (req.getId() != null && pkg.getHospital() != null) {
            assertSameHospital(hospitalId, pkg.getHospital().getId(), "Package");
        }

        pkg.setHospital(hospital);
        pkg.setName(req.getName());
        pkg.setDescription(req.getDescription());
        pkg.setCategory(PackageCategory.valueOf(req.getCategory()));
        pkg.setTargetGender(req.getTargetGender() != null ? req.getTargetGender() : "ANY");
        pkg.setPrice(req.getPrice());
        pkg.setValidityDays(req.getValidityDays() != null ? req.getValidityDays() : 1);
        pkg.setActive(req.isActive());

        pkg.getTests().clear();
        if (req.getTests() != null) {
            for (int i = 0; i < req.getTests().size(); i++) {
                TestRequest t = req.getTests().get(i);
                HealthPackageTest test = HealthPackageTest.builder()
                        .healthPackage(pkg)
                        .testName(t.getTestName())
                        .testCategory(t.getTestCategory() != null ? t.getTestCategory() : "GENERAL")
                        .normalRange(t.getNormalRange())
                        .displayOrder(i)
                        .mandatory(t.isMandatory())
                        .labServiceId(t.getLabServiceId())   // Phase 3 — FK to lab_services
                        .build();
                pkg.getTests().add(test);
            }
        }

        return packageRepo.save(pkg);
    }

    @Transactional
    public void togglePackage(UUID hospitalId, UUID packageId) {
        packageRepo.findById(packageId).ifPresent(p -> {
            assertSameHospital(hospitalId, p.getHospital().getId(), "Package");
            p.setActive(!p.isActive());
            packageRepo.save(p);
        });
    }

    @Transactional
    public void deletePackage(UUID hospitalId, UUID packageId) {
        packageRepo.findById(packageId).ifPresent(p -> {
            assertSameHospital(hospitalId, p.getHospital().getId(), "Package");
            packageRepo.delete(p);
        });
    }

    // ── Bookings ──────────────────────────────────────────────────────────

    public List<HealthCheckupBooking> getBookings(UUID hospitalId, String status, String date) {
        if (status != null && !status.isBlank()) {
            return bookingRepo.findByHospital_IdAndStatusOrderByScheduledDateDesc(
                    hospitalId, CheckupBookingStatus.valueOf(status));
        }
        if (date != null && !date.isBlank()) {
            return bookingRepo.findByHospital_IdAndScheduledDateOrderByScheduledTimeAsc(
                    hospitalId, LocalDate.parse(date));
        }
        return bookingRepo.findByHospital_IdOrderByScheduledDateDescCreatedAtDesc(hospitalId);
    }

    public HealthCheckupBooking getBooking(UUID bookingId, UUID hospitalId) {
        return loadBookingForTenant(bookingId, hospitalId);
    }

    @Transactional
    public HealthCheckupBooking createBooking(UUID hospitalId, BookingRequest req, String performedBy) {
        Hospital hospital = hospitalRepo.findById(hospitalId)
                .orElseThrow(() -> new IllegalArgumentException("Hospital not found"));
        Patient patient = patientRepo.findById(req.getPatientId())
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));
        assertSameHospital(hospitalId, patient.getHospital().getId(), "Patient");

        HealthPackage pkg = packageRepo.findById(req.getPackageId())
                .orElseThrow(() -> new IllegalArgumentException("Package not found"));
        assertSameHospital(hospitalId, pkg.getHospital().getId(), "Package");
        if (!pkg.isActive()) {
            throw new RuntimeException("Selected package is inactive — pick another or re-enable it");
        }

        Doctor doctor = null;
        if (req.getDoctorId() != null) {
            doctor = doctorRepo.findById(req.getDoctorId()).orElse(null);
            if (doctor != null) {
                assertSameHospital(hospitalId, doctor.getHospitalId(), "Doctor");
            }
        }

        String bookingNumber = generateBookingNumber(hospital);

        HealthCheckupBooking booking = HealthCheckupBooking.builder()
                .hospital(hospital)
                .patient(patient)
                .healthPackage(pkg)
                .assignedDoctor(doctor)
                .bookingNumber(bookingNumber)
                .scheduledDate(LocalDate.parse(req.getScheduledDate()))
                .scheduledTime(req.getScheduledTime() != null ? LocalTime.parse(req.getScheduledTime()) : null)
                .status(CheckupBookingStatus.SCHEDULED)
                .paymentStatus(req.getPaymentStatus() != null ? req.getPaymentStatus() : "PENDING")
                .amountPaid(req.getAmountPaid() != null ? req.getAmountPaid() : BigDecimal.ZERO)
                .notes(req.getNotes())
                .createdBy(performedBy)
                .build();

        for (HealthPackageTest t : pkg.getTests()) {
            booking.getResults().add(HealthCheckupResult.builder()
                    .booking(booking)
                    .testName(t.getTestName())
                    .testCategory(t.getTestCategory())
                    .normalRange(t.getNormalRange())
                    .displayOrder(t.getDisplayOrder())
                    .mandatory(t.isMandatory())
                    .resultStatus("PENDING")
                    .build());
        }

        return bookingRepo.save(booking);
    }

    @Transactional
    public HealthCheckupBooking updateStatus(UUID bookingId, UUID hospitalId, String statusStr) {
        HealthCheckupBooking booking = loadBookingForTenant(bookingId, hospitalId);

        CheckupBookingStatus next;
        try {
            next = CheckupBookingStatus.valueOf(statusStr);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Unknown status: " + statusStr);
        }
        CheckupBookingStatus current = booking.getStatus();
        if (current != next) {
            Set<CheckupBookingStatus> allowed = ALLOWED_TRANSITIONS.get(current);
            if (allowed == null || !allowed.contains(next)) {
                throw new RuntimeException("Invalid status transition: " + current + " → " + next);
            }
        }
        booking.setStatus(next);

        HealthCheckupBooking saved = bookingRepo.save(booking);

        if (next == CheckupBookingStatus.COMPLETED && saved.getInvoiceId() == null) {
            try {
                billingService.billCheckupBooking(saved);
                bookingRepo.save(saved);
            } catch (Exception e) {
                log.warn("Auto-bill failed for checkup booking {}: {}", saved.getId(), e.getMessage());
            }
        }
        return saved;
    }

    @Transactional
    public HealthCheckupBooking updateResult(UUID bookingId, UUID hospitalId, Long resultId, ResultUpdateRequest req) {
        HealthCheckupBooking booking = loadBookingForTenant(bookingId, hospitalId);
        if (!EDITABLE_RESULT_STATES.contains(booking.getStatus())) {
            throw new RuntimeException("Cannot edit results — booking is " + booking.getStatus());
        }

        booking.getResults().stream()
                .filter(r -> r.getId().equals(resultId))
                .findFirst()
                .ifPresentOrElse(r -> {
                    r.setResultValue(req.getResultValue());
                    r.setResultStatus(req.getResultStatus() != null ? req.getResultStatus() : "COMPLETED");
                    r.setResultNotes(req.getResultNotes());
                    if ("COMPLETED".equals(r.getResultStatus())) r.setCompletedAt(LocalDateTime.now());
                }, () -> {
                    throw new RuntimeException("Result not found on this booking");
                });

        if (booking.getStatus() == CheckupBookingStatus.CHECKED_IN
                || booking.getStatus() == CheckupBookingStatus.SCHEDULED) {
            booking.setStatus(CheckupBookingStatus.IN_PROGRESS);
        }

        return bookingRepo.save(booking);
    }

    @Transactional
    public HealthCheckupBooking assignDoctor(UUID bookingId, UUID hospitalId, UUID doctorId) {
        HealthCheckupBooking booking = loadBookingForTenant(bookingId, hospitalId);
        Doctor doctor = null;
        if (doctorId != null) {
            doctor = doctorRepo.findById(doctorId)
                    .orElseThrow(() -> new IllegalArgumentException("Doctor not found"));
            assertSameHospital(hospitalId, doctor.getHospitalId(), "Doctor");
        }
        booking.setAssignedDoctor(doctor);
        return bookingRepo.save(booking);
    }

    @Transactional
    public HealthCheckupBooking saveDoctorNotes(UUID bookingId, UUID hospitalId, DoctorNotesRequest req) {
        HealthCheckupBooking booking = loadBookingForTenant(bookingId, hospitalId);
        booking.setDoctorNotes(req.getDoctorNotes());
        booking.setRecommendation(req.getRecommendation());
        return bookingRepo.save(booking);
    }

    public Map<String, Long> getStats(UUID hospitalId) {
        long today = bookingRepo.countByHospital_IdAndScheduledDateExcludingCancelled(hospitalId, LocalDate.now());
        long scheduled  = bookingRepo.countByHospital_IdAndStatus(hospitalId, CheckupBookingStatus.SCHEDULED);
        long inProgress = bookingRepo.countByHospital_IdAndStatus(hospitalId, CheckupBookingStatus.IN_PROGRESS);
        long completed  = bookingRepo.countByHospital_IdAndStatus(hospitalId, CheckupBookingStatus.COMPLETED);
        long cancelled  = bookingRepo.countByHospital_IdAndStatus(hospitalId, CheckupBookingStatus.CANCELLED);
        return Map.of(
                "today", today,
                "scheduled", scheduled,
                "inProgress", inProgress,
                "completed", completed,
                "cancelled", cancelled);
    }

    private String generateBookingNumber(Hospital hospital) {
        String year = String.valueOf(LocalDate.now().getYear());
        String hospPrefix = HospitalIdPrefix.of(hospital);
        String coreFormat = "HCP-" + year + "-";
        List<String> existing = bookingRepo.findBookingNumbersForYear(hospital.getId(), year);
        int maxSeq = existing.stream().mapToInt(this::extractTrailingSequence).max().orElse(0);
        return hospPrefix + coreFormat + String.format("%04d", maxSeq + 1);
    }

    private int extractTrailingSequence(String id) {
        if (id == null) return 0;
        try {
            int dash = id.lastIndexOf('-');
            if (dash < 0 || dash == id.length() - 1) return 0;
            return Integer.parseInt(id.substring(dash + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ── Request DTOs (kept as inner classes so HealthCheckupController can
    // import via HealthCheckupService.* exactly like HMS does) ────────────

    @lombok.Data
    public static class PackageRequest {
        private UUID id;
        private String name;
        private String description;
        private String category;
        private String targetGender;
        private BigDecimal price;
        private Integer validityDays;
        private boolean active = true;
        private List<TestRequest> tests;
    }

    @lombok.Data
    public static class TestRequest {
        private String testName;
        private String testCategory;
        private String normalRange;
        private boolean mandatory = true;
        /** Phase 3 — FK to lab_services.id when picked from the catalogue. */
        private Long labServiceId;
    }

    @lombok.Data
    public static class BookingRequest {
        private Integer patientId;
        private UUID packageId;
        private UUID doctorId;
        private String scheduledDate;
        private String scheduledTime;
        private String paymentStatus;
        private BigDecimal amountPaid;
        private String notes;
    }

    @lombok.Data
    public static class ResultUpdateRequest {
        private String resultValue;
        private String resultStatus;
        private String resultNotes;
    }

    @lombok.Data
    public static class DoctorNotesRequest {
        private String doctorNotes;
        private String recommendation;
    }
}
