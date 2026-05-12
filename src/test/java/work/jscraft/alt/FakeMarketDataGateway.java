package work.jscraft.alt;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import work.jscraft.alt.marketdata.application.MarketDataException;
import work.jscraft.alt.marketdata.application.MarketDataGateway;
import work.jscraft.alt.marketdata.application.MarketDataSnapshots.MinuteBar;
import work.jscraft.alt.marketdata.application.MarketDataSnapshots.OrderBookSnapshot;
import work.jscraft.alt.marketdata.application.MarketDataSnapshots.PriceSnapshot;

class FakeMarketDataGateway implements MarketDataGateway {

    private final Map<String, PriceSnapshot> priceResponses = new HashMap<>();
    private final Map<String, MarketDataException> priceFailures = new HashMap<>();
    private final Map<String, List<MinuteBar>> minuteBarResponses = new HashMap<>();
    private final Map<String, MarketDataException> minuteBarFailures = new HashMap<>();
    private final Map<String, OrderBookSnapshot> orderBookResponses = new HashMap<>();

    void resetAll() {
        priceResponses.clear();
        priceFailures.clear();
        minuteBarResponses.clear();
        minuteBarFailures.clear();
        orderBookResponses.clear();
    }

    void primePrice(String symbolCode, PriceSnapshot snapshot) {
        priceResponses.put(symbolCode, snapshot);
        priceFailures.remove(symbolCode);
    }

    void primePriceFailure(String symbolCode, MarketDataException exception) {
        priceFailures.put(symbolCode, exception);
        priceResponses.remove(symbolCode);
    }

    void primeMinuteBars(String symbolCode, List<MinuteBar> bars) {
        minuteBarResponses.put(symbolCode, new ArrayList<>(bars));
        minuteBarFailures.remove(symbolCode);
    }

    void primeMinuteBarsFailure(String symbolCode, MarketDataException exception) {
        minuteBarFailures.put(symbolCode, exception);
        minuteBarResponses.remove(symbolCode);
    }

    void primeOrderBook(String symbolCode, OrderBookSnapshot snapshot) {
        orderBookResponses.put(symbolCode, snapshot);
    }

    @Override
    public PriceSnapshot fetchPrice(String symbolCode) {
        MarketDataException failure = priceFailures.get(symbolCode);
        if (failure != null) {
            throw failure;
        }
        PriceSnapshot snapshot = priceResponses.get(symbolCode);
        if (snapshot == null) {
            throw new MarketDataException(MarketDataException.Category.EMPTY_RESPONSE, "fake",
                    "no fake price for " + symbolCode);
        }
        return snapshot;
    }

    @Override
    public List<MinuteBar> fetchMinuteBars(String symbolCode, LocalDate businessDate) {
        MarketDataException failure = minuteBarFailures.get(symbolCode);
        if (failure != null) {
            throw failure;
        }
        List<MinuteBar> bars = minuteBarResponses.get(symbolCode);
        if (bars == null) {
            return List.of();
        }
        return new ArrayList<>(bars);
    }

    @Override
    public OrderBookSnapshot fetchOrderBook(String symbolCode) {
        OrderBookSnapshot snapshot = orderBookResponses.get(symbolCode);
        if (snapshot == null) {
            throw new MarketDataException(MarketDataException.Category.EMPTY_RESPONSE, "fake",
                    "no fake orderbook for " + symbolCode);
        }
        return snapshot;
    }
}
