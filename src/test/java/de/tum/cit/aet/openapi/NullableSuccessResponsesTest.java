package de.tum.cit.aet.openapi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;

class NullableSuccessResponsesTest {
    @TempDir
    Path tempDir;

    @Test
    void sharesJsonBodyTypesAcrossServicesAndResourcesWithoutChangingObservation() throws IOException {
        List<Map<String, Object>> modes = List.of(
                Map.of(), Map.of("separateResources", "false"), Map.of("useHttpResource", "false"),
                Map.of("observeResponseHeaders", "true"),
                Map.of("observeResponseHeaders", "true", "separateResources", "false"),
                Map.of("useInjectFunction", "false", "readonlyModels", "false"));
        for (int i = 0; i < modes.size(); i++) {
            Map<String, Object> options = modes.get(i);
            Path output = generate("nullable-success-openapi.yaml", "ordinary-" + i, options);
            String api = Files.readString(output.resolve("api/nullable-api.ts"));
            boolean headers = "true".equals(options.get("observeResponseHeaders"));
            Map<String, String> types = new LinkedHashMap<>(Map.of(
                    "getMixed", "(Job) | null", "getMixedReversed", "(Job) | null", "getAccepted", "(Job) | null",
                    "getBodylessFirst", "(Job) | null", "getNullableCase", "(CaseInfo) | null",
                    "getNullableReference", "(NullableReference) | null", "getNullableScalar", "(number) | null",
                    "getNullableJsonString", "(string) | null", "getNullableArray", "(Array<Payload>) | null",
                    "getNullableMap", "({ [key: string]: Payload; }) | null"));
            types.putAll(Map.of(
                    "getNullableItems", "Array<string | null>",
                    "getNullableItemsAndRoot", "(Array<string | null>) | null",
                    "getNullableValues", "{ [key: string]: number | null; }",
                    "getNonNull", "Payload", "getErrorOnly", "Payload", "getUnconstrainedSuccess", "(Payload) | null",
                    "getRecursive", "RecursivePayload", "getIntersection", "NonNullIntersection",
                    "getBoolean", "boolean", "getNumber", "number"));
            types.put("getArray", "Array<Payload>");
            types.put("getOther", "(Other) | null");
            // The inline resolver may reuse an equivalent model. The strict consumer also checks its exact shape.
            types.put("getNullableInline", nullableModelType(api, "getNullableInline"));
            for (var entry : types.entrySet()) {
                assertJsonCall(api, entry.getKey(), entry.getValue(), false);
            }
            List<String> headerJobs = List.of("getHeaderMixed", "getHeaderBodylessFirst",
                    "getHeaderBodylessFirstReversed", "getDifferentSuccessBodies");
            for (String method : headerJobs) {
                assertJsonCall(api, method, headers ? "Job" : "(Job) | null", headers);
            }
            assertJsonCall(api, "getHeaderNullable", headers ? "CaseInfo" : "(CaseInfo) | null", headers);
            contains(api, "sendNullableRequest(body?: number): Observable<Payload>");
            contains(api, "return this.http.post<Payload>(url, body);");
            contains(api, "deleteEmpty(): Observable<void>");
            contains(api, "return this.http.delete<void>(url);");
            contains(api, "sendEmpty(): Observable<void>");
            contains(api, "return this.http.post<void>(url, null);");
            contains(api, headers ? "deleteWithHeaders(): Observable<HttpResponse<void>>"
                    : "deleteWithHeaders(): Observable<void>");
            contains(api, "getBinary(): Observable<HttpResponse<Blob>>");
            contains(api, "return this.http.get(url, { responseType: 'blob', observe: 'response' });");
            contains(api, "getText(): Observable<string>");
            contains(api, "return this.http.get(url, { responseType: 'text' });");
            assertFalse(api.contains("void | null"));

            if ("false".equals(options.get("useHttpResource"))) {
                assertFalse(Files.exists(output.resolve("api/nullable-resources.ts")));
                assertFalse(api.contains("httpResource"));
            } else {
                String resources = "false".equals(options.get("separateResources")) ? api
                        : Files.readString(output.resolve("api/nullable-resources.ts"));
                headerJobs.forEach(method -> types.put(method, "(Job) | null"));
                types.put("getHeaderNullable", "(CaseInfo) | null");
                for (var entry : types.entrySet()) {
                    assertResource(resources, entry.getKey(), entry.getValue());
                }
                contains(resources, "getBinaryResource(): HttpResourceRef<Blob | undefined>");
                contains(resources, "return httpResource.blob(() => {");
                contains(resources, "getTextResource(): HttpResourceRef<string | undefined>");
                contains(resources, "return httpResource.text(() => {");
                assertFalse(resources.contains("httpResource<Blob>"));
                assertFalse(resources.contains("httpResource<string>"));
            }
        }
    }

    @Test
    void retainsNormalizedRootNullabilityWithoutWideningNestedOrIntersectionTypes() throws IOException {
        List<Map<String, Object>> modes = List.of(Map.of(), Map.of("separateResources", "false"),
                Map.of("observeResponseHeaders", "true"),
                Map.of("observeResponseHeaders", "true", "separateResources", "false"));
        for (int i = 0; i < modes.size(); i++) {
            Map<String, Object> options = modes.get(i);
            boolean separate = !"false".equals(options.get("separateResources"));
            boolean headers = "true".equals(options.get("observeResponseHeaders"));
            Path output = generate("nullable-success-31-openapi.yaml", "normalized-" + i, options);
            String api = Files.readString(output.resolve("api/normalized-api.ts"));
            String resources = separate ? Files.readString(output.resolve("api/normalized-resources.ts")) : api;
            Map<String, String> types = new LinkedHashMap<>(Map.of(
                    "getRootArray", "(Array<Payload>) | null", "getRootMap", "({ [key: string]: number; }) | null",
                    "getRootScalar", "(boolean) | null", "getJsonNull", "(null) | null", "getUnion", "(Choice) | null",
                    "getNonNull", "Payload", "getNullableItems", "Array<string | null>",
                    "getIntersection", "NonNullIntersection"));
            types.put("getJsonNullWithHeaders", "(null) | null");
            types.putAll(Map.of(
                    "getUnconstrainedSuccess", "(Payload) | null", "getUnconstrainedAllOf", "(Payload) | null",
                    "getUnconstrainedAnyOf", "(Payload) | null", "getUnconstrainedOneOf", "(Payload) | null",
                    "getExclusiveEmptyOneOf", "Payload", "getConstrainedNullableEnum", "Payload",
                    "getNullableEnum", "(Payload) | null", "getConstrainedNullableAllOf", "Payload",
                    "getNonNullTypedUnion", "Payload"));
            for (String operation : List.of("getTypeObject", "getInlineType", "getOneOf", "getAnyOf",
                    "getInlineOneOf", "getInlineAnyOf")) {
                types.put(operation, nullableModelType(api, operation));
            }
            for (var entry : types.entrySet()) {
                boolean observed = headers && List.of("getUnion", "getJsonNullWithHeaders").contains(entry.getKey());
                String type = observed ? ("getUnion".equals(entry.getKey()) ? "Choice" : "null") : entry.getValue();
                assertJsonCall(api, entry.getKey(), type, observed);
                assertResource(resources, entry.getKey(), entry.getValue());
            }
            assertFalse(api.contains("Observable<void>"));
            assertFalse(api.contains("from '../model/null'"));
            assertFalse(resources.contains("from '../model/null'"));
            contains(Files.readString(output.resolve("model/choice.ts")), "export type Choice = Payload | OtherPayload | null;");
        }
    }

    @Test
    void parenthesizesNullableMappingsWithoutParsingTheirTypeExpressions() throws IOException {
        // Use supported type mappings to exercise expressions, not just model aliases, at the operation boundary.
        List<String> mappings = List.of(
                "Job | null", "(Job | null)", "(Job) | (null)", "Array<Job | null>", "'null' | Job",
                "(Job) & (Job | null)", "{ [key: string]: Job | null; }", "() => string", "() => string | null");
        for (String type : mappings) {
            Path output = tempDir.resolve("mapped-" + type.hashCode());
            CodegenConfigurator config = configurator("nullable-success-openapi.yaml", output);
            config.addTypeMapping("Job", type);
            config.addLanguageSpecificPrimitive(type);
            new DefaultGenerator().opts(config.toClientOptInput()).generate();
            String expected = "(" + type + ") | null";
            String api = Files.readString(output.resolve("api/nullable-api.ts"));
            assertJsonCall(api, "getMixed", expected, false);
            assertResource(Files.readString(output.resolve("api/nullable-resources.ts")), "getMixed", expected);
        }
    }

    private Path generate(String fixture, String directory, Map<String, Object> options) {
        Path output = tempDir.resolve(directory);
        CodegenConfigurator config = configurator(fixture, output);
        options.forEach(config::addAdditionalProperty);
        new DefaultGenerator().opts(config.toClientOptInput()).generate();
        return output;
    }

    private CodegenConfigurator configurator(String fixture, Path output) {
        return new CodegenConfigurator().setGeneratorName(Angular22Generator.GENERATOR_NAME)
                .setInputSpec(Path.of("src/test/resources/fixtures").resolve(fixture).toAbsolutePath().toString())
                .setOutputDir(output.toString());
    }

    private static String nullableModelType(String api, String method) {
        Matcher match = Pattern.compile(Pattern.quote(method) + "\\(\\): Observable<(\\([A-Za-z_$][\\w$]*\\) \\| null)>").matcher(api);
        assertTrue(match.find(), () -> "Expected nullable model return for " + method + ":\n" + api);
        return match.group(1);
    }

    private static void assertJsonCall(String api, String method, String type, boolean observe) {
        String signature = method + "(): Observable<" + (observe ? "HttpResponse<" + type + ">" : type) + ">";
        contains(api, signature);
        String methodSource = body(api, signature, "\n    }");
        contains(methodSource, "return this.http.get<" + type + ">(url" + (observe ? ", { observe: 'response' }" : "") + ");");
    }

    private static void assertResource(String resources, String method, String type) {
        String signature = "export function " + method + "Resource(): HttpResourceRef<" + type + " | undefined>";
        contains(resources, signature);
        contains(body(resources, signature, "\n}"), "return httpResource<" + type + ">(() => {");
    }

    private static String body(String source, String signature, String end) {
        int start = source.indexOf(signature);
        return source.substring(start, source.indexOf(end, start));
    }

    private static void contains(String source, String expected) {
        assertTrue(source.contains(expected), () -> "Expected generated output to contain:\n" + expected + "\nActual:\n" + source);
    }
}
