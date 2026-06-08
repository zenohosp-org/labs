package com.labs.server.converter;

import com.labs.server.entity.LabPriority;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class LabPriorityConverter implements AttributeConverter<LabPriority, Integer> {

    @Override
    public Integer convertToDatabaseColumn(LabPriority priority) {
        return priority == null ? null : priority.id;
    }

    @Override
    public LabPriority convertToEntityAttribute(Integer id) {
        return id == null ? null : LabPriority.fromId(id);
    }
}
