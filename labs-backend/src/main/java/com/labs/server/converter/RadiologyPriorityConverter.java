package com.labs.server.converter;

import com.labs.server.entity.RadiologyPriority;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class RadiologyPriorityConverter implements AttributeConverter<RadiologyPriority, Integer> {

    @Override
    public Integer convertToDatabaseColumn(RadiologyPriority priority) {
        return priority == null ? null : priority.id;
    }

    @Override
    public RadiologyPriority convertToEntityAttribute(Integer id) {
        return id == null ? null : RadiologyPriority.fromId(id);
    }
}
