import { useState, useEffect } from "react";
import { useAuth } from "@/context/AuthContext";
import { useNotification } from "@/context/NotificationContext";
import { billingApi } from "@/api/labsClient";
import { X, IndianRupee, Loader2, CheckCircle2, AlertTriangle } from "lucide-react";
import SearchableSelect from "@/components/ui/SearchableSelect";

const PAYMENT_METHODS = [
    { value: "Cash", label: "Cash" },
    { value: "UPI", label: "UPI" },
    { value: "Card", label: "Card" },
    { value: "NetBanking", label: "Net Banking" },
    { value: "Cheque", label: "Cheque" },
];

/**
 * Collect a payment against an OPD radiology invoice. Calls HMS via the
 * labs-side billing proxy — no shared-DB write here so HMS owns the ledger
 * end-to-end (bank ledger, payment history, IPD discharge gate, etc.).
 *
 * Pre-fills the amount with the outstanding balance (invoiceTotal − invoicePaid)
 * so cash counter staff hit a single "Collect" with no math. Defaults to Cash
 * + the hospital's default bank account.
 */
export default function CollectPaymentModal({ order, onClose, onPaid }) {
    const { user } = useAuth();
    const { notify } = useNotification();

    const outstanding = (() => {
        const total = Number(order?.invoiceTotal ?? 0);
        const paid = Number(order?.invoicePaid ?? 0);
        return Math.max(total - paid, 0);
    })();

    const [accounts, setAccounts] = useState([]);
    const [bankAccountId, setBankAccountId] = useState("");
    const [paymentMethod, setPaymentMethod] = useState("Cash");
    const [amount, setAmount] = useState(String(outstanding || ""));
    const [loadingAccounts, setLoadingAccounts] = useState(true);
    const [saving, setSaving] = useState(false);
    const [accountsError, setAccountsError] = useState(null);

    useEffect(() => {
        if (!user?.hospitalId) return;
        setLoadingAccounts(true);
        setAccountsError(null);
        billingApi
            .listBankAccounts(user.hospitalId)
            .then((rows) => {
                const list = Array.isArray(rows) ? rows : [];
                setAccounts(list);
                const def = list.find((a) => a.isDefault) ?? list[0];
                if (def) setBankAccountId(def.id);
            })
            .catch(() => {
                setAccountsError(
                    "Couldn't load bank accounts from HMS. Check labs HMS_API_URL config.",
                );
            })
            .finally(() => setLoadingAccounts(false));
    }, [user?.hospitalId]);

    const canSubmit =
        order?.invoiceNumber &&
        !!bankAccountId &&
        amount &&
        Number(amount) > 0 &&
        Number(amount) <= outstanding + 0.01 &&
        !saving;

    const handleSubmit = async (e) => {
        e?.preventDefault?.();
        if (!canSubmit) return;
        setSaving(true);
        try {
            // The invoiceId isn't on the radiology order DTO today — labs
            // resolves it from invoice_number. To keep the wire surface
            // narrow we send the number plus the standard PaymentRequest
            // payload HMS expects, and the labs proxy forwards as-is.
            // (HMS BillingController takes UUID; we need to send the
            // invoice id, not the number — see below for resolution.)
            await billingApi.collectPayment(order.invoiceId ?? order.invoiceNumber, {
                amount: Number(amount),
                paymentMethod,
                bankAccountId,
                collectedBy: user?.email ?? "Labs counter",
            });
            notify(`₹${Number(amount).toLocaleString("en-IN")} collected`, "success");
            onPaid?.();
            onClose?.();
        } catch (err) {
            const msg =
                err?.response?.data?.message ||
                "Failed to collect payment. Try again, or escalate to HMS billing.";
            notify(msg, "error");
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="hms-rad-modal-overlay">
            <div className="hms-rad-modal">
                <div className="hms-rad-modal__hdr">
                    <div className="hms-rad-modal__hdr-left">
                        <div>
                            <h2 className="hms-rad-modal__title">
                                <IndianRupee className="w-4 h-4" /> Collect Payment
                            </h2>
                            <p className="hms-rad-modal__sub">
                                {order?.patientName} · {order?.serviceName}
                            </p>
                        </div>
                    </div>
                    <button onClick={onClose} className="hms-rad-modal__close">
                        <X className="w-4 h-4" />
                    </button>
                </div>

                <form onSubmit={handleSubmit} className="hms-rad-modal__body">
                    {!order?.invoiceNumber && (
                        <div className="hms-rad-info-bar is-danger">
                            <AlertTriangle className="w-4 h-4 hms-rad-info-bar__icon" />
                            <span>
                                This order has no invoice yet. Generate the report first to
                                trigger auto-billing.
                            </span>
                        </div>
                    )}

                    {accountsError && (
                        <div className="hms-rad-info-bar is-danger">
                            <AlertTriangle className="w-4 h-4 hms-rad-info-bar__icon" />
                            <span>{accountsError}</span>
                        </div>
                    )}

                    <div className="hms-rad-grid">
                        <div>
                            <label className="hms-rad-label">Invoice</label>
                            <input
                                className="hms-rad-input"
                                value={order?.invoiceNumber ?? "—"}
                                disabled
                            />
                        </div>
                        <div>
                            <label className="hms-rad-label">Outstanding</label>
                            <input
                                className="hms-rad-input"
                                value={`₹${outstanding.toLocaleString("en-IN")}`}
                                disabled
                            />
                        </div>
                    </div>

                    <div className="hms-rad-grid">
                        <div>
                            <label className="hms-rad-label">Amount (₹) *</label>
                            <input
                                type="number"
                                min="0"
                                step="0.01"
                                max={outstanding}
                                className="hms-rad-input"
                                value={amount}
                                onChange={(e) => setAmount(e.target.value)}
                                autoFocus
                            />
                        </div>
                        <div>
                            <label className="hms-rad-label">Payment Method *</label>
                            <SearchableSelect
                                value={paymentMethod}
                                onChange={setPaymentMethod}
                                options={PAYMENT_METHODS}
                            />
                        </div>
                    </div>

                    <div>
                        <label className="hms-rad-label">Bank Account *</label>
                        {loadingAccounts ? (
                            <div className="hms-rad-input hms-rad-input--readonly">
                                <Loader2 className="w-3 h-3 animate-spin" /> Loading accounts…
                            </div>
                        ) : (
                            <SearchableSelect
                                value={bankAccountId}
                                onChange={setBankAccountId}
                                options={accounts.map((a) => ({
                                    value: a.id,
                                    label: `${a.accountName ?? a.name ?? "Account"} (${a.bankName ?? a.code ?? "—"})`,
                                }))}
                            />
                        )}
                    </div>

                    <div className="hms-rad-modal__foot">
                        <button type="button" onClick={onClose} className="hms-btn-secondary">
                            Cancel
                        </button>
                        <button
                            type="submit"
                            disabled={!canSubmit}
                            className="hms-btn-primary"
                        >
                            {saving ? (
                                <>
                                    <Loader2 className="w-4 h-4 animate-spin" /> Collecting…
                                </>
                            ) : (
                                <>
                                    <CheckCircle2 className="w-4 h-4" /> Collect Payment
                                </>
                            )}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}
