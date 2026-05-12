package work.jscraft.alt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import work.jscraft.alt.auth.config.AuthProperties;
import work.jscraft.alt.auth.security.TrustedProxyIpResolver;

class TrustedProxyIpResolverTest {

    @Test
    void remoteAddrIsUsedWhenImmediatePeerIsNotTrusted() {
        TrustedProxyIpResolver resolver = resolver("10.0.0.0/8");

        String clientIp = resolver.resolve("198.51.100.10", "203.0.113.5, 10.0.0.20");

        assertThat(clientIp).isEqualTo("198.51.100.10");
    }

    @Test
    void forwardedChainUsesRightMostNonTrustedAddress() {
        TrustedProxyIpResolver resolver = resolver("10.0.0.0/8,192.168.0.0/16");

        String clientIp = resolver.resolve("10.0.0.20", "198.51.100.10, 192.168.0.50, 10.0.0.10");

        assertThat(clientIp).isEqualTo("198.51.100.10");
    }

    @Test
    void invalidForwardedValuesFallBackToRemoteAddr() {
        TrustedProxyIpResolver resolver = resolver("10.0.0.0/8");

        String clientIp = resolver.resolve("10.0.0.20", "unknown, also-bad");

        assertThat(clientIp).isEqualTo("10.0.0.20");
    }

    private TrustedProxyIpResolver resolver(String trustedProxyCidrs) {
        AuthProperties authProperties = new AuthProperties();
        authProperties.setTrustedProxyCidrs(trustedProxyCidrs);
        return new TrustedProxyIpResolver(authProperties);
    }
}
