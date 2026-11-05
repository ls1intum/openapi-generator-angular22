/*
 * Copyright (c) 2024 TUM Applied Education Technologies (AET)
 * Licensed under the MIT License
 */
package de.tum.cit.aet.openapi;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import io.swagger.v3.oas.models.media.Encoding;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Builds the TypeScript snippets that the templates print for an operation or a parameter, such as the
 * {@code HttpClient} call, the httpResource signature, the URL template literal and the {@code FormData} appends, and
 * stores them in vendor extensions. The snippets depend only on the codegen model, not on the generator's state.
 */
final class TypeScriptSnippets {

    /** TypeScript types that {@code String()} turns into a form field value without losing information. */
    private static final Set<String> TS_SCALAR_TYPES = Set.of("string", "number", "boolean");
    private static final String URL_ENCODED_FORM = "application/x-www-form-urlencoded";
    private static final Set<String> QUERY_STYLES = Set.of("form", "spaceDelimited", "pipeDelimited", "deepObject");
    private static final Pattern JSON_MEDIA_TYPE = Pattern.compile("(?i)application/([^;]+\\+)?json(\\s*;.*)?");
    private static final Pattern JSON_MEDIA_RANGE = Pattern.compile("(?i)(\\*|application)/\\*(\\s*;.*)?");
    /** An ASCII identifier, which TypeScript accepts as a property name without quotes. */
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

    /**
     * How {@code HttpClient} and {@code httpResource} must read a response body. Without an explicit
     * {@code responseType} the client parses JSON and throws on text or binary payloads (iCalendar files, CSV
     * exports, plain-text tokens, generated source code).
     */
    enum ResponseKind {
        /** Parsed as JSON into the return type, including JSON-string endpoints. */
        JSON,
        /** A string return whose produced media types are all {@code text/*}. */
        TEXT,
        /** A binary ({@code Blob}) return, including a file download. */
        BLOB;

        static ResponseKind of(CodegenOperation op) {
            if ("Blob".equals(op.returnType)) {
                return BLOB;
            }
            if ("string".equals(op.returnType) && producesTextOnly(op)) {
                return TEXT;
            }
            return JSON;
        }
    }

    /**
     * Processes path parameters for a single operation: sets {@code x-is-numeric} on each parameter, since numeric
     * parameters don't need URI encoding, and the names of its locals ({@code x-value-name}, {@code x-path-name}).
     *
     * @param op     the operation whose path parameters should be processed
     * @param locals the names from {@link #allocateLocals}
     */
    static void processPathParameters(CodegenOperation op, Map<String, Locals> locals) {
        for (CodegenParameter param : op.pathParams) {
            param.vendorExtensions.put("x-is-numeric", isNumericParam(param));
            param.vendorExtensions.put("x-value-name", locals.get(param.paramName).value());
            param.vendorExtensions.put("x-path-name", locals.get(param.paramName).path());
        }
    }

    /**
     * Processes header parameters for a single operation: sets {@code x-value-name}, the resource local that holds the
     * unwrapped value, {@code x-wire-name-literal}, the header name as a TypeScript string literal, and the value
     * expressions of the service ({@code x-header-value}) and the resource ({@code x-resource-header-value}) on each
     * parameter.
     *
     * @param op     the operation whose header parameters should be processed
     * @param locals the names from {@link #allocateLocals}
     */
    static void processHeaderParameters(CodegenOperation op, Map<String, Locals> locals) {
        for (CodegenParameter param : op.headerParams) {
            param.vendorExtensions.put("x-value-name", locals.get(param.paramName).value());
            param.vendorExtensions.put("x-wire-name-literal", stringLiteral(param.baseName));
            param.vendorExtensions.put("x-header-value", headerValue(param, param.paramName));
            param.vendorExtensions.put("x-resource-header-value", headerValue(param, locals.get(param.paramName).value()));
        }
    }

    /**
     * Returns the expression that serializes a header value in OpenAPI's default simple style: a collection as its
     * items joined with commas, an object as its keys and values joined with commas ({@code R,100,G,200}, or
     * {@code R=100,G=200} with {@code explode}), and anything else with {@code String()}.
     */
    private static String headerValue(CodegenParameter param, String value) {
        if (param.isArray) {
            return "Array.from(" + value + ").join(',')";
        }
        if (param.isMap || param.isModel || param.isFreeFormObject) {
            return param.isExplode
                    ? "Object.entries(" + value + ").map(([key, entry]) => `${key}=${entry}`).join(',')"
                    : "Object.entries(" + value + ").flat().join(',')";
        }
        return "String(" + value + ")";
    }

    /**
     * The locals that a generated body derives from a path or header parameter: {@code value} holds the unwrapped
     * signal in a resource, and {@code path} the URI-encoded value of a string path parameter ({@code null} for any
     * other parameter).
     */
    record Locals(String value, String path) {
    }

    /**
     * Names the locals that the generated bodies derive from path and header parameters. One set of used names covers
     * the operation's service method and resource function: it starts with the identifiers that the templates fix and
     * every argument name, and a derived name whose preferred form is taken gets a digit suffix. So a derived local
     * never hides an argument or another local, while ordinary inputs keep {@code <name>Value} and {@code <name>Path}.
     *
     * @param op               the operation
     * @param fixedIdentifiers the identifiers that the templates declare or call
     * @return the locals of each path and header parameter, by parameter name
     */
    static Map<String, Locals> allocateLocals(CodegenOperation op, Set<String> fixedIdentifiers) {
        Set<String> used = new HashSet<>(fixedIdentifiers);
        op.allParams.forEach(param -> used.add(param.paramName));
        Map<String, Locals> locals = new HashMap<>();
        for (CodegenParameter param : op.pathParams) {
            String value = allocate(used, param.paramName + "Value");
            String path = isNumericParam(param) ? null : allocate(used, param.paramName + "Path");
            locals.put(param.paramName, new Locals(value, path));
        }
        for (CodegenParameter param : op.headerParams) {
            locals.put(param.paramName, new Locals(allocate(used, param.paramName + "Value"), null));
        }
        return locals;
    }

    /**
     * Returns the preferred name, or the preferred name with the lowest digit suffix from 2 on, that is not yet in
     * {@code used}, and adds it.
     */
    static String allocate(Set<String> used, String preferred) {
        String name = preferred;
        for (int suffix = 2; !used.add(name); suffix++) {
            name = preferred + suffix;
        }
        return name;
    }

    /**
     * Returns the arguments that pass a query parameter's style and explode to {@code appendQueryParam}, e.g.
     * {@code , 'deepObject', true}, {@code , 'json'} for a parameter declared with JSON content, or nothing for the
     * default, form with explode, and for content of another media type.
     *
     * @param op    the operation that declares the parameter
     * @param param the query parameter
     * @return the arguments to append to the call
     * @throws IllegalArgumentException if the style is not one OpenAPI allows in a query
     */
    static String queryStyleArguments(CodegenOperation op, CodegenParameter param) {
        if (param.contentType != null) {
            return jsonMediaType(param.contentType) != null ? ", 'json'" : "";
        }
        String style = param.style != null ? param.style : "form";
        if (!QUERY_STYLES.contains(style)) {
            throw new IllegalArgumentException("Query parameter " + param.baseName + " of operation " + op.operationId + " has style "
                    + style + ", which OpenAPI does not allow in a query");
        }
        return "form".equals(style) && param.isExplode ? "" : ", " + stringLiteral(style) + ", " + param.isExplode;
    }

    /**
     * Returns the arguments that pass a URL-encoded form field's style and explode to {@code appendQueryParam}: those
     * of its {@code encoding} entry, or nothing for the default, form with explode. The parser accepts only the styles
     * OpenAPI allows in a form, and fills in form for an entry without one, so an entry's {@code contentType} cannot
     * be told apart from an explicit form style and is not used.
     */
    private static String formStyleArguments(Encoding encoding) {
        if (encoding == null) {
            return "";
        }
        String style = encoding.getStyle() != null ? encoding.getStyle().toString() : "form";
        // OpenAPI explodes a form field by default, and no other style.
        boolean explode = encoding.getExplode() != null ? encoding.getExplode() : "form".equals(style);
        return "form".equals(style) && explode ? "" : ", " + stringLiteral(style) + ", " + explode;
    }

    static String paramsInterfaceName(CodegenOperation op) {
        return Names.toPascalCase(op.operationId) + "Params";
    }

    static boolean allQueryParamsOptional(CodegenOperation op) {
        return op.queryParams.stream().noneMatch(param -> param.required);
    }

    /**
     * Whether a service method returns the full {@code HttpResponse<T>} instead of the body, so the caller can read the
     * response headers: always for a file download, and with {@code observeResponseHeaders} when a success response
     * declares headers. Headers of an error response don't count: {@code HttpErrorResponse} already exposes them.
     *
     * @param op                     the operation
     * @param observeResponseHeaders the value of the generator option
     * @return {@code true} if the service method observes the full response
     */
    static boolean observesResponse(CodegenOperation op, boolean observeResponseHeaders) {
        return op.isResponseFile
                || observeResponseHeaders && op.responses.stream().anyMatch(response -> response.is2xx && !response.headers.isEmpty());
    }

    /**
     * Builds the {@code HttpClient} call of a service method once, so the template prints a single
     * {@code return this.http.<method><typeArg>(<args>);} line.
     *
     * <p>Sets vendor extensions on the operation:</p>
     * <ul>
     *   <li>{@code x-observable-type} &mdash; what the service method's {@code Observable} emits: the return type, or
     *       {@code HttpResponse<T>} of it when the call observes the full response</li>
     *   <li>{@code x-http-type-arg} &mdash; the {@code <T>} type argument, empty when a {@code responseType}
     *       option selects a non-JSON overload that already fixes the result type</li>
     *   <li>{@code x-http-args} &mdash; the argument list: {@code url}, the payload for methods that take one,
     *       and an options object with, in this order and only when present, {@code body} (DELETE only),
     *       {@code headers}, {@code responseType} and {@code observe}</li>
     * </ul>
     *
     * @param op              the operation whose HttpClient call should be built
     * @param response        how the response body is read
     * @param observeResponse whether the call observes the full response, see {@link #observesResponse}
     */
    static void buildHttpCall(CodegenOperation op, ResponseKind response, boolean observeResponse) {
        boolean urlEncoded = isUrlEncodedForm(op);
        String payload = null;
        if (urlEncoded) {
            payload = "formBody.toString()";
        } else if (op.getHasFormParams()) {
            payload = "formData";
        } else if (op.bodyParam != null) {
            payload = op.bodyParam.paramName;
        }

        List<String> args = new ArrayList<>();
        List<String> options = new ArrayList<>();
        args.add("url");
        if ("DELETE".equalsIgnoreCase(op.httpMethod)) {
            // HttpClient.delete(url, options) has no body argument; the body travels in the options.
            if (payload != null) {
                options.add("body: " + payload);
            }
        } else if (payload != null) {
            args.add(payload);
        } else if (op.isBodyAllowed()) {
            args.add("null");
        }
        if (urlEncoded) {
            // Angular labels a string body text/plain, so a form body needs its media type named.
            String contentType = "'Content-Type': " + stringLiteral(URL_ENCODED_FORM);
            options.add(op.getHasHeaderParams() ? "headers: { ...headers, " + contentType + " }" : "headers: { " + contentType + " }");
        } else if (op.getHasHeaderParams()) {
            options.add("headers");
        }
        options.addAll(switch (response) {
            case JSON -> List.of();
            case TEXT -> List.of("responseType: 'text'");
            case BLOB -> List.of("responseType: 'blob'");
        });
        if (observeResponse) {
            options.add("observe: 'response'");
        }
        if (!options.isEmpty()) {
            args.add("{ " + String.join(", ", options) + " }");
        }

        ResponseTypes types = ResponseTypes.of(op, response, "void");
        String returnType = observeResponse ? types.effective() : types.body();
        op.vendorExtensions.put("x-observable-type", observeResponse ? "HttpResponse<" + returnType + ">" : returnType);
        op.vendorExtensions.put("x-http-type-arg", response == ResponseKind.JSON ? "<" + returnType + ">" : "");
        op.vendorExtensions.put("x-http-args", String.join(", ", args));
    }

    /**
     * Builds the parameter list, the httpResource factory and the request expression of a GET operation's
     * httpResource function.
     *
     * <p>Sets vendor extensions on the operation:</p>
     * <ul>
     *   <li>{@code x-resource-params} &mdash; path parameters, then header parameters (each a signal or a plain
     *       value), then the query {@code params} signal. An argument is marked optional ({@code ?}) only when
     *       every argument after it is optional too; an optional argument before a required one accepts
     *       {@code undefined} instead.</li>
     *   <li>{@code x-resource-type} &mdash; the value type of the {@code HttpResourceRef}</li>
     *   <li>{@code x-resource-factory} &mdash; {@code httpResource<T>} for JSON, {@code httpResource.text} or
     *       {@code httpResource.blob} otherwise</li>
     *   <li>{@code x-resource-request} &mdash; what the request function returns: the URL template literal, or
     *       {@code { url: ..., headers }} when the operation declares header parameters</li>
     * </ul>
     *
     * @param op                   the GET operation
     * @param response             how the response body is read
     * @param resourcePathTemplate the URL path with {@code ${...}} placeholders for the resource template
     */
    static void buildResourceFunction(CodegenOperation op, ResponseKind response, String resourcePathTemplate) {
        List<ResourceArg> args = new ArrayList<>();
        for (CodegenParameter param : op.pathParams) {
            args.add(new ResourceArg(param.paramName, signalOrValue(param.dataType), false));
        }
        for (CodegenParameter param : op.headerParams) {
            args.add(new ResourceArg(param.paramName, signalOrValue(param.dataType), !param.required));
        }
        boolean hasQueryParams = !op.queryParams.isEmpty();
        if (hasQueryParams) {
            args.add(new ResourceArg("params", "Signal<" + paramsInterfaceName(op) + ">", allQueryParamsOptional(op)));
        }

        LinkedList<String> rendered = new LinkedList<>();
        boolean trailingOptional = true;
        for (int i = args.size() - 1; i >= 0; i--) {
            ResourceArg arg = args.get(i);
            trailingOptional = trailingOptional && arg.optional();
            if (trailingOptional) {
                rendered.addFirst(arg.name() + "?: " + arg.type());
            } else {
                rendered.addFirst(arg.name() + ": " + arg.type() + (arg.optional() ? " | undefined" : ""));
            }
        }
        op.vendorExtensions.put("x-resource-params", String.join(", ", rendered));

        String resourceType = ResponseTypes.of(op, response, "unknown").body();
        op.vendorExtensions.put("x-resource-type", resourceType);
        op.vendorExtensions.put("x-resource-factory", switch (response) {
            case JSON -> "httpResource<" + resourceType + ">";
            case TEXT -> "httpResource.text";
            case BLOB -> "httpResource.blob";
        });

        String url = "`${BASE_PATH}" + resourcePathTemplate + (hasQueryParams ? "${query ? `?${query}` : ''}" : "") + "`";
        op.vendorExtensions.put("x-resource-request", op.getHasHeaderParams() ? "{ url: " + url + ", headers }" : url);
    }

    /** One argument of a generated httpResource function. */
    private record ResourceArg(String name, String type, boolean optional) {
    }

    private static String signalOrValue(String dataType) {
        return "Signal<" + dataType + " | undefined> | " + dataType;
    }

    /**
     * Computes the {@code FormData.append} statement for each multipart field and stores it in the
     * {@code x-form-append} vendor extension.
     *
     * <p>{@code FormData} only accepts strings and Blobs. A field, or each item of an array or set except null and
     * undefined ones, becomes one part: a binary as it is, a string, number, boolean or enum value as text (OpenAPI's
     * default encoding for primitives), a {@code Date} as ISO 8601 (a full date for {@code format: date}), and an
     * untyped value by what it holds at runtime. Everything else (objects, maps, and any non-binary field whose
     * {@code encoding} names a JSON media type) goes out as one JSON part of the declared media type or
     * {@code application/json}, an array whole and with its nulls, which is what Spring's {@code @RequestPart}
     * expects for a DTO.</p>
     *
     * <p>A URL-encoded body instead goes through {@code appendQueryParam} into a {@code URLSearchParams}, since OpenAPI
     * serializes its fields by the query rules. Each field takes the style and explode of its {@code encoding} entry,
     * or form with explode by default.</p>
     *
     * @param op        the operation whose form parameters should be processed
     * @param encodings the {@code encoding} entries of the URL-encoded body by field name, empty for any other body
     */
    static void processFormParameters(CodegenOperation op, Map<String, Encoding> encodings) {
        if (isUrlEncodedForm(op)) {
            op.vendorExtensions.put("x-url-encoded", true);
            for (CodegenParameter param : op.formParams) {
                Encoding encoding = encodings.get(param.baseName);
                param.vendorExtensions.put("x-wire-name-literal", stringLiteral(param.baseName));
                param.vendorExtensions.put("x-query-style-args", formStyleArguments(encoding));
            }
            return;
        }
        for (CodegenParameter param : op.formParams) {
            String name = param.paramName;
            String key = stringLiteral(param.baseName);
            String jsonType = jsonMediaType(param.contentType);
            String statement;
            if (param.isArray) {
                CodegenProperty items = param.items;
                String part = items == null ? null
                        : partValue(items.dataType, items.isEnum || items.isEnumRef, items.isAnyType, items.isDate, jsonType, "item");
                statement = part == null ? appendJsonPart(key, "Array.from(" + name + ")", jsonType)
                        : name + ".forEach(item => { if (item !== undefined && item !== null) { formData.append(" + key + ", " + part + "); } });";
            } else {
                String part = partValue(param.dataType, param.isEnum || param.isEnumRef, param.isAnyType, param.isDate, jsonType, name);
                statement = part == null ? appendJsonPart(key, name, jsonType) : "formData.append(" + key + ", " + part + ");";
            }
            param.vendorExtensions.put("x-form-append", statement);
        }
    }

    /**
     * Returns the expression that turns one value into a part, or null when the value belongs in a JSON part. An
     * untyped value is only known at runtime, so its expression keeps a Blob, sends a date as ISO 8601 and another
     * primitive as text, and wraps anything else in a JSON part.
     */
    private static String partValue(String dataType, boolean isEnum, boolean isAnyType, boolean isDate, String jsonType, String value) {
        if (isBinaryType(dataType)) {
            return value;
        }
        if (jsonType != null) {
            return null;
        }
        if ("Date".equals(dataType)) {
            return value + ".toISOString()" + (isDate ? ".slice(0, 10)" : "");
        }
        if (isEnum || TS_SCALAR_TYPES.contains(dataType)) {
            return "String(" + value + ")";
        }
        if (isAnyType) {
            return value + " instanceof Blob ? " + value + " : " + value + " instanceof Date ? " + value + ".toISOString() : typeof " + value
                    + " === 'object' ? " + jsonBlob(value, null) + " : String(" + value + ")";
        }
        return null;
    }

    private static String appendJsonPart(String key, String value, String jsonType) {
        return "formData.append(" + key + ", " + jsonBlob(value, jsonType) + ");";
    }

    private static String jsonBlob(String value, String jsonType) {
        return "new Blob([JSON.stringify(" + value + ")], { type: " + stringLiteral(jsonType != null ? jsonType : "application/json") + " })";
    }

    /**
     * Returns the first JSON media type of a declared content type, which OpenAPI lets list several types separated
     * by commas; {@code application/json} and {@code +json} types such as {@code application/vnd.api+json} count.
     *
     * @param contentType the declared content type, or null
     * @return the JSON media type to send, or null when none is declared
     */
    static String jsonMediaType(String contentType) {
        if (contentType == null) {
            return null;
        }
        return Arrays.stream(contentType.split(",")).map(String::strip).filter(type -> JSON_MEDIA_TYPE.matcher(type).matches())
                .findFirst().orElse(null);
    }

    /**
     * Whether a response declared with this content type can carry JSON: a JSON media type, or a range that includes
     * one, such as {@code *}{@code /*}.
     *
     * @param contentType the declared content type
     * @return {@code true} if a call that parses JSON can read such a response
     */
    static boolean acceptsJson(String contentType) {
        return jsonMediaType(contentType) != null
                || Arrays.stream(contentType.split(",")).map(String::strip).anyMatch(type -> JSON_MEDIA_RANGE.matcher(type).matches());
    }

    /**
     * Whether an operation sends its form fields as {@code application/x-www-form-urlencoded} rather than as
     * {@code multipart/form-data}. The parent marks a form body multipart only when it declares a multipart media
     * type, so every other form body is URL-encoded.
     *
     * @param op the operation
     * @return {@code true} if the operation has form fields and no multipart body
     */
    static boolean isUrlEncodedForm(CodegenOperation op) {
        return op.getHasFormParams() && !op.isMultipart;
    }

    static boolean isBinaryType(String dataType) {
        return "Blob".equals(dataType) || "File".equals(dataType);
    }

    /**
     * Builds a TypeScript template literal URL from the original OpenAPI path by replacing
     * {@code {paramName}} placeholders with {@code ${variable}} expressions.
     *
     * <p>A string parameter uses its URI-encoded path local in both contexts. A numeric parameter uses the argument in
     * a service method ({@code useSignalValue=false}) and its value local, unwrapped from the signal, in an httpResource
     * function ({@code useSignalValue=true}).</p>
     *
     * @param op             the operation being processed
     * @param originalPath   the raw OpenAPI path before URL encoding (e.g., {@code /api/jobs/{id}/pdf})
     * @param useSignalValue {@code true} for httpResource templates, {@code false} for HttpClient services
     * @param locals         the names from {@link #allocateLocals}
     * @return the TypeScript template literal path (e.g., {@code /api/jobs/${idPath}/pdf})
     */
    static String buildPathTemplate(CodegenOperation op, String originalPath, boolean useSignalValue, Map<String, Locals> locals) {
        String path = originalPath;
        for (CodegenParameter param : op.pathParams) {
            Locals names = locals.get(param.paramName);
            String valueVar;
            if (names.path() != null) {
                valueVar = names.path();
            } else {
                valueVar = useSignalValue ? names.value() : param.paramName;
            }

            String placeholder = "{" + param.baseName + "}";
            path = path.replace(placeholder, "${" + valueVar + "}");
        }
        return path;
    }

    /**
     * Checks whether a parameter represents a numeric type (integer or number),
     * which determines whether it needs URI encoding in the generated URL template.
     *
     * @param param the codegen parameter to check
     * @return {@code true} if the parameter is numeric, {@code false} otherwise
     */
    private static boolean isNumericParam(CodegenParameter param) {
        if (Boolean.TRUE.equals(param.isInteger) || Boolean.TRUE.equals(param.isNumber)) {
            return true;
        }
        return "number".equals(param.dataType) || "number".equals(param.baseType) || "integer".equals(param.baseType);
    }

    /**
     * Whether the operation only produces text media types (e.g. text/plain, text/calendar, text/csv).
     * Used to emit responseType: 'text' for string-returning operations; JSON-string endpoints (which produce
     * application/json) return false and keep the default JSON parser.
     */
    private static boolean producesTextOnly(CodegenOperation op) {
        if (op.produces == null || op.produces.isEmpty()) {
            return false;
        }
        for (Map<String, String> mediaType : op.produces) {
            String type = mediaType.get("mediaType");
            if (type == null || !type.toLowerCase(Locale.ROOT).startsWith("text/")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the TypeScript property key for a JSON key. Interfaces describe the JSON exactly, so the key is used as
     * it is: reserved words such as {@code final} are valid property names and stay unescaped, and a key that is not
     * an identifier (e.g. {@code x-y}) is quoted.
     *
     * @param jsonKey the property name in the JSON document
     * @return the key to print in the interface
     */
    static String toPropertyKey(String jsonKey) {
        return IDENTIFIER.matcher(jsonKey).matches() ? jsonKey : stringLiteral(jsonKey);
    }

    /**
     * Returns a single-quoted TypeScript string literal for a string from the spec, such as a wire name. Jackson's JSON
     * string encoder escapes backslashes, double quotes and control characters the way a TypeScript string literal
     * reads them; the apostrophe that would end the literal is escaped on top.
     *
     * @param value the string as the spec writes it
     * @return the quoted literal, e.g. {@code 'it\'s'}
     */
    static String stringLiteral(String value) {
        return "'" + new String(JsonStringEncoder.getInstance().quoteAsString(value)).replace("'", "\\'") + "'";
    }

    private TypeScriptSnippets() {
    }
}
