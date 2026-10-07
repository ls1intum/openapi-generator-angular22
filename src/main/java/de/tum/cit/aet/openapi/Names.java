/*
 * Copyright (c) 2024 TUM Applied Education Technologies (AET)
 * Licensed under the MIT License
 */
package de.tum.cit.aet.openapi;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Case conversions for the file, class and identifier names the generator derives from the OpenAPI document. */
final class Names {

    /**
     * Converts a PascalCase or camelCase string to kebab-case.
     *
     * @param name the input string (e.g., {@code "JobDetailDTO"})
     * @return the kebab-case result (e.g., {@code "job-detail-dto"})
     */
    static String toKebabCase(String name) {
        return name.replaceAll("([a-z])([A-Z])", "$1-$2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1-$2")
                .replaceAll("_", "-")
                .toLowerCase();
    }

    /**
     * Converts a snake_case or kebab-case string to camelCase.
     *
     * @param name the input string (e.g., {@code "job_id"} or {@code "job-id"})
     * @return the camelCase result (e.g., {@code "jobId"}), or the input unchanged
     *         if it is {@code null} or empty
     */
    static String toCamelCase(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        Pattern pattern = Pattern.compile("[-_]([a-zA-Z0-9])");
        Matcher matcher = pattern.matcher(name);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, matcher.group(1).toUpperCase());
        }
        matcher.appendTail(buffer);
        String result = buffer.toString();
        return Character.toLowerCase(result.charAt(0)) + result.substring(1);
    }

    /**
     * Converts a string to PascalCase by capitalizing the first letter of the camelCase result.
     *
     * @param name the input string (e.g., {@code "get_jobs"})
     * @return the PascalCase result (e.g., {@code "GetJobs"}), or the input unchanged
     *         if it is {@code null} or empty
     */
    static String toPascalCase(String name) {
        String camel = toCamelCase(name);
        if (camel == null || camel.isEmpty()) {
            return camel;
        }
        return Character.toUpperCase(camel.charAt(0)) + camel.substring(1);
    }

    private Names() {
    }
}
