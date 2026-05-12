package work.jscraft.alt.auth.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import work.jscraft.alt.common.error.ApiErrorResponse;

@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    static final String CLEAR_SESSION_COOKIE_ATTRIBUTE = JsonAuthenticationEntryPoint.class.getName() + ".clearCookie";

    private final ObjectMapper objectMapper;
    private final AuthCookieFactory authCookieFactory;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper, AuthCookieFactory authCookieFactory) {
        this.objectMapper = objectMapper;
        this.authCookieFactory = authCookieFactory;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        if (Boolean.TRUE.equals(request.getAttribute(CLEAR_SESSION_COOKIE_ATTRIBUTE))) {
            response.addHeader(HttpHeaders.SET_COOKIE, authCookieFactory.expireSessionCookie().toString());
        }

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiErrorResponse.of("UNAUTHORIZED", "인증이 필요합니다."));
    }
}
