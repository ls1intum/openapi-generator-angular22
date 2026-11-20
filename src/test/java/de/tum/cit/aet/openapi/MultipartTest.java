package de.tum.cit.aet.openapi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;

class MultipartTest {

    @TempDir
    Path tempDir;

    private static final Path TYPESCRIPT = Path.of("src/test/typescript").toAbsolutePath();

    @Test
    void sendsEachMultipartFieldAsTheRightPart() throws IOException, InterruptedException {
        generate();

        NodeProcess.assertSucceeds(Path.of("src/test/typescript"), "A multipart part has the wrong body or type",
                "multipart.test.mjs", tempDir.resolve("api/upload-api.ts").toString(),
                tempDir.resolve("api/multipart.ts").toString());
    }

    @Test
    void rejectsAValueThatDoesNotFitTheField() throws IOException, InterruptedException {
        generate();
        Files.copy(TYPESCRIPT.resolve("multipart-types.ts.template"), tempDir.resolve("multipart-types.ts"));
        Files.createSymbolicLink(tempDir.resolve("node_modules"), TYPESCRIPT.resolve("node_modules"));
        Files.writeString(tempDir.resolve("tsconfig.json"), """
                { "extends": "%s", "include": ["**/*.ts"], "exclude": ["node_modules"] }
                """.formatted(TYPESCRIPT.resolve("tsconfig.json")));

        // Each @ts-expect-error fails the compile if its line type-checks, so a too wide helper fails this test.
        NodeProcess.assertSucceeds(tempDir, "A multipart helper accepts a value that does not fit its field",
                TYPESCRIPT.resolve("node_modules/typescript/bin/tsc").toString(), "-p", "tsconfig.json");
    }

    private void generate() {
        CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName(Angular22Generator.GENERATOR_NAME)
                .setInputSpec(Path.of("src/test/resources/fixtures/multipart-openapi.yaml").toAbsolutePath().toString())
                .setOutputDir(tempDir.toString())
                .addTypeMapping("DateTime", "Date")
                .addTypeMapping("date", "Date");
        new DefaultGenerator().opts(configurator.toClientOptInput()).generate();
    }
}
