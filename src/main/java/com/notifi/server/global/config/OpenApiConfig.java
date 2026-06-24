package com.notifi.server.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String JWT_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("NotiFi API")
                        .description("카메라·웨어러블 없는 노인 Safety Agent — 보호자 앱 API")
                        .version("v0.1"))
                .addSecurityItem(new SecurityRequirement().addList(JWT_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(JWT_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    @Bean
    public ModelResolver modelResolver() {
        // swagger-core는 Jackson 2(com.fasterxml.jackson)를 사용한다. 앱 런타임의 Jackson 3(tools.jackson)과 별개로
        // 여기서 Jackson 2 매퍼에 SNAKE_CASE를 지정 → Swagger 스키마 필드명을 실제 API 계약(snake_case)과 일치시킴
        ObjectMapper swaggerMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        return new ModelResolver(swaggerMapper);
    }
}
