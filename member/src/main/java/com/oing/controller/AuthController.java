package com.oing.controller;

import com.oing.domain.CreateNewUserDTO;
import com.oing.domain.SocialLoginProvider;
import com.oing.domain.SocialLoginResult;
import com.oing.domain.TokenPair;
import com.oing.domain.exception.DomainException;
import com.oing.domain.exception.ErrorCode;
import com.oing.domain.model.Member;
import com.oing.dto.request.NativeSocialLoginRequest;
import com.oing.dto.request.RefreshAccessTokenRequest;
import com.oing.dto.response.AuthResultResponse;
import com.oing.restapi.AuthApi;
import com.oing.service.AuthService;
import com.oing.service.MemberService;
import com.oing.service.TokenGenerator;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

import java.util.Optional;

/**
 * no5ing-server
 * User: CChuYong
 * Date: 2023/11/27
 * Time: 11:04 AM
 */
@RequiredArgsConstructor
@Controller
public class AuthController implements AuthApi {
    private final AuthService authService;
    private final MemberService memberService;
    private final TokenGenerator tokenGenerator;

    @Transactional
    @Override
    public AuthResultResponse socialLogin(String provider, NativeSocialLoginRequest request) {
        // oAuth 로그인 검증 (Apple 등)
        SocialLoginProvider socialLoginProvider = SocialLoginProvider.valueOf(provider.toUpperCase());
        SocialLoginResult socialLoginResult = authService
                .authenticateFromProvider(socialLoginProvider, request.accessToken());

        // 위 결과에서 나온 identifier로 이미 있는 사용자인지 확인
        Optional<Member> member = memberService
                .findMemberBySocialMemberKey(socialLoginProvider, socialLoginResult.identifier());
        if(member.isEmpty()) {
            //회원가입이 안된 경우 임시 토큰 발행
            TokenPair temporaryTokenPair = tokenGenerator
                    .generateTemporaryTokenPair(socialLoginProvider, socialLoginResult.identifier());
            return AuthResultResponse.of(temporaryTokenPair, true);
        }

        //사용자로 토큰 생성
        TokenPair tokenPair = tokenGenerator.generateTokenPair(member.get().getId());
        return AuthResultResponse.of(tokenPair, false);
    }

    @Override
    public AuthResultResponse refreshAccessToken(RefreshAccessTokenRequest request) {
        //기존 리프레시 토큰 유효성 검증
        if (!tokenGenerator.isRefreshTokenValid(request.refreshToken()))
            throw new DomainException(ErrorCode.REFRESH_TOKEN_INVALID);

        //새 토큰 생성
        String userId = tokenGenerator.getUserIdFromAccessToken(request.refreshToken());
        TokenPair tokenPair = tokenGenerator.generateTokenPair(userId);
        return AuthResultResponse.of(tokenPair, false);
    }

    private Member createNewUser(SocialLoginProvider socialLoginProvider, String identifier) {
        CreateNewUserDTO createNewUserDTO = new CreateNewUserDTO(socialLoginProvider, identifier);
        return memberService.createNewMember(createNewUserDTO);
    }
}
