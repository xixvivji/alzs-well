package com.alzswell.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Set;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    private static final String SESSION_ROOT = "/api/v1/demo/sessions/{sessionId}";
    private static final String STAFF_CAPABILITY_ISSUANCE =
            "/api/v1/demo/staff/sessions/{sessionId}/capability";
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
                .components(new Components()
                        .addSecuritySchemes("DemoCapability", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(CAPABILITY_HEADER)
                                .description("고객 생성 API 또는 인증된 직원 발급 API에서 받은 역할 capability"))
                        .addSecuritySchemes("DemoStaffBootstrap", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")
                                .description("직원 capability를 발급하기 위한 합성 staging 전용 직원 인증"))
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("opaque")
                                .description("로컬·사설 검증용 회전형 opaque access token. production에서는 기업 IdP adapter가 필요합니다.")));
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
        addContractMetadata(path, method, operation);
        if (path.startsWith(SESSION_ROOT)) {
            addHeader(operation, CAPABILITY_HEADER, true, "세션 역할 capability");
            operation.addSecurityItem(new SecurityRequirement().addList("DemoCapability"));
            if (!RUN_HEADER_EXCLUSIONS.contains(path)) {
                addHeader(operation, RUN_HEADER, true, "현재 demoRun UUID");
            }
        }
        if (STAFF_CAPABILITY_ISSUANCE.equals(path)) {
            operation.addSecurityItem(new SecurityRequirement().addList("DemoStaffBootstrap"));
        }

        if ((method == PathItem.HttpMethod.POST || method == PathItem.HttpMethod.PATCH)
                && requiresIdempotencyKey(path)) {
            addHeader(operation, IDEMPOTENCY_HEADER, true, "동일 요청의 중복 처리를 방지하는 키");
        }

        if (method == PathItem.HttpMethod.POST && "/api/v1/demo/sessions".equals(path)) {
            ApiResponse response = response(operation, "201", "데모 세션 생성");
            response.addHeaderObject("X-Demo-Customer-Capability", responseHeader("고객 역할 capability"));
        }
        if (method == PathItem.HttpMethod.POST && STAFF_CAPABILITY_ISSUANCE.equals(path)) {
            response(operation, "200", "인증된 직원 capability 발급")
                    .addHeaderObject("X-Demo-Staff-Capability", responseHeader("행원 역할 capability"));
        }
        if (method == PathItem.HttpMethod.POST && (path.endsWith("/reset") || path.endsWith("/ingest"))) {
            String status = path.endsWith("/ingest") ? "201" : "200";
            response(operation, status, "현재 demoRun 반환")
                    .addHeaderObject(RUN_HEADER, responseHeader("새로 활성화된 demoRun UUID"));
        }
    }

    private void addContractMetadata(String path, PathItem.HttpMethod method, Operation operation) {
        AccessContract access = accessContract(path, method);
        if (operation.getSummary() == null || operation.getSummary().isBlank()) {
            operation.setSummary(actionName(method) + " — " + path);
        }
        if (operation.getDescription() == null || operation.getDescription().isBlank()) {
            operation.setDescription("ALZ's well의 " + actionName(method)
                    + " 계약입니다. 합성 데이터 전용이며 실제 금융거래나 외부 연락을 실행하지 않습니다.");
        }
        operation.addExtension("x-alzs-authority-mode", access.mode());
        operation.addExtension("x-alzs-required-authorities", access.authorities());
        operation.addExtension("x-alzs-data-classification", "SYNTHETIC_ONLY");
        operation.addExtension("x-alzs-runtime-boundary", runtimeBoundary(path));
        operation.addExtension("x-alzs-external-action", "NEVER");

        if (access.bearer()) {
            operation.addSecurityItem(new SecurityRequirement().addList("BearerAuth"));
        }
        addStandardError(operation, "400", "COMMON_VALIDATION_FAILED", "요청 형식 또는 입력값이 올바르지 않습니다.");
        if (!"PUBLIC".equals(access.mode())) {
            addStandardError(operation, "401", "COMMON_UNAUTHORIZED", "인증 정보가 없거나 유효하지 않습니다.");
            addStandardError(operation, "403", "COMMON_FORBIDDEN", "요청한 자원에 접근할 권한이 없습니다.");
        }
        if ((method == PathItem.HttpMethod.POST && !path.equals("/api/v1/knowledge/search")) || method == PathItem.HttpMethod.PUT
                || method == PathItem.HttpMethod.PATCH || method == PathItem.HttpMethod.DELETE) {
            addStandardError(operation, "409", "COMMON_CONFLICT", "현재 자원 버전 또는 상태와 요청이 충돌합니다.");
        }
    }

    private AccessContract accessContract(String path, PathItem.HttpMethod method) {
        if (path.startsWith("/api/v1/system/") || "/api/v1/demo/scenarios".equals(path)
                || ("/api/v1/demo/sessions".equals(path) && method == PathItem.HttpMethod.POST)
                || path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/token/refresh")) {
            return new AccessContract("PUBLIC", List.of(), false);
        }
        if (STAFF_CAPABILITY_ISSUANCE.equals(path)) {
            return new AccessContract("STAFF_BOOTSTRAP", List.of("DEMO_STAFF_BOOTSTRAP"), false);
        }
        if (path.startsWith(SESSION_ROOT)) {
            return new AccessContract("DEMO_CAPABILITY", List.of("CUSTOMER or STAFF capability"), false);
        }
        if (path.startsWith("/api/v1/auth/")) {
            return new AccessContract("BEARER", List.of("AUTHENTICATED_SESSION"), true);
        }
        return new AccessContract("BEARER_AUTHORITY", authorities(path, method), true);
    }

    private List<String> authorities(String path, PathItem.HttpMethod method) {
        if (path.contains("/inbox") || path.endsWith("/notification-preferences")) {
            return List.of(method == PathItem.HttpMethod.GET ? "INBOX_READ" : "INBOX_WRITE");
        }
        if (path.equals("/api/v1/notification-previews")) return List.of("NOTIFICATION_PREVIEW");
        if (path.equals("/api/v1/knowledge/search")) return List.of("KNOWLEDGE_SEARCH");
        if (path.startsWith("/api/v1/knowledge/")) return List.of("KNOWLEDGE_READ");
        if (path.equals("/api/v1/guidance-candidates")) return List.of("GUIDANCE_CANDIDATE_READ");
        if (path.startsWith("/api/v1/staff/follow-ups/")) return List.of("STAFF_FOLLOW_UP");
        if (path.startsWith("/api/v1/staff/cases")) return staffCaseAuthorities(path, method);
        if (path.contains("/alerts")) {
            return List.of(method == PathItem.HttpMethod.GET ? "ALERT_READ or ALERT_READ_ALL"
                    : "ALERT_RESPOND or ALERT_RESPOND_ALL");
        }
        if (path.contains("/connections")) return List.of("FINANCIAL_CONNECTION_READ or FINANCIAL_CONNECTION_READ_ALL");
        if (path.startsWith("/api/v1/financial-institutions")) return List.of("AUTHENTICATED");
        if (path.contains("/baselines") || path.startsWith("/api/v1/signals")) {
            return List.of(method == PathItem.HttpMethod.POST ? "DETECTION_CALCULATE or DETECTION_CALCULATE_ALL"
                    : "DETECTION_READ or DETECTION_READ_ALL");
        }
        if (path.startsWith("/api/v1/synthetic-datasets")) return List.of("SYNTHETIC_DATASET_ADMIN");
        if (path.startsWith("/api/v1/detection-runs") && path.endsWith("/promote")) return List.of("DETECTION_PROMOTE");
        if (path.startsWith("/api/v1/detection-runs")) {
            return List.of(method == PathItem.HttpMethod.POST ? "DETECTION_RUN_CREATE" : "DETECTION_RUN_READ");
        }
        if (path.startsWith("/api/v1/detection-promotions")) return List.of("DETECTION_PROMOTION_READ");
        if (path.startsWith("/api/v1/customers/")) {
            return List.of(method == PathItem.HttpMethod.GET
                    ? "CUSTOMER_PROFILE_READ or CUSTOMER_PROFILE_READ_ALL"
                    : "CUSTOMER_PROFILE_WRITE or CUSTOMER_PROFILE_WRITE_ALL");
        }
        return List.of("AUTHENTICATED");
    }

    private List<String> staffCaseAuthorities(String path, PathItem.HttpMethod method) {
        if (path.endsWith("/assignment")) return List.of("STAFF_CASE_ASSIGN");
        if (path.endsWith("/reviews")) return List.of("STAFF_CASE_REVIEW");
        if (path.endsWith("/guidance-plans")) return List.of("STAFF_GUIDANCE_APPROVE");
        if (path.endsWith("/notes") && method == PathItem.HttpMethod.POST) return List.of("STAFF_CASE_NOTE");
        if (path.endsWith("/follow-ups") && method == PathItem.HttpMethod.POST) return List.of("STAFF_FOLLOW_UP");
        return List.of("STAFF_CASE_READ");
    }

    private String runtimeBoundary(String path) {
        return path.startsWith("/api/v1/financial-institutions") || path.contains("/connections")
                ? "SYNTHETIC_EXTERNAL_ADAPTER" : "INTERNAL_OWNED";
    }

    private String actionName(PathItem.HttpMethod method) {
        return switch (method) {
            case GET -> "조회";
            case POST -> "생성 또는 상태 처리";
            case PUT -> "전체 설정 변경";
            case PATCH -> "부분 상태 변경";
            case DELETE -> "삭제 또는 폐기";
            default -> "API 처리";
        };
    }

    private void addStandardError(Operation operation, String status, String code, String message) {
        ApiResponse response = operation.getResponses().computeIfAbsent(status,
                ignored -> new ApiResponse().description(message));
        if (response.getContent() == null) {
            response.setContent(new Content().addMediaType("application/json", new MediaType()
                    .schema(new ObjectSchema())
                    .example(Map.of(
                            "success", false,
                            "code", code,
                            "message", message,
                            "data", Map.of(),
                            "errors", List.of(),
                            "traceId", "trace-example-0001"
                    ))));
        }
    }

    private record AccessContract(String mode, List<String> authorities, boolean bearer) {}

    private boolean requiresIdempotencyKey(String path) {
        return path.endsWith("/reset")
                || path.endsWith("/ingest")
                || path.endsWith("/context")
                || path.endsWith("/notes")
                || path.endsWith("/follow-ups")
                || path.contains("/follow-ups/")
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
