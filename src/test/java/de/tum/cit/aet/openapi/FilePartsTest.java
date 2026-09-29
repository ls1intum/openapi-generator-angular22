package de.tum.cit.aet.openapi;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;

class FilePartsTest {

    @TempDir
    Path tempDir;

    @Test
    void sendsFilePartsAsFilesWithTheirName() throws IOException {
        CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName(Angular22Generator.GENERATOR_NAME)
                .setInputSpec(Path.of("src/test/resources/fixtures/file-parts-openapi.yaml").toAbsolutePath().toString())
                .setOutputDir(tempDir.toString());
        new DefaultGenerator().opts(configurator.toClientOptInput()).generate();

        String api = Files.readString(tempDir.resolve("api/upload-api.ts"));
        assertTrue(api.contains("uploadFiles(title?: string, cover?: File, files?: Array<File>): Observable<void>"), api);
        // Without the third argument the browser names every part "blob".
        assertTrue(api.contains("formData.append('cover', cover, cover.name);"), api);
        assertTrue(api.contains("files.forEach(item => formData.append('files', item, item.name));"), api);
        assertTrue(api.contains("formData.append('title', title);"), api);
        // Downloads keep Blob, which is what HttpClient returns for responseType 'blob'.
        assertTrue(api.contains("downloadArchive(): Observable<HttpResponse<Blob>>"), api);
    }
}
