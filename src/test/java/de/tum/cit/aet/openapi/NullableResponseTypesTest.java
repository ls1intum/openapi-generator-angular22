package de.tum.cit.aet.openapi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openapitools.codegen.ClientOptInput;
import org.openapitools.codegen.CodegenConfig;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;

/** Strict consumer contracts against actual generated services, models and resources; executed only by CI. */
class NullableResponseTypesTest {
    private static final Path TYPESCRIPT = Path.of("src/test/typescript").toAbsolutePath();
    private static final List<Fixture> FIXTURES = List.of(
            new Fixture("nullable-success-openapi.yaml", "nullable-success-consumer.ts.template", "nullable"),
            new Fixture("nullable-success-31-openapi.yaml", "nullable-success-31-consumer.ts.template", "normalized"));

    // Keep the general fixture harness independent. Cover its six modes plus combined inline/header observation.
    private static final Map<String, Consumer<CodegenConfigurator>> OPTION_SETS = Map.of(
            "resources", configurator -> { },
            "inline-resources", configurator -> configurator.addAdditionalProperty("separateResources", "false"),
            "observables-only", configurator -> configurator.addAdditionalProperty("useHttpResource", "false")
                    .addAdditionalProperty("separateResources", "false"),
            "model-affixes", configurator -> configurator.addAdditionalProperty("modelNamePrefix", "Api")
                    .addAdditionalProperty("modelNameSuffix", "Model").addAdditionalProperty("modelSuffix", "Dto"),
            "date-objects", configurator -> configurator.addTypeMapping("DateTime", "Date").addTypeMapping("date", "Date"),
            "response-headers", configurator -> configurator.addAdditionalProperty("observeResponseHeaders", "true"),
            "inline-response-headers", configurator -> configurator.addAdditionalProperty("observeResponseHeaders", "true")
                    .addAdditionalProperty("separateResources", "false"));

    @TempDir
    Path tempDir;

    @Test
    void generatedNullableResponseConsumersCompile() throws IOException, InterruptedException {
        for (Fixture fixture : FIXTURES) {
            for (var mode : OPTION_SETS.entrySet()) {
                Path output = tempDir.resolve(fixture.tag()).resolve(mode.getKey());
                CodegenConfigurator configurator = new CodegenConfigurator()
                        .setGeneratorName(Angular22Generator.GENERATOR_NAME)
                        .setInputSpec(Path.of("src/test/resources/fixtures").resolve(fixture.spec()).toAbsolutePath().toString())
                        .setOutputDir(output.toString());
                mode.getValue().accept(configurator);
                ClientOptInput input = configurator.toClientOptInput();
                new DefaultGenerator().opts(input).generate();
                writeConsumer(fixture, output, input.getConfig());
            }
        }
        Files.createSymbolicLink(tempDir.resolve("node_modules"), TYPESCRIPT.resolve("node_modules"));
        Files.writeString(tempDir.resolve("tsconfig.json"), """
                { "extends": "%s", "include": ["**/*.ts"], "exclude": ["node_modules"] }
                """.formatted(TYPESCRIPT.resolve("tsconfig.json")));
        NodeProcess.assertSucceeds(tempDir, "Nullable response consumer contracts do not compile",
                TYPESCRIPT.resolve("node_modules/typescript/bin/tsc").toString(), "-p", "tsconfig.json");
    }

    private static void writeConsumer(Fixture fixture, Path output, CodegenConfig config) throws IOException {
        String consumer = Files.readString(TYPESCRIPT.resolve(fixture.consumer()));
        for (String model : List.of("Payload", "Job", "Other", "CaseInfo", "NullableReference", "RecursivePayload",
                "NonNullIntersection", "TypePayload", "Choice", "OtherPayload")) {
            consumer = consumer.replace("@" + model + "@", config.toModelName(model))
                    .replace("@" + model + "File@", config.toModelFilename(model));
        }
        boolean resources = enabled(config, "useHttpResource");
        boolean separate = enabled(config, "separateResources");
        boolean headers = enabled(config, "observeResponseHeaders");
        consumer = consumer.replace("@RESOURCE_MODULE@", "./api/" + fixture.tag() + (separate ? "-resources" : "-api"))
                .replace("@HEADER_JOB@", headers ? "HttpResponse<Job>" : "Job | null")
                .replace("@HEADER_CASE@", headers ? "HttpResponse<CaseInfo>" : "CaseInfo | null")
                .replace("@HEADER_VOID@", headers ? "HttpResponse<void>" : "void")
                .replace("@HEADER_NULL@", headers ? "HttpResponse<null>" : "null")
                .replace("@HEADER_CHOICE@", headers ? "HttpResponse<Choice>" : "Choice");
        consumer = section(consumer, "RESOURCES", resources);
        consumer = section(consumer, "HEADERS", headers);
        Files.writeString(output.resolve("nullable-response-consumer.ts"), consumer);
    }

    private static boolean enabled(CodegenConfig config, String option) {
        return Boolean.parseBoolean(config.additionalProperties().get(option).toString());
    }

    private static String section(String source, String name, boolean keep) {
        String start = "// " + name + "_START";
        String end = "// " + name + "_END";
        int from;
        while ((from = source.indexOf(start)) >= 0) {
            int to = source.indexOf(end, from);
            source = source.substring(0, from) + (keep ? source.substring(from + start.length(), to) : "")
                    + source.substring(to + end.length());
        }
        return source;
    }

    private record Fixture(String spec, String consumer, String tag) {
    }
}
