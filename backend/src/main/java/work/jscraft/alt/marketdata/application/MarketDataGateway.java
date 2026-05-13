package work.jscraft.alt.marketdata.application;

import java.time.LocalDate;
import java.util.List;

import work.jscraft.alt.marketdata.application.MarketDataSnapshots.FundamentalSnapshot;
import work.jscraft.alt.marketdata.application.MarketDataSnapshots.MinuteBar;
import work.jscraft.alt.marketdata.application.MarketDataSnapshots.OrderBookSnapshot;
import work.jscraft.alt.marketdata.application.MarketDataSnapshots.PriceSnapshot;

public interface MarketDataGateway {

    PriceSnapshot fetchPrice(String symbolCode);

    List<MinuteBar> fetchMinuteBars(String symbolCode, LocalDate businessDate);

    OrderBookSnapshot fetchOrderBook(String symbolCode);

    FundamentalSnapshot fetchFundamental(String symbolCode);
}
