package com.example.backend.common.api;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import com.example.backend.common.observability.RequestLoggingFilter;

/** One contract for application JSON, preserving RFC 9457 and bodyless HTTP responses. */
@RestControllerAdvice(basePackages = "com.example.backend")
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)
                || !(response instanceof ServletServerHttpResponse servletResponse)
                || !servletRequest.getServletRequest().getRequestURI().startsWith(
                        servletRequest.getServletRequest().getContextPath() + "/api/")) return body;
        ResponseMetadata metadata = ResponseMetadata.from(servletRequest.getServletRequest());
        response.getHeaders().set(RequestLoggingFilter.HEADER, metadata.requestId());
        if (body instanceof ProblemDetail problem) {
            problem.setProperty("requestId", metadata.requestId());
            problem.setProperty("timestamp", metadata.timestamp());
            return problem;
        }
        if (body == null || body instanceof ApiResponse<?> || servletResponse.getServletResponse().getStatus() >= 400
                || servletResponse.getServletResponse().getStatus() == 204) return body;
        return new ApiResponse<>(body, metadata);
    }
}
