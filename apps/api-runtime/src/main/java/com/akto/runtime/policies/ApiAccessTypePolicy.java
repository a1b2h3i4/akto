package com.akto.runtime.policies;

import com.akto.dto.ApiInfo;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.runtime_filters.RuntimeFilter;
import com.akto.log.LoggerMaker;
import com.akto.parsers.HttpCallParser;

import org.springframework.security.web.util.matcher.IpAddressMatcher;

import java.util.ArrayList;
import java.util.List;

public class ApiAccessTypePolicy {
    private List<String> privateCidrList;
    public static final String X_FORWARDED_FOR = "x-forwarded-for";
    private static final LoggerMaker loggerMaker = new LoggerMaker(ApiAccessTypePolicy.class);

    public ApiAccessTypePolicy(List<String> privateCidrList) {
        this.privateCidrList = privateCidrList;
    }


    public boolean findApiAccessType(HttpResponseParams httpResponseParams, ApiInfo apiInfo, RuntimeFilter filter) {
        if (privateCidrList == null || privateCidrList.isEmpty()) return false;
        List<String> ipList = httpResponseParams.getRequestParams().getHeaders().get(X_FORWARDED_FOR);

        if (ipList == null) {
            ipList = new ArrayList<>();
        }

        String sourceIP = httpResponseParams.getSourceIP();

        if (sourceIP != null && !sourceIP.isEmpty()) {
            ipList.add(sourceIP);
        }

        for (String ip: ipList) {
           if (ip == null) continue;
           ip = ip.replaceAll(" ", "");
           try {
                boolean result = ipInCidr(ip);
                if (!result) {
                    apiInfo.getApiAccessTypes().add(ApiInfo.ApiAccessType.PUBLIC);
                    return false;
                }
           } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e.toString());
                return false;
           }
        }

        apiInfo.getApiAccessTypes().add(ApiInfo.ApiAccessType.PRIVATE);

        return false;
    }

    public boolean ipInCidr(String ip) {
        IpAddressMatcher ipAddressMatcher;
        for (String cidr: privateCidrList) {
            ipAddressMatcher = new IpAddressMatcher(cidr);
            boolean result = ipAddressMatcher.matches(ip);
            if (result) return true;
        }

        return false;
    }

    public void setPrivateCidrList(List<String> privateCidrList) {
        this.privateCidrList = privateCidrList;
    }
}
