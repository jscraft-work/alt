package work.jscraft.alt;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AltTestProfile
@AutoConfigureMockMvc
@Import({ PostgreSqlTestConfiguration.class, RedisTestConfiguration.class })
class HealthEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointReturnsUp() throws Exception {
        String payload = mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload);

        assertThat(HttpStatus.OK.value()).isEqualTo(200);
        assertThat(response.path("status").asText()).isEqualTo("UP");
    }
}
