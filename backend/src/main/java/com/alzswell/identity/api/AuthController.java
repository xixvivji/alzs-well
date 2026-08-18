package com.alzswell.identity.api;

import com.alzswell.common.api.ApiResponse;
import com.alzswell.common.api.ApiResponses;
import com.alzswell.identity.api.AuthRequests.LoginCommand;
import com.alzswell.identity.api.AuthRequests.RefreshCommand;
import com.alzswell.identity.api.AuthResponses.CurrentUser;
import com.alzswell.identity.api.AuthResponses.PermissionList;
import com.alzswell.identity.api.AuthResponses.TokenPair;
import com.alzswell.identity.application.AuthSessionService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name = "app.features.local-auth-api-enabled", havingValue = "true")
public class AuthController {
    private final AuthSessionService authSessionService;

    public AuthController(AuthSessionService authSessionService) {
        this.authSessionService = authSessionService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenPair>> login(@Valid @RequestBody LoginCommand request) {
        return ApiResponses.ok("AUTH_LOGIN_SUCCEEDED", "로그인했습니다.",
                authSessionService.login(request.loginId(), request.password()));
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<ApiResponse<TokenPair>> refresh(@Valid @RequestBody RefreshCommand request) {
        return ApiResponses.ok("AUTH_TOKEN_REFRESHED", "인증 토큰을 갱신했습니다.",
                authSessionService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(Authentication authentication) {
        authSessionService.logout(authentication);
        return ApiResponses.ok("AUTH_LOGOUT_SUCCEEDED", "로그아웃했습니다.", null);
    }

    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAll(Authentication authentication) {
        authSessionService.logoutAll(authentication);
        return ApiResponses.ok("AUTH_LOGOUT_ALL_SUCCEEDED", "모든 인증 세션에서 로그아웃했습니다.", null);
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CurrentUser>> me(Authentication authentication) {
        return ApiResponses.ok("AUTH_CURRENT_USER_RETRIEVED", "현재 인증 주체를 조회했습니다.",
                authSessionService.currentUser(authentication));
    }

    @GetMapping("/me/permissions")
    public ResponseEntity<ApiResponse<PermissionList>> permissions(Authentication authentication) {
        return ApiResponses.ok("AUTH_PERMISSIONS_RETRIEVED", "현재 권한을 조회했습니다.",
                authSessionService.permissions(authentication));
    }
}
