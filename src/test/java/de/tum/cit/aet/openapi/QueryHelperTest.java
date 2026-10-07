package de.tum.cit.aet.openapi;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;

class QueryHelperTest {

    @TempDir
    Path tempDir;

    @Test
    void appendsQueryParametersInTheirDeclaredStyle() throws IOException, InterruptedException {
        CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName(Angular22Generator.GENERATOR_NAME)
                .setInputSpec(Path.of("src/test/resources/fixtures/object-query-openapi.yaml").toAbsolutePath().toString())
                .setOutputDir(tempDir.toString());
        new DefaultGenerator().opts(configurator.toClientOptInput()).generate();

        NodeProcess.assertSucceeds(Path.of("src/test/typescript"), "The query helper built a wrong query string",
                "query-helper.test.mjs", tempDir.resolve("api/query-params.ts").toString());
    }
}
