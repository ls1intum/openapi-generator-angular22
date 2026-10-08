package de.tum.cit.aet.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;

class OneOfModelTest {

    @TempDir
    Path tempDir;

    @Test
    void rendersOneOfSchemaAsUnionOfItsBranches() throws IOException {
        CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName(Angular22Generator.GENERATOR_NAME)
                .setInputSpec(Path.of("src/test/resources/fixtures/one-of-openapi.yaml").toAbsolutePath().toString())
                .setOutputDir(tempDir.toString());
        new DefaultGenerator().opts(configurator.toClientOptInput()).generate();

        String model = Files.readString(tempDir.resolve("model/question-create.ts"));
        assertTrue(model.contains("export type QuestionCreate = MultipleChoiceQuestionCreate | ShortAnswerQuestionCreate;"), model);
        // Only the branches are imported; the property types of the branches belong to the branch files.
        List<String> imports = model.lines().filter(line -> line.startsWith("import")).toList();
        assertEquals(List.of(
                "import type { MultipleChoiceQuestionCreate } from './multiple-choice-question-create';",
                "import type { ShortAnswerQuestionCreate } from './short-answer-question-create';"), imports);
        assertFalse(model.contains("interface QuestionCreate"), model);

        String branch = Files.readString(tempDir.resolve("model/multiple-choice-question-create.ts"));
        assertTrue(branch.contains("export interface MultipleChoiceQuestionCreate"), branch);
    }
}
