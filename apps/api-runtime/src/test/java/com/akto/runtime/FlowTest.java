package com.akto.runtime;

import com.akto.dto.HttpRequestParams;
import com.akto.log.LoggerMaker;
import org.junit.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class FlowTest {
    private static final LoggerMaker loggerMaker = new LoggerMaker(FlowTest.class);

    @Test
    public void testGetUserIdentifier() {
        Map<String, List<String>> headers = new HashMap<>();
        String name = "Access-Token";
        String value = "fwefwieofjweofiew";
        headers.put(name, Arrays.asList(value, "wefiowjefew"));
        HttpRequestParams requestParams = new HttpRequestParams(
                "get", "/api/some", "Http",headers ,"", 0
        );
        String u = null;
        try {
            u = Flow.getUserIdentifier(name, requestParams);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e.toString());
        }

        assertEquals(value, u);
    }

    @Test
    public void testGetUserIdentifierWithoutToken() {
        Map<String, List<String>> headers = new HashMap<>();
        String name = "Access-Token";
        String value = "fwefwieofjweofiew";
        headers.put(name, Arrays.asList(value));
        HttpRequestParams requestParams = new HttpRequestParams(
                "get", "/api/some", "Http",headers ,"", 0
        );
        String u = null;
        try {
            u = Flow.getUserIdentifier("wefwe", requestParams);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e.toString());
        }

        assertNull(u);
    }

    @Test
    public void testGetUserIdentifierEmptyList() {
        Map<String, List<String>> headers = new HashMap<>();
        String name = "Access-Token";
        headers.put(name, new ArrayList<>());
        HttpRequestParams requestParams = new HttpRequestParams(
                "get", "/api/some", "Http",headers ,"",0 
        );
        String u = null;
        try {
            u = Flow.getUserIdentifier("wefwe", requestParams);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e.toString());
        }

        assertNull(u);
    }
}
