package de.tum.cit.aet.openapi;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;

class UrlEncodedFormTest {

    @TempDir
    Path tempDir;

    @Test
    void sendsEachFormFieldInTheStyleOfItsEncoding() throws IOException, InterruptedException {
        generateAndCheck(Files.readString(Path.of("src/test/resources/fixtures/url-encoded-form-openapi.yaml")), false);
    }

    @Test
    void preservesEncodingsOnParameterizedPaths() throws IOException, InterruptedException {
        String spec = Files.readString(Path.of("src/test/resources/fixtures/url-encoded-form-openapi.yaml"))
                .replace("/api/login:", "/api/login/{tenant}:")
                .replace("      parameters:\n", "      parameters:\n        - name: tenant\n          in: path\n          required: true\n          schema:\n            type: string\n");
        generateAndCheck(spec, true);
    }

    @Test
    void preservesEncodingsWithMediaTypeParameters() throws IOException, InterruptedException {
        String spec = Files.readString(Path.of("src/test/resources/fixtures/url-encoded-form-openapi.yaml"))
                .replace("application/x-www-form-urlencoded:", "application/x-www-form-urlencoded; charset=utf-8:");
        generateAndCheck(spec, false);
    }

    private void generateAndCheck(String spec, boolean pathParameter) throws IOException, InterruptedException {
        Path input = tempDir.resolve("input.yaml");
        Files.writeString(input, spec);
        CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName(Angular22Generator.GENERATOR_NAME)
                .setInputSpec(input.toString())
                .setOutputDir(tempDir.toString())
                .addTypeMapping("DateTime", "Date");
        new DefaultGenerator().opts(configurator.toClientOptInput()).generate();

        NodeProcess.assertSucceeds(Path.of("src/test/typescript"), "A URL-encoded form has the wrong body or headers",
                "url-encoded-form.test.mjs", tempDir.resolve("api/login-api.ts").toString(), tempDir.resolve("api/query-params.ts").toString(), Boolean.toString(pathParameter));
    }
}
