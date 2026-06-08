package com.labs.server.service;

import com.labs.server.entity.Admission;
import com.labs.server.entity.AdmissionStatus;
import com.labs.server.entity.HealthCheckupBooking;
import com.labs.server.entity.Invoice;
import com.labs.server.entity.InvoiceItem;
import com.labs.server.entity.InvoiceStatus;
import com.labs.server.repository.AdmissionRepository;
import com.labs.server.repository.InvoiceRepository;
import com.labs.server.util.HospitalIdPrefix;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Mirror of HMS {@code InvoiceService.billCheckupBooking} (HMS lines 1153–1243).
 *
 * <ul>
 *   <li><b>IPD</b> (patient currently ADMITTED): append a CHECKUP line to the
 *       admission invoice, recompute totals, re-open SETTLED if needed.</li>
 *   <li><b>OPD walk-in</b>: create standalone invoice numbered
 *       {@code {HOSPITAL_PREFIX}HCP-{12-char uuid suffix}}.</li>
 * </ul>
 *
 * Idempotent via {@code booking.invoiceId} + {@code paymentStatus}. Caller
 * (HealthCheckupService) saves the booking after this returns.
 */
@Service
@RequiredArgsConstructor
public class CheckupBillingService {

    private final InvoiceRepository invoiceRepository;
    private final AdmissionRepository admissionRepository;

    @Transactional
    public Invoice billCheckupBooking(HealthCheckupBooking booking) {
        if (booking == null) return null;
        if (booking.getInvoiceId() != null) {
            return invoiceRepository.findById(booking.getInvoiceId()).orElse(null);
        }
        if ("BILLED".equals(booking.getPaymentStatus()) || "PAID".equals(booking.getPaymentStatus())) {
            return null;
        }
        BigDecimal price = booking.getHealthPackage() != null
                ? booking.getHealthPackage().getPrice() : null;
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) return null;

        Optional<Admission> activeAdm = admissionRepository.findByPatient_IdAndStatus(
                booking.getPatient().getId(), AdmissionStatus.ADMITTED);

        String description = "Health Checkup — " + booking.getHealthPackage().getName()
                + " (" + booking.getBookingNumber() + ")";

        Invoice resultInvoice;
        if (activeAdm.isPresent()) {
            UUID admissionId = activeAdm.get().getId();
            Invoice invoice = invoiceRepository.findAllByAdmission_IdOrderByCreatedAtDesc(admissionId)
                    .stream().findFirst().orElse(null);

            // No IPD invoice yet — defer until HMS creates one via the admission flow.
            if (invoice == null) return null;

            if (invoice.getItems() == null) invoice.setItems(new ArrayList<>());
            invoice.getItems().add(InvoiceItem.builder()
                    .invoice(invoice)
                    .itemType("CHECKUP")
                    .description(description)
                    .quantity(1)
                    .unitPrice(price)
                    .totalPrice(price)
                    .build());

            BigDecimal subtotal = invoice.getItems().stream()
                    .map(it -> it.getTotalPrice() != null ? it.getTotalPrice() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal tax = invoice.getTax() != null ? invoice.getTax() : BigDecimal.ZERO;
            BigDecimal discount = invoice.getDiscount() != null ? invoice.getDiscount() : BigDecimal.ZERO;
            invoice.setSubtotal(subtotal);
            invoice.setTotal(subtotal.add(tax).subtract(discount));

            if (InvoiceStatus.PAID.equals(invoice.getStatus())
                    || InvoiceStatus.SETTLED.equals(invoice.getStatus())) {
                invoice.setStatus(InvoiceStatus.UNSETTLED);
            }
            invoice.setUpdatedAt(LocalDateTime.now());
            resultInvoice = invoiceRepository.save(invoice);
        } else {
            String invoiceNum = HospitalIdPrefix.of(booking.getHospital())
                    + "HCP-" + booking.getId().toString().replace("-", "").substring(0, 12).toUpperCase();
            Invoice invoice = Invoice.builder()
                    .invoiceNumber(invoiceNum)
                    .hospital(booking.getHospital())
                    .patient(booking.getPatient())
                    .subtotal(price)
                    .tax(BigDecimal.ZERO)
                    .discount(BigDecimal.ZERO)
                    .total(price)
                    .status(InvoiceStatus.UNPAID)
                    .notes("Health Checkup — " + booking.getHealthPackage().getName())
                    .build();
            invoice.setItems(new ArrayList<>(List.of(InvoiceItem.builder()
                    .invoice(invoice)
                    .itemType("CHECKUP")
                    .description(description)
                    .quantity(1)
                    .unitPrice(price)
                    .totalPrice(price)
                    .build())));
            resultInvoice = invoiceRepository.save(invoice);
        }

        booking.setInvoiceId(resultInvoice.getId());
        booking.setPaymentStatus("BILLED");
        return resultInvoice;
    }
}
