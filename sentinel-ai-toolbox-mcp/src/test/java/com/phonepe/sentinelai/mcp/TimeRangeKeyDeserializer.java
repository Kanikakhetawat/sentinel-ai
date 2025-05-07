package com.phonepe.sentinelai.mcp;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.phonepe.platform.spyglass.service.models.TimeRange;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TimeRangeKeyDeserializer extends KeyDeserializer {

    private static final SimpleDateFormat format = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.ENGLISH);


    @Override
    public Object deserializeKey(String key, DeserializationContext deserializationContext) throws IOException {
        try {

            if (key == null || !key.startsWith("TimeRange(start=")) {
                throw new IllegalArgumentException("Invalid TimeRange string: " + key);
            }

            String content = key.replace("TimeRange(start=", "").replace(")", "");
            String[] parts = content.split(", end=");

            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid TimeRange format: " + key);
            }


            Date start = format.parse(parts[0].trim());
            Date end = format.parse(parts[1].trim());

            return new TimeRange(start, end);
        } catch (Exception e) {
            throw new IOException("Failed to deserialize TimeRange key: " + key, e);
        }
    }
}
