/*
 * Copyright (c) 2024 TUM Applied Education Technologies (AET)
 * Licensed under the MIT License
 */
package de.tum.cit.aet.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
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
            operation.addExtension(NULLABLE, nullableSuccess(api, operation));
            if (operation.getCallbacks() != null) {
                operation.getCallbacks().values().forEach(callback ->
                        callback.values().forEach(item -> capturePath(api, item, visited)));
            }
        }
    }

    private static boolean nullableSuccess(OpenAPI api, Operation source) {
        boolean bodyless = false;
        boolean nullable = false;
        boolean jsonBody = false;
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
                MediaType json = jsonContent(response);
                if (json != null) {
                    jsonBody = true;
                    // Preserve null evidence from all successes, without forming a union of their DTO body types.
                    // An omitted schema leaves JSON content unconstrained, including the value null.
                    nullable |= json.getSchema() == null || allowsNull(api, json.getSchema());
                }
            }
        }
        return jsonBody && (bodyless || nullable);
    }

    /** Called after the parent's fromOperation to reuse its normalized body type and imports. */
    static void capture(OpenAPI api, Operation source, CodegenOperation op, ApiResponse selected) {
        Object nullable = source.getExtensions() == null ? null : source.getExtensions().get(NULLABLE);
        op.vendorExtensions.put(NULLABLE, nullable instanceof Boolean ? nullable : nullableSuccess(api, source));
        ApiResponse selectedResponse = resolveResponse(api, selected);
        if (op.returnType == null && isBodyless(selectedResponse) && source.getResponses() != null) {
            // The lowest success can itself be bodyless. Reuse the core's already named body type/imports.
            String code = source.getResponses().entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("2") && jsonSchema(resolveResponse(api, entry.getValue())) != null)
                    .map(entry -> entry.getKey()).min(String::compareTo).orElse(null);
            String type = op.responses.stream()
                    .filter(response -> response.code.equals(code)).map(response -> response.dataType)
                    .filter(value -> value != null).findFirst().orElse(null);
            if (type != null) {
                op.vendorExtensions.put(BODY_TYPE, type);
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
                ? "(" + effective + ") | null" : effective;
        return new ResponseTypes(effective, body);
    }

    private static Schema<?> jsonSchema(ApiResponse response) {
        MediaType json = jsonContent(response);
        return json == null ? null : json.getSchema();
    }

    private static MediaType jsonContent(ApiResponse response) {
        if (response == null || response.getContent() == null || response.getContent().isEmpty()) {
            return null;
        }
        // Match the core's first-content selection; do not change text/binary overload selection.
        var entry = response.getContent().entrySet().iterator().next();
        return TypeScriptSnippets.jsonMediaType(entry.getKey()) != null ? entry.getValue() : null;
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
