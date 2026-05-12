package work.jscraft.alt.trading.application.broker;

import java.math.BigDecimal;
import java.util.List;

public record AccountStatus(
        BigDecimal cashAmount,
        BigDecimal totalAssetAmount,
        List<HoldingItem> holdings) {

    public record HoldingItem(String symbolCode, BigDecimal quantity, BigDecimal avgBuyPrice) {
    }
}
