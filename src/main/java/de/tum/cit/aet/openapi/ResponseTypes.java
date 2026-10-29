/*
 * Copyright (c) 2024 TUM Applied Education Technologies (AET)
 * Licensed under the MIT License
 */
package de.tum.cit.aet.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.utils.ModelUtils;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** The effective response type and its nullable JSON body-mode type, shared by services and resources. */
record ResponseTypes(String effective, String body) {
    private static final String NULLABLE = "x-nullable-json-success";
    private static final String BODY_TYPE = "x-json-success-body-type";
    private static final String NULLABLE_UNION = "x-json-success-nullable-union";

    /**
     * Called after the parent's fromOperation: normalization and inline resolution have already run. In particular,
     * a single-model-plus-null union can now be a nullable reference, whose sibling nullable flag must be read
     * before dereferencing. Keep this evidence on this operation, without modifying schemas or model types.
     */
    static void capture(OpenAPI api, Operation source, CodegenOperation op, ApiResponse selected) {
        boolean bodyless = false;
        boolean nullable = false;
        boolean jsonBody = false;
        String firstBodyCode = null;
        if (source.getResponses() != null) {
            for (var entry : source.getResponses().entrySet()) {
                if (!entry.getKey().startsWith("2")) {
                    continue;
                }
                ApiResponse response = resolveResponse(api, entry.getValue());
                if (response == null) {
                    continue;
                }
                bodyless |= isBodyless(response);
                Schema<?> schema = jsonSchema(response);
                if (schema != null) {
                    jsonBody = true;
                    // Preserve null evidence from all successes, without forming a union of their DTO body types.
                    nullable |= allowsNull(api, schema, identities());
                    if (firstBodyCode == null || entry.getKey().compareTo(firstBodyCode) < 0) {
                        firstBodyCode = entry.getKey();
                    }
                }
            }
        }
        op.vendorExtensions.put(NULLABLE, jsonBody && (bodyless || nullable));

        ApiResponse selectedResponse = resolveResponse(api, selected);
        Schema<?> selectedSchema = jsonSchema(selectedResponse);
        op.vendorExtensions.put(NULLABLE_UNION, renderedNullableUnion(api, selectedSchema, identities()));
        if (op.returnType == null && isBodyless(selectedResponse) && firstBodyCode != null) {
            // The lowest success can itself be bodyless. Reuse the core's already named body type/imports.
            String code = firstBodyCode;
            String type = op.responses.stream()
                    .filter(response -> code.equals(response.code)).map(response -> response.dataType)
                    .filter(value -> value != null).findFirst().orElse(null);
            if (type != null) {
                op.vendorExtensions.put(BODY_TYPE, type);
                op.vendorExtensions.put(NULLABLE_UNION, renderedNullableUnion(api,
                        jsonSchema(resolveResponse(api, source.getResponses().get(code))), identities()));
            }
        }
    }

    private static boolean isBodyless(ApiResponse response) {
        return response != null && (response.getContent() == null || response.getContent().isEmpty());
    }

    static ResponseTypes of(CodegenOperation op, TypeScriptSnippets.ResponseKind kind, String fallback) {
        String effective = op.returnType != null ? op.returnType : fallback;
        if (kind != TypeScriptSnippets.ResponseKind.JSON) {
            return new ResponseTypes(effective, effective);
        }
        effective = (String) op.vendorExtensions.getOrDefault(BODY_TYPE, effective);
        String body = Boolean.TRUE.equals(op.vendorExtensions.get(NULLABLE)) && !"void".equals(effective)
                && !Boolean.TRUE.equals(op.vendorExtensions.get(NULLABLE_UNION))
                && !hasNullMember(effective) ? effective + " | null" : effective;
        return new ResponseTypes(effective, body);
    }

    private static Schema<?> jsonSchema(ApiResponse response) {
        if (response == null || response.getContent() == null || response.getContent().isEmpty()) {
            return null;
        }
        // Match the core's first-content selection; do not change text/binary overload selection.
        var entry = response.getContent().entrySet().iterator().next();
        return TypeScriptSnippets.jsonMediaType(entry.getKey()) != null && entry.getValue() != null
                ? entry.getValue().getSchema() : null;
    }

    private static ApiResponse resolveResponse(OpenAPI api, ApiResponse response) {
        Set<ApiResponse> visited = identities();
        while (response != null && response.get$ref() != null) {
            String prefix = "#/components/responses/";
            if (!visited.add(response) || !response.get$ref().startsWith(prefix)
                    || api.getComponents() == null || api.getComponents().getResponses() == null) {
                return null;
            }
            String name = response.get$ref().substring(prefix.length()).replace("~1", "/").replace("~0", "~");
            response = api.getComponents().getResponses().get(name);
        }
        return response;
    }

    /** Root evidence only: nullable properties, items and map values do not make their container nullable. */
    private static boolean allowsNull(OpenAPI api, Schema<?> schema, Set<Schema<?>> active) {
        if (schema == null || !active.add(schema)) {
            return false;
        }
        try {
            if (schema.get$ref() != null) {
                Schema<?> target = referencedSchema(api, schema);
                return target != null && (Boolean.TRUE.equals(schema.getNullable()) || allowsNull(api, target, active));
            }
            if (Boolean.TRUE.equals(schema.getNullable()) || "null".equals(schema.getType())
                    || schema.getTypes() != null && schema.getTypes().contains("null")) {
                return true;
            }
            // A non-null local type constrains the entire composition, not just its object properties.
            if (schema.getType() != null || schema.getTypes() != null && !schema.getTypes().isEmpty()) {
                return false;
            }
            if (schema.getAllOf() != null && !schema.getAllOf().isEmpty()) {
                return schema.getAllOf().stream().allMatch(branch -> allowsNull(api, branch, active));
            }
            if (schema.getOneOf() != null && !schema.getOneOf().isEmpty()) {
                return schema.getOneOf().stream().filter(branch -> allowsNull(api, branch, active)).count() == 1;
            }
            if (schema.getAnyOf() != null && !schema.getAnyOf().isEmpty()) {
                return schema.getAnyOf().stream().anyMatch(branch -> allowsNull(api, branch, active));
            }
            return schema.getEnum() != null && schema.getEnum().contains(null);
        } finally {
            active.remove(schema);
        }
    }

    /** The existing oneOf model renderer already includes null in these aliases. Single-model copies do not. */
    private static boolean renderedNullableUnion(OpenAPI api, Schema<?> schema, Set<Schema<?>> active) {
        if (schema == null || !active.add(schema)) {
            return false;
        }
        if (schema.get$ref() != null) {
            return renderedNullableUnion(api, referencedSchema(api, schema), active);
        }
        return schema.getOneOf() != null && schema.getOneOf().size() > 1 && Boolean.TRUE.equals(schema.getNullable());
    }

    private static Schema<?> referencedSchema(OpenAPI api, Schema<?> schema) {
        // ModelUtils also falls back to a component's last path segment for unsupported/invalid pointers.
        // Do not turn such a fallback into evidence that a response may be null.
        if (!schema.get$ref().matches("#/components/schemas/[^/]+(?:/properties/[^/]+)?")) {
            return null;
        }
        Schema<?> target = ModelUtils.getReferencedSchema(api, schema);
        return target == schema ? null : target;
    }

    private static <T> Set<T> identities() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    /**
     * Schema metadata decides nullability and identifies nullable model aliases above. Custom type mappings are
     * opaque TypeScript expressions, so this small delimiter/quote scan only avoids appending a duplicate top-level
     * null member. It does not infer response nullability from text or mistake Array<Item | null> for a nullable root.
     */
    private static boolean hasNullMember(String type) {
        String expression = type.trim();
        if ("null".equals(expression)) {
            return true;
        }
        int depth = 0;
        char quote = 0;
        boolean parenthesized = expression.startsWith("(");
        for (int i = 0; i < expression.length(); i++) {
            char ch = expression.charAt(i);
            if (quote != 0) {
                if (ch == '\\') {
                    i++;
                } else if (ch == quote) {
                    quote = 0;
                }
                continue;
            }
            switch (ch) {
                case '\'', '"', '`' -> quote = ch;
                case '(', '[', '{', '<' -> depth++;
                case ')', ']', '}', '>' -> {
                    depth--;
                    if (depth == 0 && i < expression.length() - 1) {
                        parenthesized = false;
                    }
                }
                case '|' -> {
                    if (depth == 0) {
                        return hasNullMember(expression.substring(0, i)) || hasNullMember(expression.substring(i + 1));
                    }
                }
                default -> { }
            }
        }
        return parenthesized && expression.endsWith(")") && hasNullMember(expression.substring(1, expression.length() - 1));
    }
}
