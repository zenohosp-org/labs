import { useState, useEffect } from "react";
import { useAuth } from "@/context/AuthContext";
import { useNotification } from "@/context/NotificationContext";
import { hospitalServiceApi } from "@/utils/api";
import {
    Button,
    Drawer,
    FormGroup,
    Input,
    Modal,
} from "@/components/ui";
import SearchableSelect from "@/components/ui/SearchableSelect";

/**
 * Add / Edit Service.
 *
 * UX contract preserved from the pre-migration file:
 *   * `service` truthy  → edit, opens a right-edge Drawer.
 *   * `service` falsey  → create, opens a centred Modal.
 * Both shells share the same form id so a single submit pipeline is the
 * source of truth.
 *
 * <SearchableSelect> is kept on the legacy stack for now — replacing the
 * searchable combobox is a separate concern from the design-system
 * migration and will happen later. Lives inside an <FormGroup> so the
 * label and surrounding rhythm match the rest of the form.
 */
function AddServiceModal({ isOpen, onClose, service, specializations, onSuccess }) {
    const { user } = useAuth();
    const { notify } = useNotification();
    const [loading, setLoading] = useState(false);
    const [formData, setFormData] = useState({
        name: "",
        specializationId: "",
        price: "",
        gstRate: "",
    });

    useEffect(() => {
        if (service) {
            setFormData({
                name: service.name,
                specializationId: service.specializationId,
                price: service.price.toString(),
                gstRate:
                    service.gstRate != null && service.gstRate !== 0
                        ? service.gstRate.toString()
                        : "",
            });
        } else {
            setFormData({ name: "", specializationId: "", price: "", gstRate: "" });
        }
    }, [service, isOpen]);

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!user?.hospitalId) return;
        const payload = {
            ...formData,
            hospitalId: user.hospitalId,
            price: parseFloat(formData.price),
            gstRate: Number(formData.gstRate),
            isActive: service ? service.isActive : true,
        };
        setLoading(true);
        try {
            if (service) {
                await hospitalServiceApi.update(service.id, payload);
                notify("Service updated successfully", "success");
            } else {
                await hospitalServiceApi.create(payload);
                notify("Service created successfully", "success");
            }
            onSuccess?.();
            onClose?.();
        } catch {
            notify(service ? "Failed to update service" : "Failed to create service", "error");
        } finally {
            setLoading(false);
        }
    };

    const formId = "service-form";
    const required = <span className="text-danger">*</span>;

    const formBody = (
        <form
            id={formId}
            onSubmit={handleSubmit}
            className="flex flex-col gap-4"
        >
            <FormGroup label={<>Service name {required}</>}>
                <Input
                    required
                    type="text"
                    value={formData.name}
                    onChange={(e) => setFormData((p) => ({ ...p, name: e.target.value }))}
                    placeholder="e.g. General consultation"
                />
            </FormGroup>

            <FormGroup label={<>Department {required}</>}>
                <SearchableSelect
                    value={formData.specializationId}
                    onChange={(v) => setFormData((p) => ({ ...p, specializationId: v }))}
                    options={specializations.map((s) => ({ value: s.id, label: s.name }))}
                    placeholder="Select department"
                />
            </FormGroup>

            <FormGroup label={<>Price (₹) {required}</>}>
                <Input
                    required
                    type="number"
                    step="0.01"
                    min="0"
                    value={formData.price}
                    onChange={(e) => setFormData((p) => ({ ...p, price: e.target.value }))}
                    placeholder="0.00"
                />
            </FormGroup>

            <FormGroup label="GST rate (%)" hint="Leave blank if exempt">
                <Input
                    type="number"
                    min="0"
                    max="28"
                    step="0.5"
                    value={formData.gstRate}
                    onChange={(e) => setFormData((p) => ({ ...p, gstRate: e.target.value }))}
                    placeholder="e.g. 18"
                />
            </FormGroup>
        </form>
    );

    const actionRow = (
        <>
            <Button variant="cancel" onClick={onClose} type="button">
                Cancel
            </Button>
            <Button variant="primary" type="submit" form={formId} loading={loading}>
                {service ? "Update service" : "Add new service"}
            </Button>
        </>
    );

    if (service) {
        return (
            <Drawer
                isOpen={isOpen}
                onClose={onClose}
                title="Edit service"
                footer={actionRow}
            >
                {formBody}
            </Drawer>
        );
    }

    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title="New service"
            size="md"
            footer={actionRow}
        >
            {formBody}
        </Modal>
    );
}

export { AddServiceModal as default };
