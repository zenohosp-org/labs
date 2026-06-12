package com.labs.server.service;

import com.labs.server.entity.AdmissionStatus;
import com.labs.server.entity.Invoice;
import com.labs.server.entity.InvoiceItem;
import com.labs.server.entity.InvoiceStatus;
import com.labs.server.entity.LabOrder;
import com.labs.server.entity.LabStatus;
import com.labs.server.repository.InvoiceRepository;
import com.labs.server.repository.LabOrderRepository;
import com.labs.server.util.HospitalIdPrefix;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Auto-bill a lab order once its report has been generated. Mirrors the
 * radiology auto-bill flow on main:
 *
 * - <b>IPD</b> (order has an admission AND that admission is still ADMITTED):
 *   append a LAB line to the patient's active IPD invoice (creates it if
 *   missing), recompute totals, and re-open the invoice from SETTLED if it was
 *   already closed.
 *
 * - <b>OPD walk-in</b> (no admission, or admission no longer active):
 *   create a fresh standalone OPD invoice with just this lab line.
 *   Invoice number format: {HOSPITAL_PREFIX}LAB-{12-digit-padded order id}.
 *
 * Idempotent: if the order is already BILLED or already linked to an existing
 * invoice item, returns without doing anything. No-op when price is null or
 * zero — staff will price manually via the HMS billing flow.
 */
@Service
@RequiredArgsConstructor
public class LabBillingService {

    private final InvoiceRepository invoiceRepository;
    private final LabOrderRepository labOrderRepository;

    @Transactional
    public void billLabOrder(LabOrder order) {
        if (order == null) return;
        if (order.getPrice() == null || order.getPrice().compareTo(BigDecimal.ZERO) <= 0) return;
        if (LabStatus.BILLED.equals(order.getStatus())) return;

        // Already linked to an invoice item? Someone billed it manually first —
        // just flip the status and exit so we don't double-bill.
        if (invoiceRepository.existsItemByLabOrderId(order.getId())) {
            order.setStatus(LabStatus.BILLED);
            labOrderRepository.save(order);
            return;
        }

        boolean isIpd = order.getAdmission() != null
                && AdmissionStatus.ADMITTED.equals(order.getAdmission().getStatus());

        String description = "Lab — " + order.getServiceName()
                + (order.getReportId() != null ? " (Report " + order.getReportId() + ")" : "");

        BigDecimal lineGst = computeLineGst(order.getPrice(), order.getGstRate());

        if (isIpd) {
            UUID admissionId = order.getAdmission().getId();
            Invoice invoice = invoiceRepository.findAllByAdmission_IdOrderByCreatedAtDesc(admissionId)
                    .stream().findFirst().orElse(null);

            // No IPD invoice yet — bail out and let HMS create it on the next
            // admission/billing touch. This mirrors radiology's behaviour when
            // the radiology service can't find an existing IPD invoice and
            // delegates to HMS's createAdmissionInvoice.
            if (invoice == null) return;

            if (invoice.getItems() == null) invoice.setItems(new ArrayList<>());
            invoice.getItems().add(InvoiceItem.builder()
                    .invoice(invoice)
                    .itemType("LAB_TEST")
                    .labOrderId(order.getId())
                    .description(description)
                    .quantity(1)
                    .unitPrice(order.getPrice())
                    .totalPrice(order.getPrice())
                    .build());

            BigDecimal subtotal = invoice.getItems().stream()
                    .map(it -> it.getTotalPrice() != null ? it.getTotalPrice() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            // Aggregate tax += this line's GST. Existing invoice.tax already
            // accounts for prior lines' GST, so we only add the new contribution.
            BigDecimal tax = (invoice.getTax() != null ? invoice.getTax() : BigDecimal.ZERO).add(lineGst);
            BigDecimal discount = invoice.getDiscount() != null ? invoice.getDiscount() : BigDecimal.ZERO;
            invoice.setSubtotal(subtotal);
            invoice.setTax(tax);
            invoice.setTotal(subtotal.add(tax).subtract(discount));

            if (InvoiceStatus.PAID.equals(invoice.getStatus())
                    || InvoiceStatus.SETTLED.equals(invoice.getStatus())) {
                invoice.setStatus(InvoiceStatus.UNSETTLED);
            }
            invoice.setUpdatedAt(LocalDateTime.now());
            invoiceRepository.save(invoice);
        } else {
            String invoiceNum = HospitalIdPrefix.of(order.getHospital())
                    + "LAB-" + String.format("%012d", order.getId());
            Invoice invoice = Invoice.builder()
                    .invoiceNumber(invoiceNum)
                    .hospital(order.getHospital())
                    .patient(order.getPatient())
                    .subtotal(order.getPrice())
                    .tax(lineGst)
                    .discount(BigDecimal.ZERO)
                    .total(order.getPrice().add(lineGst))
                    .status(InvoiceStatus.UNPAID)
                    .notes("Lab walk-in — " + order.getServiceName())
                    .build();
            invoice.setItems(new ArrayList<>(List.of(InvoiceItem.builder()
                    .invoice(invoice)
                    .itemType("LAB_TEST")
                    .labOrderId(order.getId())
                    .description(description)
                    .quantity(1)
                    .unitPrice(order.getPrice())
                    .totalPrice(order.getPrice())
                    .build())));
            invoiceRepository.save(invoice);
        }

        order.setStatus(LabStatus.BILLED);
        labOrderRepository.save(order);
    }

    /**
     * price * gstRate / 100 at 2-decimal precision (HALF_UP) — matches the
     * Invoice.tax column scale. Null or non-positive inputs yield ZERO so
     * pre-catalog orders and tax-free tests bill at exactly their base price.
     */
    private static BigDecimal computeLineGst(BigDecimal price, BigDecimal rate) {
        if (price == null || rate == null) return BigDecimal.ZERO;
        if (rate.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        return price.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
