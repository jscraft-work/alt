package work.jscraft.alt;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import work.jscraft.alt.integrations.kis.broker.KisResponseMapper;
import work.jscraft.alt.trading.application.broker.AccountStatus;
import work.jscraft.alt.trading.application.broker.BrokerGatewayException;
import work.jscraft.alt.trading.application.broker.OrderStatusResult;
import work.jscraft.alt.trading.application.broker.PlaceOrderResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KisBrokerGatewayContractTest {

    private final KisResponseMapper mapper = new KisResponseMapper(new ObjectMapper());

    @Test
    void placeOrderSuccessMapsAcceptedStatusAndBrokerOrderNo() {
        String body = """
                {
                  "rt_cd":"0",
                  "msg1":"OK",
                  "output":{
                    "ODNO":"BROKER-001",
                    "ord_qty":"5",
                    "tot_ccld_qty":"0"
                  }
                }
                """;

        PlaceOrderResult result = mapper.mapPlaceOrderResponse("client-1", body);

        assertThat(result.clientOrderId()).isEqualTo("client-1");
        assertThat(result.brokerOrderNo()).isEqualTo("BROKER-001");
        assertThat(result.orderStatus()).isEqualTo(PlaceOrderResult.STATUS_ACCEPTED);
        assertThat(result.filledQuantity()).isEqualByComparingTo("0");
        assertThat(result.failureReason()).isNull();
    }

    @Test
    void placeOrderImmediateFillMapsFilledStatusAndAvgPrice() {
        String body = """
                {
                  "rt_cd":"0",
                  "output":{
                    "ODNO":"BROKER-FILL",
                    "ord_qty":"5",
                    "tot_ccld_qty":"5",
                    "avg_prvs":"81000.00000000"
                  }
                }
                """;

        PlaceOrderResult result = mapper.mapPlaceOrderResponse("client-fill", body);

        assertThat(result.orderStatus()).isEqualTo(PlaceOrderResult.STATUS_FILLED);
        assertThat(result.filledQuantity()).isEqualByComparingTo("5");
        assertThat(result.avgFilledPrice()).isEqualByComparingTo("81000");
    }

    @Test
    void placeOrderRejectionMapsToRejectedWithReason() {
        String body = """
                {
                  "rt_cd":"3",
                  "msg1":"잔고 부족",
                  "output":{}
                }
                """;

        PlaceOrderResult result = mapper.mapPlaceOrderResponse("client-rej", body);

        assertThat(result.orderStatus()).isEqualTo(PlaceOrderResult.STATUS_REJECTED);
        assertThat(result.failureReason()).isEqualTo("잔고 부족");
        assertThat(result.brokerOrderNo()).isNull();
    }

    @Test
    void orderStatusPartialFillIsClassifiedAsPartial() {
        String body = """
                {
                  "rt_cd":"0",
                  "output":{
                    "ODNO":"BROKER-PART",
                    "ord_qty":"10",
                    "tot_ccld_qty":"3",
                    "avg_prvs":"80500"
                  }
                }
                """;

        OrderStatusResult result = mapper.mapOrderStatusResponse(body);

        assertThat(result.brokerOrderNo()).isEqualTo("BROKER-PART");
        assertThat(result.orderStatus()).isEqualTo(PlaceOrderResult.STATUS_PARTIAL);
        assertThat(result.filledQuantity()).isEqualByComparingTo("3");
        assertThat(result.avgFilledPrice()).isEqualByComparingTo("80500");
        assertThat(result.isTerminal()).isFalse();
    }

    @Test
    void orderStatusFullFillIsTerminal() {
        String body = """
                {
                  "rt_cd":"0",
                  "output":{
                    "ODNO":"BROKER-DONE",
                    "ord_qty":"5",
                    "tot_ccld_qty":"5",
                    "avg_prvs":"82000"
                  }
                }
                """;

        OrderStatusResult result = mapper.mapOrderStatusResponse(body);

        assertThat(result.orderStatus()).isEqualTo(PlaceOrderResult.STATUS_FILLED);
        assertThat(result.isTerminal()).isTrue();
    }

    @Test
    void orderStatusErrorRaisesGatewayException() {
        String body = """
                {"rt_cd":"3","msg1":"order not found"}
                """;

        assertThatThrownBy(() -> mapper.mapOrderStatusResponse(body))
                .isInstanceOf(BrokerGatewayException.class);
    }

    @Test
    void accountStatusMapsCashAndHoldings() {
        String body = """
                {
                  "rt_cd":"0",
                  "output":{
                    "dnca_tot_amt":"6200000",
                    "tot_evlu_amt":"10120000"
                  },
                  "positions":[
                    {"pdno":"005930","hldg_qty":"12","pchs_avg_pric":"81200"},
                    {"pdno":"000660","hldg_qty":"4","pchs_avg_pric":"120000"}
                  ]
                }
                """;

        AccountStatus status = mapper.mapAccountStatusResponse(body);

        assertThat(status.cashAmount()).isEqualByComparingTo("6200000");
        assertThat(status.totalAssetAmount()).isEqualByComparingTo("10120000");
        assertThat(status.holdings()).hasSize(2);
        assertThat(status.holdings().get(0).symbolCode()).isEqualTo("005930");
        assertThat(status.holdings().get(0).quantity()).isEqualByComparingTo("12");
        assertThat(status.holdings().get(0).avgBuyPrice()).isEqualByComparingTo("81200");
    }

    @Test
    void emptyResponseBodyRaisesEmptyResponse() {
        assertThatThrownBy(() -> mapper.mapPlaceOrderResponse("client", ""))
                .isInstanceOf(BrokerGatewayException.class);
    }
}
