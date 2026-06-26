import { useEffect, useState } from "react";
import {
    Beaker,
    Printer,
    UtensilsCrossed,
    User as UserIcon,
    AlertTriangle,
    Loader2,
} from "lucide-react";
import { useNotification } from "@/context/NotificationContext";
import { useAuth } from "@/context/AuthContext";
import { collectionApi } from "@/api/labsClient";
import {
    Alert,
    Badge,
    Button,
    FormGroup,
    Input,
    Modal,
} from "@/components/ui";
import { printBarcodes } from "@/utils/printBarcodes";

const PRIORITY_TONE = {
    STAT:    "danger",
    URGENT:  "warning",
    ROUTINE: "neutral",
};

/**
 * Bulk-collect confirmation modal opened from CollectionQueue.
 *
 * Shows:
 *   - the patient header (name, UHID, age/sex, fasting flag)
 *   - the full list of orders being collected
 *   - the resolved tube plan (one row per container) — staff can uncheck
 *     a tube if the patient already gave that sample elsewhere
 *   - a "phlebotomist name" field (defaults to JWT email)
 *   - the Collect & Print button — atomically marks orders + creates
 *     specimens + opens the print window with one label per specimen.
 */
export default function BulkCollectModal({ patient, onClose, onCollected }) {
    const { user } = useAuth();
    const { notify } = useNotification();
    const [collectedBy, setCollectedBy] = useState(user?.email || "");
    const [collectionSite, setCollectionSite] = useState("Antecubital fossa");
    const [notes, setNotes] = useState("");
    const [includedTubes, setIncludedTubes] = useState({});
    const [submitting, setSubmitting] = useState(false);

    useEffect(() => {
        if (!patient?.containerPlan) return;
        const initial = {};
        for (const t of patient.containerPlan) initial[t.containerType] = true;
        setIncludedTubes(initial);
    }, [patient?.containerPlan]);

    if (!patient) return null;

    const toggleTube = (containerType) => {
        setIncludedTubes((s) => ({ ...s, [containerType]: !s[containerType] }));
    };

    const hasFasting = (patient.containerPlan || []).some((t) => t.fastingRequired);

    const handleCollect = async () => {
        const tubes = (patient.containerPlan || [])
            .filter((t) => includedTubes[t.containerType])
            .map((t) => ({
                containerType: t.containerType,
                additive: t.additive,
                volumeMl: t.volumeMl,
                servesOrderIds: t.servesOrderIds,
            }));
        if (tubes.length === 0) {
            notify("Pick at least one tube", "error");
            return;
        }

        setSubmitting(true);
        try {
            const result = await collectionApi.bulkCollect({
                patientId: patient.patientId,
                hospitalId: patient.orders?.[0]?.hospitalId || user?.hospitalId,
                orderIds: patient.orders.map((o) => o.id),
                tubes,
                collectedByName: collectedBy.trim() || null,
                collectionSite: collectionSite.trim() || null,
                notes: notes.trim() || null,
            });
            notify(
                `Collected ${result.orderCount} order(s) in ${result.tubeCount} tube(s)`,
                "success",
            );
            // Print barcodes — pull what the backend just created
            printBarcodes({
                patient: { name: patient.patientName, uhid: patient.patientUhid },
                specimens: (result.createdSpecimens || []).map((s) => ({
                    containerType: s.containerType,
                    volumeMl: s.volumeMl,
                    barcode: s.barcode,
                    accessionNumber: patient.orders.find((o) => o.id === s.labOrderId)
                        ?.accessionNumber,
                })),
            });
            onCollected?.(result);
            onClose();
        } catch (err) {
            notify(
                err?.response?.data?.message ||
                    err?.response?.data ||
                    "Bulk-collect failed — nothing was changed",
                "error",
            );
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <Modal
            isOpen
            onClose={() => !submitting && onClose?.()}
            size="xl"
            title={
                <span className="inline-flex items-center gap-2">
                    <Beaker size={18} /> Bulk collect · {patient.patientName}
                    {patient.highestPriority && (
                        <Badge tone={PRIORITY_TONE[patient.highestPriority] || "neutral"} soft>
                            {patient.highestPriority}
                        </Badge>
                    )}
                </span>
            }
            footer={
                <>
                    <Button variant="cancel" onClick={onClose} disabled={submitting}>
                        Cancel
                    </Button>
                    <Button variant="primary" onClick={handleCollect} disabled={submitting}>
                        {submitting ? (
                            <>
                                <Loader2 size={14} className="animate-spin" /> Collecting…
                            </>
                        ) : (
                            <>
                                <Printer size={14} /> Collect &amp; Print labels
                            </>
                        )}
                    </Button>
                </>
            }
        >
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-12 mb-3">
                <Cell label="UHID" value={patient.patientUhid || "—"} />
                <Cell
                    label="Age / Sex"
                    value={`${patient.ageYears != null ? patient.ageYears + " yrs" : "—"} · ${patient.patientSex || "—"}`}
                />
                <Cell label="Phone" value={patient.patientPhone || "—"} />
                <Cell label="Orders pending" value={String(patient.orders?.length || 0)} />
            </div>

            {hasFasting && (
                <Alert tone="warning" icon={<UtensilsCrossed size={14} />}>
                    One or more tests require fasting. Confirm with the patient
                    before drawing.
                </Alert>
            )}

            <div className="text-12 font-bold text-gray-500 uppercase tracking-wide mb-2">
                Tube plan ({(patient.containerPlan || []).length})
            </div>
            <div className="flex flex-col gap-2 mb-4">
                {(patient.containerPlan || []).map((t) => (
                    <TubeRow
                        key={t.containerType}
                        tube={t}
                        included={!!includedTubes[t.containerType]}
                        onToggle={() => toggleTube(t.containerType)}
                    />
                ))}
                {(patient.containerPlan || []).length === 0 && (
                    <div className="text-13 text-gray-400">No tubes — orders may be radiology only.</div>
                )}
            </div>

            <div className="text-12 font-bold text-gray-500 uppercase tracking-wide mb-2">
                Orders being collected ({patient.orders?.length || 0})
            </div>
            <div className="flex flex-col gap-1 mb-4 text-12">
                {(patient.orders || []).map((o) => (
                    <div key={o.id} className="flex items-center gap-2 border-b border-gray-100 pb-1">
                        <Badge tone={PRIORITY_TONE[o.priority] || "neutral"} soft>{o.priority}</Badge>
                        <span className="text-gray-700">#{o.id}</span>
                        <span className="font-bold text-gray-900 flex-1">{o.serviceName}</span>
                        {o.resolvedContainer && (
                            <span className="text-11 text-gray-500">{o.resolvedContainer}</span>
                        )}
                        {o.referredByName && (
                            <span className="text-11 text-gray-500">· Dr {o.referredByName}</span>
                        )}
                    </div>
                ))}
            </div>

            <div className="grid grid-cols-2 gap-3">
                <FormGroup label="Phlebotomist" hint="Defaults to your login email">
                    <Input value={collectedBy} onChange={(e) => setCollectedBy(e.target.value)} />
                </FormGroup>
                <FormGroup label="Collection site">
                    <Input
                        value={collectionSite}
                        onChange={(e) => setCollectionSite(e.target.value)}
                        placeholder="Antecubital fossa"
                    />
                </FormGroup>
            </div>
            <FormGroup label="Notes (optional)">
                <Input value={notes} onChange={(e) => setNotes(e.target.value)} />
            </FormGroup>

            <p className="text-11 text-gray-500 mt-2">
                <AlertTriangle size={11} className="inline mr-1 text-amber-600" />
                Atomic — if any order can't be marked collected, the whole pickup rolls back.
                Labels print automatically on success.
            </p>
        </Modal>
    );
}

function TubeRow({ tube, included, onToggle }) {
    return (
        <label
            className={`flex items-start gap-3 border rounded-lg p-2 cursor-pointer ${
                included ? "border-emerald-400 bg-emerald-50/30" : "border-gray-200 bg-white"
            }`}
        >
            <input type="checkbox" checked={included} onChange={onToggle} className="mt-1" />
            <div className="flex-1">
                <div className="flex items-center gap-2 flex-wrap">
                    <Badge tone="info" soft>{tube.containerType}</Badge>
                    {tube.volumeMl != null && (
                        <span className="text-12 text-gray-700">{tube.volumeMl} mL</span>
                    )}
                    {tube.fastingRequired && (
                        <Badge tone="warning" soft>
                            <UtensilsCrossed size={11} className="inline mr-1" /> fasting
                        </Badge>
                    )}
                    <span className="text-11 text-gray-500">serves {tube.servesOrderIds.length} order(s)</span>
                </div>
                <div className="text-11 text-gray-600 mt-1">
                    {tube.servesTestNames.join(" · ")}
                </div>
            </div>
        </label>
    );
}

function Cell({ label, value }) {
    return (
        <div className="bg-gray-50 border border-gray-100 rounded px-2 py-1.5">
            <div className="text-11 text-gray-500 uppercase tracking-wide">{label}</div>
            <div className="text-13 text-gray-900 font-bold">{value}</div>
        </div>
    );
}
