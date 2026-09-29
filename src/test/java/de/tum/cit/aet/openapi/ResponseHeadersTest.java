package de.tum.cit.aet.openapi;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;

class ResponseHeadersTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsFullResponseForOperationsThatDeclareResponseHeaders() throws IOException {
        generate();

        String questionApi = Files.readString(tempDir.resolve("api/question-api.ts"));
        assertTrue(questionApi.contains("import { HttpClient, HttpResponse } from '@angular/common/http';"), questionApi);
        assertTrue(questionApi.contains("getQuestions(page?: number): Observable<HttpResponse<Array<string>>>"), questionApi);
        assertTrue(questionApi.contains("return this.http.get<Array<string>>(url, { observe: 'response' });"), questionApi);
        // A request body must not hide the operation's flag from the template.
        assertTrue(questionApi.contains("createQuestion(body: string): Observable<HttpResponse<string>>"), questionApi);
        assertTrue(questionApi.contains("return this.http.post<string>(url, body, { observe: 'response' });"), questionApi);
        assertTrue(questionApi.contains("importQuestions(title?: string): Observable<HttpResponse<number>>"), questionApi);
        assertTrue(questionApi.contains("return this.http.post<number>(url, formData, { observe: 'response' });"), questionApi);
    }

    @Test
    void keepsBodyOnlyResponseForOperationsWithoutResponseHeaders() throws IOException {
        generate();

        String courseApi = Files.readString(tempDir.resolve("api/course-api.ts"));
        assertTrue(courseApi.contains("import { HttpClient } from '@angular/common/http';"), courseApi);
        assertTrue(courseApi.contains("getCourses(): Observable<Array<string>>"), courseApi);
        assertTrue(courseApi.contains("return this.http.get<Array<string>>(url);"), courseApi);
    }

    private void generate() {
        CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName(Angular22Generator.GENERATOR_NAME)
                .setInputSpec(Path.of("src/test/resources/fixtures/response-headers-openapi.yaml").toAbsolutePath().toString())
                .setOutputDir(tempDir.toString());
        new DefaultGenerator().opts(configurator.toClientOptInput()).generate();
    }
}
