package com.phonepe.sentinelai.models;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.phonepe.platform.spyglass.service.models.TimeRange;

import java.io.IOException;
import java.util.Date;

public class MetricTypeKeyDeserializer extends KeyDeserializer  {

    @Override
    public Object deserializeKey(String key, DeserializationContext deserializationContext) throws IOException {
        try {
           return key;
        } catch (Exception e) {
            throw new IOException("Failed to deserialize TimeRange key: " + key, e);
        }
    }
}
