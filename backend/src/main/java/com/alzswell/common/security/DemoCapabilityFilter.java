package com.alzswell.common.security;

import com.alzswell.demo.api.DemoErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class DemoCapabilityFilter extends OncePerRequestFilter {

    private static final Pattern SESSION_PATH = Pattern.compile(
            "^/api/v1/demo/sessions/([^/]+)(?:/.*)?$"
    );

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
                || !SESSION_PATH.matcher(request.getRequestURI()).matches();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Matcher matcher = SESSION_PATH.matcher(request.getRequestURI());
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

        String path = request.getRequestURI();
        DemoCapabilityService.RequiredRole requiredRole =
                path.contains("/staff/") || path.contains("/cases/")
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

        if (requiresCurrentRun(path)) {
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

    private boolean requiresCurrentRun(String path) {
        return !path.matches("^/api/v1/demo/sessions/[^/]+$")
                && !path.matches("^/api/v1/demo/sessions/[^/]+/reset$")
                && !path.matches("^/api/v1/demo/sessions/[^/]+/scenarios/[^/]+/ingest$");
    }
}
