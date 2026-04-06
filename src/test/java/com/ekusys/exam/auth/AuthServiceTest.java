package com.ekusys.exam.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ekusys.exam.auth.dto.AuthResponse;
import com.ekusys.exam.auth.dto.LoginRequest;
import com.ekusys.exam.auth.service.AuthService;
import com.ekusys.exam.auth.service.RefreshTokenSessionService;
import com.ekusys.exam.common.exception.BusinessException;
import com.ekusys.exam.common.security.JwtTokenProvider;
import com.ekusys.exam.common.security.LoginUser;
import com.ekusys.exam.repository.entity.User;
import com.ekusys.exam.repository.mapper.UserMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserMapper userMapper;

    @Mock
    private RefreshTokenSessionService refreshTokenSessionService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(authenticationManager, jwtTokenProvider, userMapper, refreshTokenSessionService);
    }

    @Test
    void loginShouldStoreRefreshSession() {
        LoginUser loginUser = LoginUser.builder()
            .userId(1001L)
            .username("alice")
            .enabled(true)
            .roles(List.of("STUDENT"))
            .build();
        Authentication authentication = new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtTokenProvider.createAccessToken(loginUser)).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(eq(loginUser), any())).thenReturn("refresh-token");
        when(jwtTokenProvider.getExpiration("refresh-token")).thenReturn(Instant.parse("2026-04-05T00:00:00Z"));

        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("secret");
        AuthResponse response = authService.login(request);

        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        verify(refreshTokenSessionService).store(eq(1001L), any(), eq(Instant.parse("2026-04-05T00:00:00Z")));
    }

    @Test
    void refreshShouldRejectInactiveSession() {
        LoginUser tokenUser = LoginUser.builder()
            .userId(1001L)
            .username("alice")
            .roles(List.of("STUDENT"))
            .enabled(true)
            .build();
        when(jwtTokenProvider.isRefreshToken("refresh-token")).thenReturn(true);
        when(jwtTokenProvider.parseLoginUser("refresh-token")).thenReturn(tokenUser);
        when(jwtTokenProvider.getTokenId("refresh-token")).thenReturn("jti-1");
        when(refreshTokenSessionService.isActive(1001L, "jti-1")).thenReturn(false);

        assertThrows(BusinessException.class, () -> authService.refresh("refresh-token"));
    }

    @Test
    void refreshShouldReloadUserAndRotateToken() {
        LoginUser tokenUser = LoginUser.builder()
            .userId(1001L)
            .username("alice")
            .roles(List.of("STUDENT"))
            .enabled(true)
            .build();
        User user = new User();
        user.setId(1001L);
        user.setUsername("alice");
        user.setEnabled(true);

        when(jwtTokenProvider.isRefreshToken("refresh-token")).thenReturn(true);
        when(jwtTokenProvider.parseLoginUser("refresh-token")).thenReturn(tokenUser);
        when(jwtTokenProvider.getTokenId("refresh-token")).thenReturn("jti-1");
        when(refreshTokenSessionService.isActive(1001L, "jti-1")).thenReturn(true);
        when(userMapper.selectById(1001L)).thenReturn(user);
        when(userMapper.selectRoleCodes(1001L)).thenReturn(List.of("STUDENT", "ADMIN"));
        when(jwtTokenProvider.createAccessToken(any(LoginUser.class))).thenReturn("new-access-token");
        when(jwtTokenProvider.createRefreshToken(any(LoginUser.class), any())).thenReturn("new-refresh-token");
        when(jwtTokenProvider.getExpiration("new-refresh-token")).thenReturn(Instant.parse("2026-04-06T00:00:00Z"));

        AuthResponse response = authService.refresh("refresh-token");

        assertEquals("new-access-token", response.getAccessToken());
        assertEquals(List.of("STUDENT", "ADMIN"), response.getRoles());
        verify(refreshTokenSessionService).store(eq(1001L), any(), eq(Instant.parse("2026-04-06T00:00:00Z")));
    }
}
