package work.jscraft.alt.interfaces.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import work.jscraft.alt.common.config.ApplicationProfiles;
import work.jscraft.alt.integrations.kis.websocket.KisWebSocketClient;

/**
 * KIS 실시간 호가는 정규장(09:00~15:30 KST)에만 흐른다. WS 세션 수명을 장 시간에 맞춘다 —
 * 개장 직전(08:55) 연결, 장 마감(15:30) 종료. 이렇게 하면 야간에 연결을 안 들고 있어
 * KIS 야간 점검/끊김으로 인한 좀비 연결·재연결 실패가 원천 차단된다.
 *
 * <p>장중 못 붙으면 {@link KisWebSocketClient} 의 reconnect 루프가 20회까지 재시도 후 경보하고
 * 멈추지만, 매일 이 스케줄이 새 세션을 열어 다음 장에 자동 복구된다.
 */
@Component
@Profile(ApplicationProfiles.COLLECTOR_WORKER)
public class KisWebSocketSessionScheduler {

    private static final Logger log = LoggerFactory.getLogger(KisWebSocketSessionScheduler.class);

    private final KisWebSocketClient kisWebSocketClient;

    public KisWebSocketSessionScheduler(KisWebSocketClient kisWebSocketClient) {
        this.kisWebSocketClient = kisWebSocketClient;
    }

    @Scheduled(cron = "${app.kis.ws.session-open-cron:0 55 8 * * MON-FRI}", zone = "Asia/Seoul")
    public void openSession() {
        log.info("KIS WS 세션 개시 (장 개장 준비)");
        kisWebSocketClient.connect();
    }

    @Scheduled(cron = "${app.kis.ws.session-close-cron:0 30 15 * * MON-FRI}", zone = "Asia/Seoul")
    public void closeSession() {
        log.info("KIS WS 세션 종료 (장 마감)");
        kisWebSocketClient.disconnect();
    }
}
