package com.phonepe.sentinelai.toolbox.mcp;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;

import java.io.IOException;

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
