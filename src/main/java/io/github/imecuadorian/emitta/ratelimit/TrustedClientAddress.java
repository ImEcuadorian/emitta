package io.github.imecuadorian.emitta.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.Set;

/** Trust only literal socket peers explicitly configured for the Cloudflare tunnel. Never resolve DNS. */
public final class TrustedClientAddress {
    private TrustedClientAddress() {}
    public static Set<String> peers(String configured) {
        if (configured == null || configured.isBlank()) return Set.of();
        var result = new java.util.HashSet<String>();
        for (String peer : configured.split(",", -1)) {
            String literal = literal(peer.trim());
            if (literal == null) throw new IllegalArgumentException("Trusted proxy peers must be literal IP addresses");
            result.add(literal);
        }
        return Set.copyOf(result);
    }
    public static String resolve(HttpServletRequest request, Set<String> peers) {
        String socket = request.getRemoteAddr();
        String normalized = literal(socket);
        if (normalized == null || !peers.contains(normalized)) return socket;
        var values = request.getHeaders("CF-Connecting-IP");
        if (values == null || !values.hasMoreElements()) return socket;
        String value = values.nextElement();
        if (values.hasMoreElements()) return socket;
        String parsed = literal(value);
        return parsed == null ? socket : parsed;
    }
    private static String literal(String value) {
        if (value == null || value.length() > 45) return null;
        if (value.matches("[0-9.]+")) {
            String[] parts = value.split("\\.", -1);
            if (parts.length != 4) return null;
            for (String part : parts) {
                if (!part.matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(part) > 255) return null;
            }
            return value;
        }
        if (!value.contains(":") || !value.matches("[a-fA-F0-9:]+")) return null;
        try { return InetAddress.getByName(value).getHostAddress(); }
        catch (java.net.UnknownHostException invalid) { return null; }
    }
}
