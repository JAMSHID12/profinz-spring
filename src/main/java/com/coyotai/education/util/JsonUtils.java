package com.coyotai.education.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.coyotai.education.common.BusinessRuleException;

/** Small Jackson facade used for the notification payload column. */
public final class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtils() {
    }

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessRuleException("Unable to serialise payload: " + ex.getOriginalMessage());
        }
    }

    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new BusinessRuleException("Unable to read stored payload: " + ex.getOriginalMessage());
        }
    }
}
