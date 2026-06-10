import { IndianRupee } from "lucide-react";
import { paymentChipFor, formatPaymentSummary } from "@/utils/paymentBadge";

const COLLECTABLE_STATES = new Set(["UNPAID", "PARTIAL", "UNSETTLED"]);

/**
 * Renders the payment-status chip + amount summary for a radiology order.
 * When the order is OPD (no admissionId) and the invoice is in a collectable
 * state, also surfaces a "Collect" button that opens the payment modal.
 *
 * IPD invoices intentionally do NOT show the Collect button — those settle
 * via the HMS discharge consolidation flow, not at the labs counter.
 */
export default function PaymentCell({ order, onCollect }) {
    const chip = paymentChipFor(order);
    const summary = formatPaymentSummary(order);

    const canCollect =
        !order?.admissionId &&
        order?.invoiceId &&
        COLLECTABLE_STATES.has(order?.invoiceStatus) &&
        typeof onCollect === "function";

    return (
        <div className="flex flex-col gap-0.5">
            <div className="flex items-center gap-2">
                <span className={`hms-rad-chip ${chip.cls}`}>{chip.label}</span>
                {canCollect && (
                    <button
                        type="button"
                        onClick={(e) => {
                            e.stopPropagation();
                            onCollect(order);
                        }}
                        className="hms-rad-row__view-btn is-collect"
                        title="Collect payment at the labs counter"
                    >
                        <IndianRupee className="w-3 h-3" /> Collect
                    </button>
                )}
            </div>
            {summary && <span className="text-12 text-gray-500">{summary}</span>}
        </div>
    );
}
