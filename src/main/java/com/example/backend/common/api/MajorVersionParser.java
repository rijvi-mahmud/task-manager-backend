package com.example.backend.common.api;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.accept.ApiVersionParser;
import org.springframework.web.accept.InvalidApiVersionException;

/**
 * Accepts only {@code v<major>} (e.g. {@code v1}). Spring's default parser is
 * lenient and would also accept {@code 1} or {@code x1}; a public URI should
 * have exactly one canonical form.
 */
public class MajorVersionParser implements ApiVersionParser<Integer> {

    private static final Pattern FORMAT = Pattern.compile("v([1-9][0-9]{0,2})");

    @Override
    public Integer parseVersion(String version) {
        Matcher matcher = FORMAT.matcher(version);
        if (!matcher.matches()) {
            throw new InvalidApiVersionException(version);
        }
        return Integer.valueOf(matcher.group(1));
    }
}
