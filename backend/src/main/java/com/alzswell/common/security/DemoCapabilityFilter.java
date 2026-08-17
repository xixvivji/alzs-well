package com.alzswell.common.security;

import com.alzswell.demo.api.DemoErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class DemoCapabilityFilter extends OncePerRequestFilter {

    private static final Pattern SESSION_PATH = Pattern.compile(
            "^/api/v1/demo/sessions/([^/]+)(/.*)?$"
    );
    private static final String DEMO_ROOT = "/api/v1/demo/";

    private final DemoCapabilityService capabilityService;
    private final SecurityErrorWriter errorWriter;

    public DemoCapabilityFilter(
            DemoCapabilityService capabilityService,
            SecurityErrorWriter errorWriter
    ) {
        this.capabilityService = capabilityService;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || !request.getRequestURI().startsWith(DEMO_ROOT);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        if (hasAmbiguousPathEncoding(requestUri)) {
            errorWriter.write(response, DemoErrorCode.SESSION_NOT_FOUND);
            return;
        }

        Matcher matcher = SESSION_PATH.matcher(requestUri);
        if (!matcher.matches()) {
            filterChain.doFilter(request, response);
            return;
        }

        UUID sessionId;
        try {
            sessionId = UUID.fromString(matcher.group(1));
        } catch (IllegalArgumentException exception) {
            errorWriter.write(response, DemoErrorCode.SESSION_NOT_FOUND);
            return;
        }

        List<String> tailSegments = tailSegments(matcher.group(2));
        DemoCapabilityService.RequiredRole requiredRole =
                !tailSegments.isEmpty()
                                && ("staff".equals(tailSegments.getFirst())
                                || "cases".equals(tailSegments.getFirst()))
                        ? DemoCapabilityService.RequiredRole.STAFF
                        : DemoCapabilityService.RequiredRole.CUSTOMER;
        String token = request.getHeader(DemoCapabilityService.REQUEST_HEADER);
        DemoCapabilityService.Validation validation =
                capabilityService.validate(token, sessionId, requiredRole);
        if (validation.status() == DemoCapabilityService.ValidationStatus.NOT_FOUND) {
            errorWriter.write(response, DemoErrorCode.SESSION_NOT_FOUND);
            return;
        }
        if (validation.status() == DemoCapabilityService.ValidationStatus.SCOPE_FORBIDDEN) {
            errorWriter.write(response, DemoErrorCode.CAPABILITY_SCOPE_FORBIDDEN);
            return;
        }
        request.setAttribute(DemoCapabilityService.REQUEST_ROLE_ATTRIBUTE, validation.role().name());
        request.setAttribute(DemoCapabilityService.REQUEST_HASH_ATTRIBUTE, validation.capabilityHash());
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "demo:" + sessionId,
                null,
                List.of(new SimpleGrantedAuthority(validation.role().name()))
        ));
        SecurityContextHolder.setContext(securityContext);

        if (requiresCurrentRun(tailSegments)) {
            UUID requestedRunId;
            try {
                requestedRunId = UUID.fromString(request.getHeader(DemoCapabilityService.RUN_HEADER));
            } catch (IllegalArgumentException | NullPointerException exception) {
                errorWriter.write(response, DemoErrorCode.RUN_STALE);
                return;
            }
            if (!requestedRunId.equals(validation.currentDemoRunId())) {
                errorWriter.write(response, DemoErrorCode.RUN_STALE);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean hasAmbiguousPathEncoding(String requestUri) {
        return requestUri.indexOf('%') >= 0
                || requestUri.indexOf(';') >= 0
                || requestUri.indexOf('\\') >= 0
                || requestUri.contains("//")
                || requestUri.chars().anyMatch(character -> Character.isISOControl(character));
    }

    private List<String> tailSegments(String tail) {
        if (tail == null || tail.isBlank() || "/".equals(tail)) {
            return List.of();
        }
        return Arrays.stream(tail.substring(1).split("/"))
                .filter(segment -> !segment.isBlank())
                .toList();
    }

    private boolean requiresCurrentRun(List<String> tailSegments) {
        if (tailSegments.isEmpty() || tailSegments.equals(List.of("reset"))) {
            return false;
        }
        return !(tailSegments.size() == 3
                && "scenarios".equals(tailSegments.get(0))
                && "ingest".equals(tailSegments.get(2)));
    }
}
