package de.tum.cit.aet.openapi;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;

class UrlEncodedFormTest {

    @TempDir
    Path tempDir;

    @Test
    void sendsEachFormFieldInTheStyleOfItsEncoding() throws IOException, InterruptedException {
        CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName(Angular22Generator.GENERATOR_NAME)
                .setInputSpec(Path.of("src/test/resources/fixtures/url-encoded-form-openapi.yaml").toAbsolutePath().toString())
                .setOutputDir(tempDir.toString())
                .addTypeMapping("DateTime", "Date");
        new DefaultGenerator().opts(configurator.toClientOptInput()).generate();

        NodeProcess.assertSucceeds(Path.of("src/test/typescript"), "A URL-encoded form has the wrong body or headers",
                "url-encoded-form.test.mjs", tempDir.resolve("api/login-api.ts").toString(), tempDir.resolve("api/query-params.ts").toString());
    }
}
