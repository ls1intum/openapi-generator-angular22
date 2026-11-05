package de.tum.cit.aet.openapi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;

/** Null membership in schema constraints, independent of model rendering and response-body selection. */
class ResponseNullabilityTest {
    private final OpenAPI api = new OpenAPI().openapi("3.1.1").components(new Components()
            .addSchemas("Payload", new Schema<>().type("object"))
            .addSchemas("NullablePayload", nullable("object"))
            .addSchemas("Cycle", ref("Cycle")));

    @Test
    void emptyAndTypeSpecificKeywordsDoNotExcludeNullWithoutARootType() {
        assertTrue(allows(new Schema<>()));
        assertTrue(allows(new Schema<>().description("Any JSON value")));
        assertTrue(allows(new Schema<>().addProperty("value", new Schema<>().type("string"))));
        assertTrue(allows(new Schema<>().items(new Schema<>().type("string"))));
        assertTrue(allows(new Schema<>().additionalProperties(new Schema<>().type("number"))));
        assertFalse(allows(new Schema<>().type("object").addProperty("value", nullable("string"))));
        assertFalse(allows(new Schema<>().type("array").items(nullable("string"))));
        assertFalse(allows(new Schema<>().type("object").additionalProperties(nullable("number"))));
        assertFalse(allows(new Schema<>().booleanSchemaValue(false)));
        assertTrue(allows(new Schema<>().booleanSchemaValue(true)));
    }

    @Test
    void enumAndConstStillConstrainNullableTypes() {
        assertFalse(allows(nullable("string")._enum(List.of("ready"))));
        assertTrue(allows(nullable("string")._enum(Arrays.asList("ready", null))));
        assertFalse(allows(nullable("string")._const("ready")));
        assertFalse(allows(new Schema<>().type("string").nullable(true)._enum(List.of("ready"))));
        assertTrue(allows(new Schema<>()._enum(Arrays.asList("ready", null))));
        assertFalse(allows(new Schema<>().type("string")._enum(Arrays.asList("ready", null))));
    }

    @Test
    void emptyBranchesObeyIntersectionUnionAndExclusivity() {
        assertTrue(allows(new Schema<>().allOf(List.of(new Schema<>(), ref("NullablePayload")))));
        assertFalse(allows(new Schema<>().allOf(List.of(new Schema<>(), ref("Payload")))));
        assertTrue(allows(new Schema<>().anyOf(List.of(new Schema<>(), ref("Payload")))));
        assertTrue(allows(new Schema<>().oneOf(List.of(new Schema<>(), ref("Payload")))));
        assertFalse(allows(new Schema<>().oneOf(List.of(new Schema<>(), ref("NullablePayload")))));
        assertFalse(allows(new Schema<>().oneOf(List.of(new Schema<>(), new Schema<>()))));
        assertTrue(allows(new Schema<>().anyOf(List.of(new Schema<>(), new Schema<>()))));
    }

    @Test
    void siblingConstraintsApplyTogetherEvenWhenTheRootTypeIncludesNull() {
        assertFalse(allows(nullable("object").allOf(List.of(ref("Payload")))));
        assertFalse(allows(nullable("object").anyOf(List.of(ref("Payload")))));
        assertFalse(allows(nullable("object").oneOf(List.of(ref("Payload")))));
        assertFalse(allows(new Schema<>().type("object").oneOf(List.of(ref("Payload"), new Schema<>().type("null")))));
        assertFalse(allows(new Schema<>().allOf(List.of(ref("NullablePayload")))
                .anyOf(List.of(ref("Payload")))));
        assertFalse(allows(new Schema<>().anyOf(List.of(new Schema<>()))
                .oneOf(List.of(new Schema<>(), ref("NullablePayload")))));
        assertFalse(allows(nullable("string").not(new Schema<>().type("null"))));
        assertTrue(allows(nullable("string").not(new Schema<>().type("number"))));
    }

    @Test
    void referencesRespectSiblingConstraintsAndDoNotManufactureEvidenceForInvalidOrCyclicTargets() {
        assertTrue(allows(ref("NullablePayload")));
        assertFalse(allows(ref("NullablePayload")._enum(List.of("ready"))));
        assertFalse(allows(ref("NullablePayload").allOf(List.of(ref("Payload")))));
        assertFalse(allows(ref("Cycle")));
        assertFalse(allows(ref("Missing")));
        assertFalse(allows(ref("Missing").nullable(true)));
        assertFalse(allows(new Schema<>().$ref("#/unsupported/NullablePayload")));
    }

    @Test
    void retainsTheSupportedOpenApi30NullableReferenceAliasConvention() {
        OpenAPI legacy = new OpenAPI().openapi("3.0.3").components(api.getComponents());
        Schema<Object> alias = new Schema<>();
        alias.setNullable(true);
        alias.setAllOf(List.of(ref("Payload")));
        assertTrue(ResponseTypes.allowsNull(legacy, alias));
        assertFalse(ResponseTypes.allowsNull(legacy, alias._enum(List.of("ready"))));
        assertFalse(ResponseTypes.allowsNull(legacy, new Schema<>().nullable(true).allOf(List.of(ref("Missing")))));
    }

    private boolean allows(Schema<?> schema) {
        return ResponseTypes.allowsNull(api, schema);
    }

    private static Schema<Object> nullable(String type) {
        Schema<Object> schema = new Schema<>();
        schema.setTypes(Set.of(type, "null"));
        return schema;
    }

    private static Schema<Object> ref(String name) {
        Schema<Object> schema = new Schema<>();
        schema.set$ref("#/components/schemas/" + name);
        return schema;
    }
}
