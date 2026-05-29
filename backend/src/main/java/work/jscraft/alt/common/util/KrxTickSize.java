package work.jscraft.alt.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * KRX 가격대별 1 틱(=호가단위) lookup + LIMIT 가격의 호가단위 정합화.
 *
 * <p>2023-01-25 KRX 호가단위 통일 적용. KOSPI / KOSDAQ 동일 표.
 *
 * <ul>
 *   <li>가격 &lt; 2,000원: 1원</li>
 *   <li>2,000원 ≤ 가격 &lt; 5,000원: 5원</li>
 *   <li>5,000원 ≤ 가격 &lt; 20,000원: 10원</li>
 *   <li>20,000원 ≤ 가격 &lt; 50,000원: 50원</li>
 *   <li>50,000원 ≤ 가격 &lt; 200,000원: 100원</li>
 *   <li>200,000원 ≤ 가격 &lt; 500,000원: 500원</li>
 *   <li>500,000원 이상: 1,000원</li>
 * </ul>
 *
 * <p>PaperOrderExecutor (M1) / PaperOrderbookWalkSimulator (N2) 가 LIMIT 가격을 호가단위에 정합시키는 데
 * 사용한다. BUY 는 {@link #roundDownToTick(BigDecimal)} (의도가보다 같거나 낮은 호가),
 * SELL 은 {@link #roundUpToTick(BigDecimal)} (의도가보다 같거나 높은 호가).
 */
public final class KrxTickSize {

    private KrxTickSize() {
    }

    private static final BigDecimal TIER_1_THRESHOLD = new BigDecimal("2000");
    private static final BigDecimal TIER_2_THRESHOLD = new BigDecimal("5000");
    private static final BigDecimal TIER_3_THRESHOLD = new BigDecimal("20000");
    private static final BigDecimal TIER_4_THRESHOLD = new BigDecimal("50000");
    private static final BigDecimal TIER_5_THRESHOLD = new BigDecimal("200000");
    private static final BigDecimal TIER_6_THRESHOLD = new BigDecimal("500000");

    private static final BigDecimal TICK_1 = BigDecimal.ONE;
    private static final BigDecimal TICK_5 = new BigDecimal("5");
    private static final BigDecimal TICK_10 = BigDecimal.TEN;
    private static final BigDecimal TICK_50 = new BigDecimal("50");
    private static final BigDecimal TICK_100 = new BigDecimal("100");
    private static final BigDecimal TICK_500 = new BigDecimal("500");
    private static final BigDecimal TICK_1000 = new BigDecimal("1000");

    /**
     * 주어진 가격에 적용되는 KRX 1 틱(호가단위)을 반환한다.
     *
     * @param price 양수 가격 (BigDecimal)
     * @return 1 틱 (원). 가격이 null/0 이하면 {@link IllegalArgumentException}
     */
    public static BigDecimal tickSize(BigDecimal price) {
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("price 는 양수여야 합니다: " + price);
        }
        if (price.compareTo(TIER_1_THRESHOLD) < 0) {
            return TICK_1;
        }
        if (price.compareTo(TIER_2_THRESHOLD) < 0) {
            return TICK_5;
        }
        if (price.compareTo(TIER_3_THRESHOLD) < 0) {
            return TICK_10;
        }
        if (price.compareTo(TIER_4_THRESHOLD) < 0) {
            return TICK_50;
        }
        if (price.compareTo(TIER_5_THRESHOLD) < 0) {
            return TICK_100;
        }
        if (price.compareTo(TIER_6_THRESHOLD) < 0) {
            return TICK_500;
        }
        return TICK_1000;
    }

    /**
     * 가격을 자기 tick 의 배수 중 가장 가까운 같거나 작은 값으로 정합화 (BUY 의도가 정합용).
     * 이미 호가단위에 정합되어 있으면 그대로 반환.
     *
     * <p>예: 50,130 원 → 50,100 원 (50,000~200,000 구간 100 원 tick).
     */
    public static BigDecimal roundDownToTick(BigDecimal price) {
        BigDecimal tick = tickSize(price);
        return price.divide(tick, 0, RoundingMode.FLOOR).multiply(tick);
    }

    /**
     * 가격을 자기 tick 의 배수 중 가장 가까운 같거나 큰 값으로 정합화 (SELL 의도가 정합용).
     * 이미 호가단위에 정합되어 있으면 그대로 반환.
     *
     * <p>경계 (tier 변경) 가격은 정합 후 가격이 다음 tier 에 속할 수 있다.
     * 예: 199,950 원 → 200,000 원 (100 원 tick 으로 ceiling, 그 결과는 200,000~500,000 구간 500 원 tick 에 속함).
     */
    public static BigDecimal roundUpToTick(BigDecimal price) {
        BigDecimal tick = tickSize(price);
        return price.divide(tick, 0, RoundingMode.CEILING).multiply(tick);
    }
}
