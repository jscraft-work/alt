package work.jscraft.alt.common.util;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KrxTickSizeTest {

    /**
     * KRX 7-구간 호가단위 표 — 각 구간 안에서 1 개 + 경계 직전/직후 가격을 함께 검증.
     * 운영자 요청 10 케이스 이상.
     */
    @ParameterizedTest(name = "price={0} → tick={1}")
    @CsvSource({
            "100, 1",          // Tier 1
            "1999, 1",         // Tier 1 상한 직전
            "2000, 5",         // Tier 2 시작
            "4999, 5",         // Tier 2 상한 직전
            "5000, 10",        // Tier 3 시작
            "19999, 10",       // Tier 3 상한 직전
            "20000, 50",       // Tier 4 시작
            "49999, 50",       // Tier 4 상한 직전
            "50000, 100",      // Tier 5 시작
            "199999, 100",     // Tier 5 상한 직전
            "200000, 500",     // Tier 6 시작
            "499999, 500",     // Tier 6 상한 직전
            "500000, 1000",    // Tier 7 시작
            "1234567, 1000"    // Tier 7 임의값
    })
    void tickSize_returnsExpected_perPriceTier(String priceStr, String expectedTickStr) {
        BigDecimal price = new BigDecimal(priceStr);
        BigDecimal expectedTick = new BigDecimal(expectedTickStr);

        assertThat(KrxTickSize.tickSize(price)).isEqualByComparingTo(expectedTick);
    }

    @Test
    void tickSize_rejectsNullOrNonPositive() {
        assertThatThrownBy(() -> KrxTickSize.tickSize(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KrxTickSize.tickSize(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KrxTickSize.tickSize(new BigDecimal("-100")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * BUY 의도가 정합 — 자기 tick 의 가장 가까운 같거나 작은 배수.
     */
    @ParameterizedTest(name = "price={0} → roundDown={1}")
    @CsvSource({
            // 이미 정합 — 그대로 반환
            "100, 100",
            "50000, 50000",
            "200000, 200000",
            // Tier 2 (5원 tick): 2001 → 2000
            "2001, 2000",
            "2004, 2000",
            "2005, 2005",
            // Tier 5 (100원 tick): 50130 → 50100
            "50130, 50100",
            "50199, 50100",
            // Tier 7 (1000원 tick): 501500 → 501000
            "501500, 501000"
    })
    void roundDownToTick_floorsToTickMultiple(String priceStr, String expectedStr) {
        BigDecimal price = new BigDecimal(priceStr);
        BigDecimal expected = new BigDecimal(expectedStr);

        assertThat(KrxTickSize.roundDownToTick(price)).isEqualByComparingTo(expected);
    }

    /**
     * SELL 의도가 정합 — 자기 tick 의 가장 가까운 같거나 큰 배수.
     */
    @ParameterizedTest(name = "price={0} → roundUp={1}")
    @CsvSource({
            // 이미 정합 — 그대로 반환
            "100, 100",
            "50000, 50000",
            "200000, 200000",
            // Tier 2: 2001 → 2005
            "2001, 2005",
            "2004, 2005",
            "2005, 2005",
            // Tier 5: 50130 → 50200
            "50130, 50200",
            "50199, 50200",
            // Tier 7: 501500 → 502000
            "501500, 502000"
    })
    void roundUpToTick_ceilsToTickMultiple(String priceStr, String expectedStr) {
        BigDecimal price = new BigDecimal(priceStr);
        BigDecimal expected = new BigDecimal(expectedStr);

        assertThat(KrxTickSize.roundUpToTick(price)).isEqualByComparingTo(expected);
    }
}
