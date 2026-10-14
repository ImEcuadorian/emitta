package io.github.imecuadorian.emitta.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;

class TrustedClientAddressTest {
    @Test void trustsOnlyExactTunnelPeerAndSingleLiteralHeader() {
        var peers=TrustedClientAddress.peers("192.0.2.10");
        var request=new MockHttpServletRequest();request.setRemoteAddr("192.0.2.11");
        request.addHeader("CF-Connecting-IP","203.0.113.8");
        request.addHeader("X-Forwarded-For","203.0.113.9");
        assertEquals("192.0.2.11",TrustedClientAddress.resolve(request,peers));
        request.setRemoteAddr("192.0.2.10");
        assertEquals("203.0.113.8",TrustedClientAddress.resolve(request,peers));
        request.addHeader("CF-Connecting-IP","203.0.113.9");
        assertEquals("192.0.2.10",TrustedClientAddress.resolve(request,peers));
    }
    @Test void rejectsHostnamesCidrsPortsListsAndAmbiguousLiterals() {
        for(String invalid:java.util.List.of("proxy.example","192.0.2.0/24","*","192.0.2.10:80","010.0.0.1","999.0.0.1"))
            assertThrows(IllegalArgumentException.class,()->TrustedClientAddress.peers(invalid));
        var request=new MockHttpServletRequest();request.setRemoteAddr("192.0.2.10");
        for(String invalid:java.util.List.of("localhost","203.0.113.8, 203.0.113.9","[2001:db8::1]","fe80::1%eth0")) {
            request.removeHeader("CF-Connecting-IP");request.addHeader("CF-Connecting-IP",invalid);
            assertEquals("192.0.2.10",TrustedClientAddress.resolve(request,TrustedClientAddress.peers("192.0.2.10")));
        }
        request.removeHeader("CF-Connecting-IP");request.addHeader("CF-Connecting-IP","2001:db8::1");
        assertEquals("2001:db8:0:0:0:0:0:1",TrustedClientAddress.resolve(request,TrustedClientAddress.peers("192.0.2.10")));
        assertEquals("192.0.2.10",TrustedClientAddress.resolve(request,java.util.Set.of()));
    }
}
