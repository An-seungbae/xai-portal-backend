package kr.co.xai.portal.backend;

import kr.co.xai.portal.backend.ai.openai.OpenAiProperties;
import kr.co.xai.portal.backend.config.JwtProperties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication
@EnableConfigurationProperties({
        OpenAiProperties.class,
        JwtProperties.class
})
public class XaiPortalBackendApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(XaiPortalBackendApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(XaiPortalBackendApplication.class);
    }
}
