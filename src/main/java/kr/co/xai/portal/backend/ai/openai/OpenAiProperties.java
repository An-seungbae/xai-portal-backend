package kr.co.xai.portal.backend.ai.openai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "openai.api")
public class OpenAiProperties {

    private String key;
    private String url;
    private String model;
    private int maxTokens;
    private int timeoutSeconds;
}
