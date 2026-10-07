package de.tum.cit.aet.openapi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class TemplateIdentifiersTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/angular22");
    private static final Pattern CONST = Pattern.compile("\\bconst ([A-Za-z_$][\\w$]*)");
    private static final Pattern IMPORT = Pattern.compile("^import \\{(.*)\\} from", Pattern.MULTILINE);

    @Test
    void renamesParametersNamedLikeAFixedIdentifierOfTheGeneratedBodies() throws IOException {
        Set<String> identifiers = new TreeSet<>();
        for (String template : new String[] {"api-service.mustache", "api-resource.mustache", "resourceFunction.mustache"}) {
            String source = Files.readString(TEMPLATES.resolve(template)).replaceAll("\\{\\{[^}]*}}", "");
            Matcher constant = CONST.matcher(source);
            while (constant.find()) {
                identifiers.add(constant.group(1));
            }
            Matcher imports = IMPORT.matcher(source);
            while (imports.find()) {
                for (String name : imports.group(1).split(",")) {
                    if (!name.isBlank() && Character.isLowerCase(name.trim().charAt(0))) {
                        identifiers.add(name.trim());
                    }
                }
            }
        }

        assertFalse(identifiers.isEmpty());
        Angular22Generator generator = new Angular22Generator();
        for (String identifier : identifiers) {
            assertNotEquals(identifier, generator.toParamName(identifier), "A parameter named " + identifier + " would shadow it");
        }
    }
}
