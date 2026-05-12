package work.jscraft.alt.integrations.telegram;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import work.jscraft.alt.ops.application.AlertEvent;
import work.jscraft.alt.ops.application.AlertGateway;
import work.jscraft.alt.ops.application.AlertPolicy;

@Component
public class TelegramAlertGateway implements AlertGateway {

    private static final Logger log = LoggerFactory.getLogger(TelegramAlertGateway.class);

    private final AlertPolicy alertPolicy;
    private final Clock clock;
    private final TelegramProperties properties;
    private final RestClient restClient;

    @Autowired
    public TelegramAlertGateway(AlertPolicy alertPolicy, Clock clock, TelegramProperties properties) {
        this(alertPolicy, clock, properties, buildRestClient(properties));
    }

    public TelegramAlertGateway(AlertPolicy alertPolicy,
                                Clock clock,
                                TelegramProperties properties,
                                RestClient restClient) {
        this.alertPolicy = alertPolicy;
        this.clock = clock;
        this.properties = properties;
        this.restClient = restClient;
    }

    private static RestClient buildRestClient(TelegramProperties properties) {
        Duration timeout = Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds()));
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(timeout);
        ClientHttpRequestFactory requestFactory = factory;
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public DispatchResult dispatch(AlertEvent event) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (!alertPolicy.isEnabled()) {
            log.info("alert skipped event_disabled severity={} title={}", event.severity(), event.title());
            return DispatchResult.SKIPPED_DISABLED;
        }
        if (!alertPolicy.shouldDispatch(event, now)) {
            log.info("alert skipped quiet_hours severity={} title={}", event.severity(), event.title());
            return DispatchResult.SKIPPED_QUIET_HOURS;
        }
        String botToken = properties.getBotToken();
        String chatId = properties.getChatId();
        if (isBlank(botToken) || isBlank(chatId)) {
            log.warn("alert send failed missing_credentials severity={} title={}",
                    event.severity(), event.title());
            return DispatchResult.FAILED;
        }

        String url = properties.getBaseUrl() + "/bot" + botToken + "/sendMessage";
        Map<String, String> body = Map.of(
                "chat_id", chatId,
                "text", formatText(event));
        try {
            restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("alert dispatched severity={} title={}", event.severity(), event.title());
            return DispatchResult.SENT;
        } catch (Exception ex) {
            log.error("alert send failed severity={} title={} error={}",
                    event.severity(), event.title(), ex.getClass().getSimpleName());
            return DispatchResult.FAILED;
        }
    }

    private static String formatText(AlertEvent event) {
        return "[" + event.severity() + "] " + event.title() + "\n" + event.message();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
