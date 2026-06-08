package com.labs.server.service;

import com.labs.server.entity.AdmissionStatus;
import com.labs.server.entity.Invoice;
import com.labs.server.entity.InvoiceItem;
import com.labs.server.entity.InvoiceStatus;
import com.labs.server.entity.RadiologyOrder;
import com.labs.server.entity.RadiologyStatus;
import com.labs.server.repository.InvoiceRepository;
import com.labs.server.repository.RadiologyOrderRepository;
import com.labs.server.util.HospitalIdPrefix;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mirror of HMS {@code InvoiceService.billRadiologyOrder} (HMS lines 1043–1132).
 *
 * <ul>
 *   <li><b>IPD</b> (admission present + status ADMITTED): append a RADIOLOGY
 *       line to the patient's active admission invoice, recompute totals, and
 *       re-open the invoice from SETTLED if it had already been closed.</li>
 *   <li><b>OPD walk-in</b>: create a standalone invoice
 *       numbered {@code {HOSPITAL_PREFIX}RAD-{12-padded order id}}.</li>
 * </ul>
 *
 * Idempotent: returns early if the order is already BILLED or already linked
 * to an invoice item. No-op when price is null or zero — staff will price
 * manually via the HMS billing UI.
 */
@Service
@RequiredArgsConstructor
public class RadiologyBillingService {

    private final InvoiceRepository invoiceRepository;
    private final RadiologyOrderRepository radiologyOrderRepository;

    @Transactional
    public void billRadiologyOrder(RadiologyOrder order) {
        if (order == null) return;
        if (order.getPrice() == null || order.getPrice().compareTo(BigDecimal.ZERO) <= 0) return;
        if (RadiologyStatus.BILLED.equals(order.getStatus())) return;

        // Already linked to an invoice item? Someone billed manually first.
        if (invoiceRepository.existsItemByRadiologyOrderId(order.getId())) {
            order.setStatus(RadiologyStatus.BILLED);
            radiologyOrderRepository.save(order);
            return;
        }

        boolean isIpd = order.getAdmission() != null
                && AdmissionStatus.ADMITTED.equals(order.getAdmission().getStatus());

        String description = "Radiology — " + order.getServiceName()
                + (order.getReportId() != null ? " (Report " + order.getReportId() + ")" : "");

        if (isIpd) {
            UUID admissionId = order.getAdmission().getId();
            Invoice invoice = invoiceRepository.findAllByAdmission_IdOrderByCreatedAtDesc(admissionId)
                    .stream().findFirst().orElse(null);

            // No IPD invoice yet — defer until HMS creates it via the admission
            // flow. Same behaviour as labs lab-order billing.
            if (invoice == null) return;

            if (invoice.getItems() == null) invoice.setItems(new ArrayList<>());
            invoice.getItems().add(InvoiceItem.builder()
                    .invoice(invoice)
                    .itemType("RADIOLOGY")
                    .radiologyOrderId(order.getId())
                    .description(description)
                    .quantity(1)
                    .unitPrice(order.getPrice())
                    .totalPrice(order.getPrice())
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
            invoiceRepository.save(invoice);
        } else {
            String invoiceNum = HospitalIdPrefix.of(order.getHospital())
                    + "RAD-" + String.format("%012d", order.getId());
            Invoice invoice = Invoice.builder()
                    .invoiceNumber(invoiceNum)
                    .hospital(order.getHospital())
                    .patient(order.getPatient())
                    .subtotal(order.getPrice())
                    .tax(BigDecimal.ZERO)
                    .discount(BigDecimal.ZERO)
                    .total(order.getPrice())
                    .status(InvoiceStatus.UNPAID)
                    .notes("Radiology walk-in — " + order.getServiceName())
                    .build();
            invoice.setItems(new ArrayList<>(List.of(InvoiceItem.builder()
                    .invoice(invoice)
                    .itemType("RADIOLOGY")
                    .radiologyOrderId(order.getId())
                    .description(description)
                    .quantity(1)
                    .unitPrice(order.getPrice())
                    .totalPrice(order.getPrice())
                    .build())));
            invoiceRepository.save(invoice);
        }

        order.setStatus(RadiologyStatus.BILLED);
        radiologyOrderRepository.save(order);
    }
}
