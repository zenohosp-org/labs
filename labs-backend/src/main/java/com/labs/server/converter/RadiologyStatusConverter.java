package com.labs.server.converter;

import com.labs.server.entity.RadiologyStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class RadiologyStatusConverter implements AttributeConverter<RadiologyStatus, Integer> {

    @Override
    public Integer convertToDatabaseColumn(RadiologyStatus status) {
        return status == null ? null : status.id;
    }

    @Override
    public RadiologyStatus convertToEntityAttribute(Integer id) {
        return id == null ? null : RadiologyStatus.fromId(id);
    }
}
