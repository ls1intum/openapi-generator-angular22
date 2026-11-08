/*
 * Copyright (c) 2024 TUM Applied Education Technologies (AET)
 * Licensed under the MIT License
 */
package de.tum.cit.aet.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.utils.ModelUtils;

import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

/** The effective response type and its nullable JSON body-mode type, shared by services and resources. */
record ResponseTypes(String effective, String body) {
    private static final String NULLABLE = "x-nullable-json-success";
    private static final String BODYLESS = "x-bodyless-success";
    private static final String BODY_TYPE = "x-json-success-body-type";
    private static final String UNKNOWN = "unknown";

    /**
     * ClientOptInput sets the OpenAPI before processOpts, which runs before normalization. Preserve only an
     * operation-level boolean here: normalization can remove empty/null union branches, deduplicate oneOf branches,
     * and replace a union with a nullable reference, losing the original intersection and exclusivity constraints.
     */
    static void captureBeforeNormalization(OpenAPI api) {
        if (api == null) {
            return;
        }
        Set<Operation> visited = identities();
        if (api.getPaths() != null) {
            api.getPaths().values().forEach(path -> capturePath(api, path, visited));
        }
        if (api.getWebhooks() != null) {
            api.getWebhooks().values().forEach(path -> capturePath(api, path, visited));
        }
    }

    private static void capturePath(OpenAPI api, PathItem path, Set<Operation> visited) {
        if (path == null) {
            return;
        }
        for (Operation operation : path.readOperations()) {
            if (!visited.add(operation)) {
                continue;
            }
            operation.addExtension(NULLABLE, nullableJsonSuccess(api, operation));
            if (operation.getCallbacks() != null) {
                operation.getCallbacks().values().forEach(callback ->
                        callback.values().forEach(item -> capturePath(api, item, visited)));
            }
        }
    }

    /**
     * The documented successes in the core's selection order, so the first is the response it types the method from:
     * the 2xx responses by code or, without any, the default response.
     */
    private static Map<String, ApiResponse> successes(OpenAPI api, Operation source) {
        Map<String, ApiResponse> successes = new TreeMap<>();
        if (source.getResponses() == null) {
            return successes;
        }
        source.getResponses().forEach((code, response) -> {
            ApiResponse resolved = code.startsWith("2") ? resolveResponse(api, response) : null;
            if (resolved != null) {
                successes.put(code, resolved);
            }
        });
        ApiResponse fallback = resolveResponse(api, source.getResponses().get("default"));
        if (successes.isEmpty() && fallback != null) {
            successes.put("default", fallback);
        }
        return successes;
    }

    /**
     * Moves the media types a JSON call can read to the front of each success, because the core types a response from
     * its first entry. The generated call parses JSON and Angular's default Accept header prefers it, so the type must
     * not depend on declaration order. Called on the normalized operation just before the core types it, so that
     * {@code blob} can ask the core itself: a first entry it types as a Blob stays, since it selects the Blob overload
     * rather than a JSON call, whether its schema is inline, referenced or nullable. Nor does an entry the core types
     * as a Blob move forward under a media range such as {@code *}{@code /*}: it is a file, not a JSON body.
     */
    static void preferJsonContent(OpenAPI api, Operation source, Predicate<Schema<?>> blob) {
        successes(api, source).values().forEach(response -> preferJsonContent(response.getContent(), blob));
    }

    private static void preferJsonContent(Content content, Predicate<Schema<?>> blob) {
        if (content == null || content.size() < 2) {
            return;
        }
        Schema<?> first = content.values().iterator().next().getSchema();
        if (first != null && blob.test(first)) {
            return;
        }
        Map<String, MediaType> declared = new LinkedHashMap<>(content);
        content.clear();
        // A stable sort: exact JSON media types, then ranges that include JSON, then everything else.
        declared.entrySet().stream().sorted(Comparator.comparingInt(entry -> jsonPreference(entry, blob)))
                .forEach(entry -> content.put(entry.getKey(), entry.getValue()));
    }

    private static int jsonPreference(Map.Entry<String, MediaType> entry, Predicate<Schema<?>> blob) {
        Schema<?> schema = entry.getValue().getSchema();
        if (schema != null && blob.test(schema)) {
            return 2;
        }
        if (TypeScriptSnippets.jsonMediaType(entry.getKey()) != null) {
            return 0;
        }
        return TypeScriptSnippets.acceptsJson(entry.getKey()) ? 1 : 2;
    }

    /** Null evidence from every readable body of every success, without forming a union of their DTO body types. */
    private static boolean nullableJsonSuccess(OpenAPI api, Operation source) {
        return successes(api, source).values().stream()
                .filter(response -> !isBodyless(response))
                .flatMap(response -> response.getContent().entrySet().stream())
                .filter(entry -> TypeScriptSnippets.acceptsJson(entry.getKey()))
                // An omitted schema leaves JSON content unconstrained, including the value null.
                .anyMatch(entry -> entry.getValue().getSchema() == null || allowsNull(api, entry.getValue().getSchema()));
    }

    /** Called after the parent's fromOperation to reuse its normalized body type and imports. */
    static void capture(OpenAPI api, Operation source, CodegenOperation op) {
        Map<String, ApiResponse> successes = successes(api, source);
        Object nullable = source.getExtensions() == null ? null : source.getExtensions().get(NULLABLE);
        op.vendorExtensions.put(NULLABLE, nullable instanceof Boolean ? nullable : nullableJsonSuccess(api, source));
        op.vendorExtensions.put(BODYLESS, successes.values().stream().anyMatch(ResponseTypes::isBodyless));
        if (op.returnType == null) {
            String type = recoveredBodyType(successes, op);
            if (type != null) {
                op.vendorExtensions.put(BODY_TYPE, type);
            }
        }
    }

    /**
     * The body type of a JSON call when the core selected a response without a type. A selected body without a schema
     * may hold any JSON value. A selected bodyless response defers to the lowest success with a type, then to a
     * success without a schema. A success the core types as a file is not a body the JSON call can read, even under a
     * media range. Returns null when no success documents a body the call can read.
     */
    private static String recoveredBodyType(Map<String, ApiResponse> successes, CodegenOperation op) {
        if (successes.isEmpty()) {
            return null;
        }
        if (hasNoSchema(successes.values().iterator().next())) {
            return UNKNOWN;
        }
        return successes.entrySet().stream()
                .filter(entry -> jsonContent(entry.getValue()) != null && jsonContent(entry.getValue()).getSchema() != null)
                .flatMap(entry -> op.responses.stream().filter(response -> response.code.equals(entry.getKey())))
                .map(response -> response.dataType)
                .filter(type -> type != null && !TypeScriptSnippets.isBinaryType(type))
                .findFirst()
                .orElse(successes.values().stream().anyMatch(ResponseTypes::hasNoSchema) ? UNKNOWN : null);
    }

    private static boolean isBodyless(ApiResponse response) {
        return response.getContent() == null || response.getContent().isEmpty();
    }

    private static boolean hasNoSchema(ApiResponse response) {
        MediaType json = jsonContent(response);
        return json != null && json.getSchema() == null;
    }

    static ResponseTypes of(CodegenOperation op, TypeScriptSnippets.ResponseKind kind, String fallback) {
        String effective = op.returnType != null ? op.returnType : fallback;
        if (kind != TypeScriptSnippets.ResponseKind.JSON) {
            return new ResponseTypes(effective, effective);
        }
        effective = (String) op.vendorExtensions.getOrDefault(BODY_TYPE, effective);
        // A JSON call yields null for an empty response whatever media type the other successes declare.
        boolean nullable = Boolean.TRUE.equals(op.vendorExtensions.get(NULLABLE))
                || Boolean.TRUE.equals(op.vendorExtensions.get(BODYLESS));
        String body = nullable && !"void".equals(effective) && !UNKNOWN.equals(effective)
                ? "(" + effective + ") | null" : effective;
        return new ResponseTypes(effective, body);
    }

    /** The entry the core types this response from, when a JSON call can read it. */
    private static MediaType jsonContent(ApiResponse response) {
        if (isBodyless(response)) {
            return null;
        }
        var entry = response.getContent().entrySet().iterator().next();
        return TypeScriptSnippets.acceptsJson(entry.getKey()) ? entry.getValue() : null;
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
    static boolean allowsNull(OpenAPI api, Schema<?> schema) {
        return allowsNull(api, schema, identities());
    }

    private static boolean allowsNull(OpenAPI api, Schema<?> schema, Set<Schema<?>> active) {
        if (schema == null || !active.add(schema)) {
            return false;
        }
        try {
            if (Boolean.FALSE.equals(schema.getBooleanSchemaValue())
                    || schema.getEnum() != null && schema.getEnum().stream().noneMatch(value -> value == null)
                    || schema.getConst() != null) {
                return false;
            }
            boolean nullable = Boolean.TRUE.equals(schema.getNullable());
            boolean typeAllowsNull = schema.getTypes() != null && !schema.getTypes().isEmpty()
                    ? schema.getTypes().contains("null") : schema.getType() == null || "null".equals(schema.getType());
            if (!nullable && !typeAllowsNull) {
                return false;
            }
            if (schema.get$ref() != null) {
                Schema<?> target = referencedSchema(api, schema);
                if (target == null || !(nullable || allowsNull(api, target, active))) {
                    return false;
                }
            }
            // Retain the supported OpenAPI 3.0 nullable reference-alias convention. Actual 3.1 type/null
            // intersections are evaluated before normalization, so synthetic nullable wrappers are unambiguous.
            boolean nullableAlias = nullable && api.getOpenapi() != null && api.getOpenapi().startsWith("3.0.")
                    && schema.getType() == null
                    && (schema.getTypes() == null || schema.getTypes().isEmpty())
                    && schema.getAllOf() != null && schema.getAllOf().size() == 1
                    && schema.getAllOf().getFirst().get$ref() != null
                    && referencedSchema(api, schema.getAllOf().getFirst()) != null;
            if (schema.getAllOf() != null && !nullableAlias
                    && !schema.getAllOf().stream().allMatch(branch -> allowsNull(api, branch, active))) {
                return false;
            }
            if (schema.getOneOf() != null
                    && schema.getOneOf().stream().filter(branch -> allowsNull(api, branch, active)).count() != 1) {
                return false;
            }
            if (schema.getAnyOf() != null && schema.getAnyOf().stream().noneMatch(branch -> allowsNull(api, branch, active))) {
                return false;
            }
            return schema.getNot() == null || !allowsNull(api, schema.getNot(), active);
        } finally {
            active.remove(schema);
        }
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
}
