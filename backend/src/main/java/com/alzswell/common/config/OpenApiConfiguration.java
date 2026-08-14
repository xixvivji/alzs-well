package com.alzswell.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    private static final String SESSION_ROOT = "/api/v1/demo/sessions/{sessionId}";
    private static final String CAPABILITY_HEADER = "X-Demo-Capability";
    private static final String RUN_HEADER = "X-Demo-Run-Id";
    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final Set<String> RUN_HEADER_EXCLUSIONS = Set.of(
            SESSION_ROOT,
            SESSION_ROOT + "/reset",
            SESSION_ROOT + "/scenarios/{scenarioId}/ingest"
    );

    @Bean
    OpenAPI alzsWellOpenApi() {
        return new OpenAPI()
                .openapi("3.1.0")
                .info(new Info()
                        .title("ALZ's well P0 API")
                        .version("v1")
                        .description("합성 데이터 전용 금융생활 변화 알림 및 행원 보호업무 데모 API. 외부 전송과 실제 금융 실행은 지원하지 않습니다."))
                .components(new Components().addSecuritySchemes("DemoCapability",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(CAPABILITY_HEADER)
                                .description("세션 생성 응답으로 발급된 customer 또는 staff 역할 capability")));
    }

    @Bean
    GroupedOpenApi p0OpenApi(OpenApiCustomizer contractHeaders) {
        return GroupedOpenApi.builder()
                .group("p0")
                .pathsToMatch("/api/v1/**")
                .addOpenApiCustomizer(contractHeaders)
                .build();
    }

    @Bean
    OpenApiCustomizer contractHeaders() {
        return openApi -> openApi.getPaths().forEach((path, pathItem) ->
                pathItem.readOperationsMap().forEach((method, operation) -> customize(path, method, operation)));
    }

    private void customize(String path, PathItem.HttpMethod method, Operation operation) {
        if (path.startsWith(SESSION_ROOT)) {
            addHeader(operation, CAPABILITY_HEADER, true, "세션 역할 capability");
            operation.addSecurityItem(new SecurityRequirement().addList("DemoCapability"));
            if (!RUN_HEADER_EXCLUSIONS.contains(path)) {
                addHeader(operation, RUN_HEADER, true, "현재 demoRun UUID");
            }
        }

        if (method == PathItem.HttpMethod.POST && requiresIdempotencyKey(path)) {
            addHeader(operation, IDEMPOTENCY_HEADER, true, "동일 요청의 중복 처리를 방지하는 키");
        }

        if (method == PathItem.HttpMethod.POST && "/api/v1/demo/sessions".equals(path)) {
            ApiResponse response = response(operation, "201", "데모 세션 생성");
            response.addHeaderObject("X-Demo-Customer-Capability", responseHeader("고객 역할 capability"));
            response.addHeaderObject("X-Demo-Staff-Capability", responseHeader("행원 역할 capability"));
        }
        if (method == PathItem.HttpMethod.POST && (path.endsWith("/reset") || path.endsWith("/ingest"))) {
            String status = path.endsWith("/ingest") ? "201" : "200";
            response(operation, status, "현재 demoRun 반환")
                    .addHeaderObject(RUN_HEADER, responseHeader("새로 활성화된 demoRun UUID"));
        }
    }

    private boolean requiresIdempotencyKey(String path) {
        return path.endsWith("/reset")
                || path.endsWith("/ingest")
                || path.endsWith("/context")
                || path.endsWith("/review")
                || path.endsWith("/guidance-plan");
    }

    private void addHeader(Operation operation, String name, boolean required, String description) {
        boolean alreadyPresent = operation.getParameters() != null
                && operation.getParameters().stream().anyMatch(parameter -> name.equals(parameter.getName()));
        if (!alreadyPresent) {
            Parameter parameter = new HeaderParameter()
                    .name(name)
                    .required(required)
                    .description(description)
                    .schema(new StringSchema());
            operation.addParametersItem(parameter);
        }
    }

    private ApiResponse response(Operation operation, String status, String description) {
        ApiResponses responses = operation.getResponses();
        ApiResponse response = responses.computeIfAbsent(status, ignored -> new ApiResponse().description(description));
        if (response.getDescription() == null) {
            response.setDescription(description);
        }
        return response;
    }

    private Header responseHeader(String description) {
        return new Header().description(description).schema(new StringSchema());
    }
}
