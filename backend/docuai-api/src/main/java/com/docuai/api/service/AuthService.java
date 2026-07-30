package com.docuai.api.service;

import com.docuai.api.dto.UserDTO;
import com.docuai.api.dto.auth.JwtResponse;
import com.docuai.api.exception.BusinessException;
import com.docuai.api.mapper.UserMapper;
import com.docuai.security.jwt.JwtTokenProvider;
import com.docuai.security.jwt.TokenBlacklistService;
import com.docuai.security.service.UserDetailsImpl;
import com.docuai.security.service.UserDetailsServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service applicatif d'authentification — extrait d'AuthController pour
 * respecter la règle "thin controllers" de la section 4.1 (aucune logique
 * métier dans les contrôleurs).
 */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserDetailsServiceImpl userDetailsService;
    private final UserMapper userMapper;

    public AuthService(AuthenticationManager authenticationManager,
                        JwtTokenProvider tokenProvider,
                        TokenBlacklistService tokenBlacklistService,
                        UserDetailsServiceImpl userDetailsService,
                        UserMapper userMapper) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.tokenBlacklistService = tokenBlacklistService;
        this.userDetailsService = userDetailsService;
        this.userMapper = userMapper;
    }

    public JwtResponse login(String email, String password) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        return buildJwtResponse(userDetails, authentication);
    }

    public JwtResponse refresh(String refreshToken) {
        if (!tokenProvider.validateToken(refreshToken) || !tokenProvider.isRefreshToken(refreshToken)) {
            throw new BusinessException("INVALID_REFRESH_TOKEN", "Jeton de rafraîchissement invalide ou expiré.", HttpStatus.UNAUTHORIZED);
        }
        String username = tokenProvider.getUsernameFromJWT(refreshToken);
        UserDetailsImpl userDetails = (UserDetailsImpl) userDetailsService.loadUserByUsername(username);
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        // Rotation : l'ancien refresh token est immédiatement révoqué pour
        // limiter la fenêtre de rejeu en cas de vol du jeton.
        tokenBlacklistService.blacklist(refreshToken, tokenProvider.getRemainingValidity(refreshToken));

        return buildJwtResponse(userDetails, authentication);
    }

    public void logout(String refreshToken) {
        Duration remaining = tokenProvider.getRemainingValidity(refreshToken);
        tokenBlacklistService.blacklist(refreshToken, remaining);
        SecurityContextHolder.clearContext();
    }

    private JwtResponse buildJwtResponse(UserDetailsImpl userDetails, Authentication authentication) {
        String accessToken = tokenProvider.generateAccessToken(authentication);
        String refreshToken = tokenProvider.generateRefreshToken(authentication);

        List<String> authorities = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
        List<String> roles = authorities.stream().filter(a -> a.startsWith("ROLE_")).collect(Collectors.toList());
        List<String> permissions = authorities.stream().filter(a -> !a.startsWith("ROLE_")).collect(Collectors.toList());

        UserDTO userDTO = userMapper.toDto(userDetails.getUtilisateur());
        return new JwtResponse(accessToken, refreshToken, userDetails.getUsername(), roles, permissions, userDTO);
    }
}
