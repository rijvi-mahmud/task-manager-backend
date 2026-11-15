package com.example.backend.support;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import jakarta.servlet.http.Cookie;

/**
 * A minimal cookie-keeping client over MockMvc that behaves like the SPA:
 * it stores cookies from responses, sends them back, and echoes the
 * XSRF-TOKEN cookie in the X-XSRF-TOKEN header on unsafe requests. Used to
 * test the real session and CSRF flow end to end.
 */
public class Browser {

    private final MockMvc mvc;
    private final Map<String, Cookie> cookies = new LinkedHashMap<>();

    public Browser(MockMvc mvc) {
        this.mvc = mvc;
    }

    public MvcResult get(String url) throws Exception {
        return send(MockMvcRequestBuilders.get(url), false);
    }

    public MvcResult post(String url, String json) throws Exception {
        return send(MockMvcRequestBuilders.request(HttpMethod.POST, url)
                .contentType(MediaType.APPLICATION_JSON).content(json), true);
    }

    /** Sends a write without the CSRF header, as a forged cross-site request would. */
    public MvcResult postWithoutCsrfHeader(String url, String json) throws Exception {
        return send(MockMvcRequestBuilders.request(HttpMethod.POST, url)
                .contentType(MediaType.APPLICATION_JSON).content(json), false);
    }

    public String cookie(String name) {
        Cookie cookie = cookies.get(name);
        return cookie == null ? null : cookie.getValue();
    }

    private MvcResult send(MockHttpServletRequestBuilder request, boolean withCsrfHeader) throws Exception {
        if (withCsrfHeader && !cookies.containsKey("XSRF-TOKEN")) {
            send(MockMvcRequestBuilders.get("/api/v1/auth/csrf"), false);
        }
        if (!cookies.isEmpty()) {
            request.cookie(cookies.values().toArray(Cookie[]::new));
        }
        if (withCsrfHeader) {
            request.header("X-XSRF-TOKEN", cookie("XSRF-TOKEN"));
        }
        MvcResult result = mvc.perform(request).andReturn();
        for (Cookie cookie : result.getResponse().getCookies()) {
            if (cookie.getMaxAge() == 0 || cookie.getValue() == null || cookie.getValue().isEmpty()) {
                cookies.remove(cookie.getName());
            } else {
                cookies.put(cookie.getName(), cookie);
            }
        }
        return result;
    }
}
