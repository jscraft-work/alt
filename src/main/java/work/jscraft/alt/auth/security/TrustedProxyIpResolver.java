package work.jscraft.alt.auth.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import work.jscraft.alt.auth.config.AuthProperties;

@Component
public class TrustedProxyIpResolver {

    private final List<CidrBlock> trustedProxyCidrs;

    public TrustedProxyIpResolver(AuthProperties authProperties) {
        this.trustedProxyCidrs = Arrays.stream(authProperties.getTrustedProxyCidrs().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(CidrBlock::parse)
                .toList();
    }

    public String resolve(HttpServletRequest request) {
        return resolve(request.getRemoteAddr(), request.getHeader("X-Forwarded-For"));
    }

    public String resolve(String remoteAddr, String xForwardedFor) {
        if (!isTrusted(remoteAddr)) {
            return remoteAddr;
        }

        if (!StringUtils.hasText(xForwardedFor)) {
            return remoteAddr;
        }

        List<String> forwardedChain = Arrays.stream(xForwardedFor.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();

        if (forwardedChain.isEmpty()) {
            return remoteAddr;
        }

        for (int index = forwardedChain.size() - 1; index >= 0; index--) {
            String candidate = forwardedChain.get(index);
            if (!isValidIp(candidate)) {
                continue;
            }

            if (!isTrusted(candidate)) {
                return candidate;
            }
        }

        return remoteAddr;
    }

    public boolean isValidIp(String value) {
        if (!StringUtils.hasText(value) || !looksLikeIpLiteral(value)) {
            return false;
        }

        try {
            InetAddress.getByName(value);
            return true;
        } catch (UnknownHostException exception) {
            return false;
        }
    }

    private boolean isTrusted(String ipAddress) {
        if (!isValidIp(ipAddress)) {
            return false;
        }

        return trustedProxyCidrs.stream().anyMatch(cidr -> cidr.matches(ipAddress));
    }

    private boolean looksLikeIpLiteral(String value) {
        return value.contains(":") || value.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }

    private record CidrBlock(byte[] networkAddress, int prefixLength) {

        static CidrBlock parse(String cidr) {
            String[] parts = cidr.split("/", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid CIDR: " + cidr);
            }

            byte[] address = toAddress(parts[0], cidr);
            int prefixLength = parsePrefix(parts[1], address.length, cidr);
            return new CidrBlock(address, prefixLength);
        }

        boolean matches(String ipAddress) {
            byte[] candidate = toAddress(ipAddress, ipAddress);
            if (candidate.length != networkAddress.length) {
                return false;
            }

            int fullBytes = prefixLength / 8;
            int remainderBits = prefixLength % 8;

            for (int index = 0; index < fullBytes; index++) {
                if (networkAddress[index] != candidate[index]) {
                    return false;
                }
            }

            if (remainderBits == 0) {
                return true;
            }

            int mask = 0xFF << (8 - remainderBits);
            return (networkAddress[fullBytes] & mask) == (candidate[fullBytes] & mask);
        }

        private static byte[] toAddress(String value, String source) {
            try {
                return InetAddress.getByName(value).getAddress();
            } catch (UnknownHostException exception) {
                throw new IllegalArgumentException("Invalid IP address: " + source, exception);
            }
        }

        private static int parsePrefix(String prefixValue, int addressLength, String source) {
            try {
                int prefixLength = Integer.parseInt(prefixValue);
                int maxBits = addressLength * 8;
                if (prefixLength < 0 || prefixLength > maxBits) {
                    throw new IllegalArgumentException("Invalid CIDR prefix: " + source);
                }
                return prefixLength;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid CIDR prefix: " + source, exception);
            }
        }
    }
}
