package com.ekusys.exam.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ekusys.exam.auth.dto.AuthResponse;
import com.ekusys.exam.auth.dto.LoginRequest;
import com.ekusys.exam.auth.dto.MeResponse;
import com.ekusys.exam.common.exception.BusinessException;
import com.ekusys.exam.common.security.JwtTokenProvider;
import com.ekusys.exam.common.security.LoginUser;
import com.ekusys.exam.common.security.SecurityUtils;
import com.ekusys.exam.repository.entity.User;
import com.ekusys.exam.repository.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;

    public AuthService(AuthenticationManager authenticationManager,
                       JwtTokenProvider jwtTokenProvider,
                       UserMapper userMapper) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userMapper = userMapper;
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        LoginUser user = (LoginUser) authentication.getPrincipal();
        log.info("User login success: userId={}, username={}", user.getUserId(), user.getUsername());
        return AuthResponse.builder()
            .accessToken(jwtTokenProvider.createAccessToken(user))
            .refreshToken(jwtTokenProvider.createRefreshToken(user))
            .tokenType("Bearer")
            .roles(user.getRoles())
            .build();
    }

    public AuthResponse refresh(String refreshToken) {
        if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new BusinessException("无效的刷新令牌");
        }
        LoginUser user = jwtTokenProvider.parseLoginUser(refreshToken);
        log.info("Refresh token success: userId={}, username={}", user.getUserId(), user.getUsername());
        return AuthResponse.builder()
            .accessToken(jwtTokenProvider.createAccessToken(user))
            .refreshToken(jwtTokenProvider.createRefreshToken(user))
            .tokenType("Bearer")
            .roles(user.getRoles())
            .build();
    }

    public MeResponse me() {
        LoginUser current = SecurityUtils.getCurrentUser();
        if (current == null) {
            throw new BusinessException("未登录");
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getId, current.getUserId()));
        return MeResponse.builder()
            .userId(current.getUserId())
            .username(current.getUsername())
            .realName(user == null ? current.getUsername() : user.getRealName())
            .roles(current.getRoles())
            .build();
    }
}
