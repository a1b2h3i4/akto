package com.akto.testing;

import com.akto.runtime.URLAggregator;
import com.mongodb.BasicDBObject;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;

public class ApiExecutorTest {

    @Test
    public void testMakeUrlAbsolute() throws Exception {
        String originalUrl = "/dashboard";
        String url = ApiExecutor.makeUrlAbsolute(originalUrl, "akto.io", "https");
        assertEquals(url, "https://akto.io/dashboard");

        originalUrl = "dashboard";
        url = ApiExecutor.makeUrlAbsolute(originalUrl, "akto.io", "http");
        assertEquals(url, "http://akto.io/dashboard");

        originalUrl = "/dashboard";
        url = ApiExecutor.makeUrlAbsolute(originalUrl, "https://www.akto.io/", null);
        assertEquals(url, "https://www.akto.io/dashboard");

        originalUrl = "/dashboard";
        url = ApiExecutor.makeUrlAbsolute(originalUrl, "akto.io/", null);
        assertEquals(url, "https://akto.io/dashboard");

        originalUrl = "/dashboard";
        url = ApiExecutor.makeUrlAbsolute(originalUrl, "127.0.0.1", null );
        assertEquals(url, "http://127.0.0.1/dashboard");
    }

    @Test
    public void testGetRawQueryFromJson() {
        String normalReq = "{\"name\": \"avneesh\", \"cities\": [{\"name\": \"Mumbai\"}, {\"name\": \"Bangalore\"}], \"age\": 99}";
        String resultNormalReq = ApiExecutor.getRawQueryFromJson(normalReq);
        BasicDBObject queryParams = URLAggregator.getQueryJSON("?"+ resultNormalReq);
        assertEquals(3, queryParams.size());
    }
}
