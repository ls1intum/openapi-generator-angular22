package de.tum.cit.aet.openapi;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;

class MultipartTest {

    @TempDir
    Path tempDir;

    @Test
    void sendsEachMultipartFieldAsTheRightPart() throws IOException, InterruptedException {
        CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName(Angular22Generator.GENERATOR_NAME)
                .setInputSpec(Path.of("src/test/resources/fixtures/multipart-openapi.yaml").toAbsolutePath().toString())
                .setOutputDir(tempDir.toString())
                .addTypeMapping("DateTime", "Date")
                .addTypeMapping("date", "Date");
        new DefaultGenerator().opts(configurator.toClientOptInput()).generate();

        NodeProcess.assertSucceeds(Path.of("src/test/typescript"), "A multipart part has the wrong body or type",
                "multipart.test.mjs", tempDir.resolve("api/upload-api.ts").toString());
    }
}
