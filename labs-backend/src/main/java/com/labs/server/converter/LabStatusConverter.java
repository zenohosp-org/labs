package com.labs.server.converter;

import com.labs.server.entity.LabStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class LabStatusConverter implements AttributeConverter<LabStatus, Integer> {

    @Override
    public Integer convertToDatabaseColumn(LabStatus status) {
        return status == null ? null : status.id;
    }

    @Override
    public LabStatus convertToEntityAttribute(Integer id) {
        return id == null ? null : LabStatus.fromId(id);
    }
}
