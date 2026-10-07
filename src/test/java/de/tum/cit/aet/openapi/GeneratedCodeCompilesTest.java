package de.tum.cit.aet.openapi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;

class GeneratedCodeCompilesTest {

    private static final Path FIXTURES = Path.of("src/test/resources/fixtures");
    private static final Path TYPESCRIPT = Path.of("src/test/typescript").toAbsolutePath();

    private static final Map<String, Consumer<CodegenConfigurator>> OPTION_SETS = Map.of(
            "resources", configurator -> { },
            "inline-resources", configurator -> configurator.addAdditionalProperty("separateResources", "false"),
            "observables-only", configurator -> configurator.addAdditionalProperty("useHttpResource", "false")
                    .addAdditionalProperty("separateResources", "false"),
            "model-affixes", configurator -> configurator.addAdditionalProperty("modelNamePrefix", "Api")
                    .addAdditionalProperty("modelNameSuffix", "Model").addAdditionalProperty("modelSuffix", "Dto"),
            "date-objects", configurator -> configurator.addTypeMapping("DateTime", "Date").addTypeMapping("date", "Date"));

    @TempDir
    Path tempDir;

    @Test
    void generatedCodeOfEveryFixtureCompiles() throws IOException, InterruptedException {
        List<Path> fixtures;
        try (Stream<Path> files = Files.list(FIXTURES)) {
            fixtures = files.filter(file -> file.toString().endsWith(".yaml")).sorted().toList();
        }
        for (Path fixture : fixtures) {
            String name = fixture.getFileName().toString().replace(".yaml", "");
            OPTION_SETS.forEach((options, properties) -> generate(fixture, tempDir.resolve(name).resolve(options), properties));
        }
        Files.createSymbolicLink(tempDir.resolve("node_modules"), TYPESCRIPT.resolve("node_modules"));
        Files.writeString(tempDir.resolve("tsconfig.json"), """
                { "extends": "%s", "include": ["**/*.ts"], "exclude": ["node_modules"] }
                """.formatted(TYPESCRIPT.resolve("tsconfig.json")));

        NodeProcess.assertSucceeds(tempDir, "Generated code does not compile",
                TYPESCRIPT.resolve("node_modules/typescript/bin/tsc").toString(), "-p", "tsconfig.json");
    }

    private static void generate(Path spec, Path outputDir, Consumer<CodegenConfigurator> options) {
        CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName(Angular22Generator.GENERATOR_NAME)
                .setInputSpec(spec.toAbsolutePath().toString())
                .setOutputDir(outputDir.toString());
        options.accept(configurator);
        new DefaultGenerator().opts(configurator.toClientOptInput()).generate();
    }
}
