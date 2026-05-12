package work.jscraft.alt.common.security;

public final class BrokerAccountMasker {

    private static final int LEADING_VISIBLE = 4;

    private BrokerAccountMasker() {
    }

    public static String mask(String brokerAccountNo) {
        if (brokerAccountNo == null) {
            return null;
        }
        String stripped = brokerAccountNo.replace("-", "");
        if (stripped.length() <= LEADING_VISIBLE) {
            return repeat('*', stripped.length());
        }
        String visible = stripped.substring(0, LEADING_VISIBLE);
        return visible + repeat('*', stripped.length() - LEADING_VISIBLE);
    }

    private static String repeat(char ch, int count) {
        if (count <= 0) {
            return "";
        }
        char[] buf = new char[count];
        java.util.Arrays.fill(buf, ch);
        return new String(buf);
    }
}
