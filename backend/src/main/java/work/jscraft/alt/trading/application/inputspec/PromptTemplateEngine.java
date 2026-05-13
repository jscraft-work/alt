package work.jscraft.alt.trading.application.inputspec;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;

import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.loader.StringLoader;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import jakarta.annotation.PostConstruct;

import org.springframework.stereotype.Component;

@Component
public class PromptTemplateEngine {

    private PebbleEngine engine;

    @PostConstruct
    public void init() {
        this.engine = new PebbleEngine.Builder()
                .loader(new StringLoader())
                .autoEscaping(false)
                .strictVariables(false)
                .build();
    }

    public String render(String templateBody, Map<String, Object> context) {
        try {
            PebbleTemplate compiled = engine.getTemplate(templateBody);
            Writer writer = new StringWriter();
            compiled.evaluate(writer, context);
            return writer.toString();
        } catch (IOException ex) {
            throw new PromptInputSpecException("prompt 렌더 실패: " + ex.getMessage());
        }
    }
}
