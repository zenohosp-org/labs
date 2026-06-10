/**
 * Maps an order's invoice fields onto a chip the queue/reports can render.
 *
 * The decision tree mirrors how OTM/HMS describe IPD vs OPD:
 *   - No invoice yet           → "Not Billed"
 *   - Invoice + admissionId    → IPD: pending until the discharge bill is settled
 *   - Invoice + no admissionId → OPD: pending until collectPayment() flips PAID
 *
 * The "fullySettled" check looks at both PAID and SETTLED (HMS uses SETTLED
 * for closed IPD bills and PAID for closed OPD bills — same business state).
 */
const PAID_STATES = new Set(["PAID", "SETTLED"]);
const PARTIAL_STATES = new Set(["PARTIAL", "UNSETTLED"]);

export function paymentChipFor(order) {
    const status = order?.invoiceStatus;
    const isIpd = !!order?.admissionId;

    if (!status) {
        return { label: "Not Billed", cls: "is-slate", tone: "neutral" };
    }
    if (PAID_STATES.has(status)) {
        return { label: "Paid", cls: "is-emerald", tone: "success" };
    }
    if (status === "CANCELLED") {
        return { label: "Cancelled", cls: "is-rose", tone: "danger" };
    }
    if (isIpd) {
        // IPD invoices stay UNSETTLED / PARTIAL until discharge consolidation.
        return { label: "IPD — Pending", cls: "is-amber", tone: "warning" };
    }
    if (PARTIAL_STATES.has(status)) {
        return { label: "Partial", cls: "is-amber", tone: "warning" };
    }
    // OPD UNPAID — the most common pre-payment state for walk-ins.
    return { label: "Awaiting Payment", cls: "is-amber", tone: "warning" };
}

export function formatPaymentSummary(order) {
    if (!order?.invoiceStatus) return null;
    const paid = order.invoicePaid != null ? Number(order.invoicePaid) : null;
    const total = order.invoiceTotal != null ? Number(order.invoiceTotal) : null;
    if (paid != null && total != null && total > 0) {
        return `₹${paid.toLocaleString("en-IN")} / ₹${total.toLocaleString("en-IN")}`;
    }
    if (total != null) return `₹${total.toLocaleString("en-IN")}`;
    return null;
}
