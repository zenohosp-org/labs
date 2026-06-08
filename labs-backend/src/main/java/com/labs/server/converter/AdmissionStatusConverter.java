package com.labs.server.converter;

import com.labs.server.entity.AdmissionStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Mirrors HMS AdmissionStatusConverter so labs can read admissions.status_id
 * (id-converted on main) when deciding IPD vs OPD billing routing.
 */
@Converter
public class AdmissionStatusConverter implements AttributeConverter<AdmissionStatus, Integer> {

    @Override
    public Integer convertToDatabaseColumn(AdmissionStatus status) {
        return status == null ? null : status.id;
    }

    @Override
    public AdmissionStatus convertToEntityAttribute(Integer id) {
        return id == null ? null : AdmissionStatus.fromId(id);
    }
}
