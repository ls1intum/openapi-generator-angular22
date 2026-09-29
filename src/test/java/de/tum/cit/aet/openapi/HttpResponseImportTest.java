package de.tum.cit.aet.openapi;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;

class HttpResponseImportTest {

    @TempDir
    Path tempDir;

    @Test
    void importsHttpResponseOnlyInServicesThatUseIt() throws IOException {
        CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName(Angular22Generator.GENERATOR_NAME)
                .setInputSpec(Path.of("src/test/resources/fixtures/http-response-import-openapi.yaml").toAbsolutePath().toString())
                .setOutputDir(tempDir.toString());
        new DefaultGenerator().opts(configurator.toClientOptInput()).generate();

        String courseApi = Files.readString(tempDir.resolve("api/course-api.ts"));
        assertTrue(courseApi.contains("import { HttpClient } from '@angular/common/http';"), courseApi);

        String exportApi = Files.readString(tempDir.resolve("api/export-api.ts"));
        assertTrue(exportApi.contains("import { HttpClient, HttpResponse } from '@angular/common/http';"), exportApi);
        assertTrue(exportApi.contains("exportCourse(courseId: number): Observable<HttpResponse<Blob>>"), exportApi);
    }
}
