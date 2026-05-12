package work.jscraft.alt;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class KstClockConfigTest {

    @Autowired
    private Clock appClock;

    @Test
    void defaultTimezoneIsAsiaSeoul() {
        assertThat(appClock.getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
        assertThat(ZoneId.systemDefault()).isEqualTo(ZoneId.of("Asia/Seoul"));
        assertThat(TimeZone.getDefault().getID()).isEqualTo("Asia/Seoul");
    }
}
