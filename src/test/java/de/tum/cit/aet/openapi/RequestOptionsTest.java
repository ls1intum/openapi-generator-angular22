package de.tum.cit.aet.openapi;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;

class RequestOptionsTest {

    @TempDir
    Path tempDir;

    @Test
    void passesTheCallerContextAndHeadersToHttpClient() throws IOException, InterruptedException {
        CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName(Angular22Generator.GENERATOR_NAME)
                .setInputSpec(Path.of("src/test/resources/fixtures/header-params-openapi.yaml").toAbsolutePath().toString())
                .setOutputDir(tempDir.toString());
        new DefaultGenerator().opts(configurator.toClientOptInput()).generate();

        NodeProcess.assertSucceeds(Path.of("src/test/typescript"), "A service method drops or misorders the caller's request options",
                "request-options.test.mjs", tempDir.resolve("api/repository-api.ts").toString());
    }
}
