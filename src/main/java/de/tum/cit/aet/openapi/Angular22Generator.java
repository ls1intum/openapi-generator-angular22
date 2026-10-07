/*
 * Copyright (c) 2024 TUM Applied Education Technologies (AET)
 * Licensed under the MIT License
 */
package de.tum.cit.aet.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import org.openapitools.codegen.*;
import org.openapitools.codegen.languages.TypeScriptAngularClientCodegen;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.model.OperationMap;
import org.openapitools.codegen.model.OperationsMap;
import org.openapitools.codegen.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

/**
 * Custom OpenAPI Generator for Angular 22+ with modern best practices.
 *
 * <p>This generator extends the default TypeScript Angular generator and produces
 * a clean, signal-based Angular client. It generates three types of files per API tag:</p>
 *
 * <ol>
 *   <li><b>API Service</b> ({@code *-api.ts}) &mdash; Injectable service with an {@code Observable} method for every
 *       operation, using {@code HttpClient} and the {@code inject()} function.</li>
 *   <li><b>API Resource</b> ({@code *-resources.ts}) &mdash; Signal-based {@code httpResource} wrappers
 *       for GET operations, enabling reactive data fetching. Only generated for tags that have GET operations.</li>
 *   <li><b>Model</b> ({@code *.ts}) &mdash; TypeScript interfaces with readonly properties,
 *       plus const enum objects for runtime enum access (e.g., {@code JobDetailDTOStateEnum.Draft}).</li>
 * </ol>
 *
 * <p>One {@code api/query-params.ts} per client holds {@code appendQueryParam}, which the API and resource files
 * with query parameters import.</p>
 *
 * <p><b>Generation Pipeline</b></p>
 * The generator hooks into four lifecycle stages of the OpenAPI Generator framework:
 * <ol>
 *   <li>{@link #processOpts()} &mdash; Reads CLI options and registers mustache templates.</li>
 *   <li>{@link #processOpenAPI(OpenAPI)} &mdash; Scans paths to determine which tags need resource files.</li>
 *   <li>{@link #postProcessAllModels(Map)} &mdash; Marks models as readonly or mutable, and requires the properties
 *       a subtype restates when its parent requires them.</li>
 *   <li>{@link #postProcessOperationsWithModels(OperationsMap, List)} &mdash; Builds the HttpClient calls,
 *       resource functions and URL templates, and collects imports.</li>
 * </ol>
 *
 * <p><b>Naming Conventions</b></p>
 * <ul>
 *   <li>File names: kebab-case (e.g., {@code job-resource-api.ts}, {@code job-detail-dto.ts})</li>
 *   <li>Class names: PascalCase with {@code Api} suffix (e.g., {@code JobResourceApi})</li>
 *   <li>Operation IDs: camelCase, stripped of leading underscores and trailing digits</li>
 * </ul>
 */
public class Angular22Generator extends TypeScriptAngularClientCodegen {

    private static final Logger LOGGER = LoggerFactory.getLogger(Angular22Generator.class);


    /** Identifiers that a generated service method or resource function declares or calls next to its parameters. */
    private static final Set<String> TEMPLATE_LOCALS = Set.of("url", "queryParams", "queryString", "formData", "headers",
            "searchParams", "query", "params", "appendQueryParam", "httpResource", "inject");

    /** Generator name used by the OpenAPI Generator SPI and CLI. */
    public static final String GENERATOR_NAME = "angular22";
    /** Config option for enabling httpResource-based GET resources. */
    public static final String USE_HTTP_RESOURCE = "useHttpResource";
    /** Config option for enabling inject() instead of constructor injection. */
    public static final String USE_INJECT_FUNCTION = "useInjectFunction";
    /** Config option for generating separate resource files for GET operations. */
    public static final String SEPARATE_RESOURCES = "separateResources";
    /** Config option for adding readonly modifiers to response models. */
    public static final String READONLY_MODELS = "readonlyModels";

    /** Whether to generate httpResource-based GET resources. */
    protected boolean useHttpResource = true;
    /** Whether to use Angular inject() for service dependencies. */
    protected boolean useInjectFunction = true;
    /** Whether to place GET resources in separate files. */
    protected boolean separateResources = true;
    /** Whether to add readonly modifiers to response models. */
    protected boolean readonlyModels = true;

    // =============================================================================================
    // 1) Constructor &mdash; Register templates, set naming conventions, define CLI options
    // =============================================================================================

    /**
     * Initializes the Angular 22 generator with custom templates, naming conventions, and CLI options.
     *
     * <p>Registers the template files:</p>
     * <ul>
     *   <li>{@code model.mustache} &rarr; model TypeScript files</li>
     *   <li>{@code api-service.mustache} &rarr; API service files ({@code *-api.ts})</li>
     *   <li>{@code api-resource.mustache} &rarr; httpResource files ({@code *-resources.ts}),
     *       conditionally added in {@link #processOpts()}</li>
     *   <li>{@code query-params.mustache} &rarr; {@code api/query-params.ts}, added in {@link #processOpts()}</li>
     * </ul>
     */
    public Angular22Generator() {
        super();

        embeddedTemplateDir = templateDir = GENERATOR_NAME;
        outputFolder = "generated-code" + File.separator + GENERATOR_NAME;

        modelTemplateFiles.clear();
        modelTemplateFiles.put("model.mustache", ".ts");

        apiNameSuffix = "Api";
        // The parent strips serviceSuffix off every API class name to derive a file name, and fails on names shorter
        // than its default "Service" (e.g. FaqApi). Keep it equal to the suffix toApiName appends.
        serviceSuffix = apiNameSuffix;
        // JSON has no sets: a response arrives as an array, and JSON.stringify sends a Set as {}.
        typeMapping.put("set", "Array");
        apiTemplateFiles.clear();
        apiTemplateFiles.put("api-service.mustache", "-api.ts");

        supportingFiles.clear();

        cliOptions.add(new CliOption(USE_HTTP_RESOURCE,
                "Use httpResource for GET requests (signal-based reactive fetching)")
                .defaultValue("true"));
        cliOptions.add(new CliOption(USE_INJECT_FUNCTION,
                "Use inject() function instead of constructor injection")
                .defaultValue("true"));
        cliOptions.add(new CliOption(SEPARATE_RESOURCES,
                "Generate separate resource files for GET operations")
                .defaultValue("true"));
        cliOptions.add(new CliOption(READONLY_MODELS,
                "Add readonly modifier to model properties")
                .defaultValue("true"));
    }

    /**
     * Returns the unique identifier for this generator, used by the CLI ({@code -g angular22}).
     *
     * @return the generator name ({@code "angular22"})
     */
    @Override
    public String getName() {
        return GENERATOR_NAME;
    }

    /**
     * Returns a human-readable description shown in the CLI help output.
     *
     * @return a description of this generator's capabilities
     */
    @Override
    public String getHelp() {
        return "Generates Angular 22 client code with modern best practices including " +
                "httpResource for GET requests, inject() function, and signal-based reactivity.";
    }

    // =============================================================================================
    // 2) processOpts &mdash; Read CLI options and conditionally register the resource template
    // =============================================================================================

    /**
     * Reads user-provided CLI options from {@code --additional-properties} and configures
     * the generator accordingly.
     *
     * <p>Each boolean option (useHttpResource, useInjectFunction, separateResources, readonlyModels)
     * defaults to {@code true} if not explicitly provided. When both {@code useHttpResource} and
     * {@code separateResources} are enabled, the {@code api-resource.mustache} template is registered
     * to generate signal-based httpResource wrapper files.</p>
     */
    @Override
    public void processOpts() {
        super.processOpts();

        supportingFiles.clear();
        supportingFiles.add(new SupportingFile("query-params.mustache", "api", "query-params.ts"));

        if (additionalProperties.containsKey(USE_HTTP_RESOURCE)) {
            useHttpResource = Boolean.parseBoolean(additionalProperties.get(USE_HTTP_RESOURCE).toString());
        }
        additionalProperties.put(USE_HTTP_RESOURCE, useHttpResource);

        if (additionalProperties.containsKey(USE_INJECT_FUNCTION)) {
            useInjectFunction = Boolean.parseBoolean(additionalProperties.get(USE_INJECT_FUNCTION).toString());
        }
        additionalProperties.put(USE_INJECT_FUNCTION, useInjectFunction);

        if (additionalProperties.containsKey(SEPARATE_RESOURCES)) {
            separateResources = Boolean.parseBoolean(additionalProperties.get(SEPARATE_RESOURCES).toString());
        }
        additionalProperties.put(SEPARATE_RESOURCES, separateResources);

        if (additionalProperties.containsKey(READONLY_MODELS)) {
            readonlyModels = Boolean.parseBoolean(additionalProperties.get(READONLY_MODELS).toString());
        }
        additionalProperties.put(READONLY_MODELS, readonlyModels);

        if (useHttpResource && separateResources) {
            apiTemplateFiles.put("api-resource.mustache", "-resources.ts");
        }

        LOGGER.info("Angular22 Generator initialized with: useHttpResource={}, useInjectFunction={}, " +
                "separateResources={}, readonlyModels={}",
                useHttpResource, useInjectFunction, separateResources, readonlyModels);
    }

    // =============================================================================================
    // 3) processOpenAPI &mdash; Scan the spec to determine which tags need resource files
    // =============================================================================================

    /**
     * Scans all paths in the OpenAPI spec to find the tags that have GET operations. Tags without any GET
     * operations get their resource file added to the generator's ignore list, since there is nothing to wrap
     * in an httpResource.
     *
     * @param openAPI the parsed OpenAPI specification
     */
    @Override
    public void processOpenAPI(OpenAPI openAPI) {
        super.processOpenAPI(openAPI);

        if (openapiGeneratorIgnoreList == null) {
            openapiGeneratorIgnoreList = new HashSet<>();
        }

        Map<String, TagUsage> usageByTag = new HashMap<>();
        if (openAPI != null && openAPI.getPaths() != null) {
            openAPI.getPaths().forEach((path, pathItem) -> {
                if (pathItem == null) {
                    return;
                }
                addOperationUsage(pathItem.getGet(), true, usageByTag);
                addOperationUsage(pathItem.getPost(), false, usageByTag);
                addOperationUsage(pathItem.getPut(), false, usageByTag);
                addOperationUsage(pathItem.getDelete(), false, usageByTag);
                addOperationUsage(pathItem.getPatch(), false, usageByTag);
                addOperationUsage(pathItem.getHead(), false, usageByTag);
                addOperationUsage(pathItem.getOptions(), false, usageByTag);
                addOperationUsage(pathItem.getTrace(), false, usageByTag);
            });
        }

        for (Map.Entry<String, TagUsage> entry : usageByTag.entrySet()) {
            String apiFilename = toApiFilename(entry.getKey());
            TagUsage usage = entry.getValue();
            if (!useHttpResource || !separateResources) {
                // No resource files when httpResource is disabled or resources are inline
                openapiGeneratorIgnoreList.add("api/" + apiFilename + "-resources.ts");
            } else if (!usage.hasGet) {
                // No resource file for tags without GET operations
                openapiGeneratorIgnoreList.add("api/" + apiFilename + "-resources.ts");
            }
            // Service files are always generated — they contain Observable methods for all operations
        }
    }

    /**
     * Records each tag of a single operation, and whether the operation is a GET.
     * Operations without tags are assigned to the "default" tag.
     *
     * @param operation  the OpenAPI operation to classify (may be {@code null})
     * @param isGet      {@code true} if this is a GET operation, {@code false} for mutations
     * @param usageByTag the map accumulating the GET flag per tag
     */
    private void addOperationUsage(Operation operation, boolean isGet, Map<String, TagUsage> usageByTag) {
        if (operation == null) {
            return;
        }

        List<String> tags = operation.getTags();
        if (tags == null || tags.isEmpty()) {
            tags = Collections.singletonList("default");
        }
        for (String tag : tags) {
            String sanitizedTag = sanitizeTag(tag);
            TagUsage usage = usageByTag.computeIfAbsent(sanitizedTag, key -> new TagUsage());
            if (isGet) {
                usage.hasGet = true;
            }
        }
    }

    /** Tracks whether a given API tag has GET operations. */
    private static final class TagUsage {
        private boolean hasGet;
    }

    // =============================================================================================
    // 4) postProcessAllModels &mdash; Mark models as readonly or mutable
    // =============================================================================================

    /**
     * Post-processes all generated models to determine which should have readonly properties.
     *
     * <p>Models whose names end with {@code Create}, {@code Update}, {@code Request}, or {@code Input}
     * are considered input DTOs and get mutable properties. All other models are treated as output
     * DTOs and receive the {@code readonly} modifier on every property.</p>
     *
     * <p>The decision is passed to the mustache template via vendor extensions:</p>
     * <ul>
     *   <li>{@code x-is-readonly} on each property &mdash; whether to emit the {@code readonly} keyword</li>
     *   <li>{@code x-property-name} on each property &mdash; the property key, see {@link TypeScriptSnippets#toPropertyKey(String)}</li>
     * </ul>
     *
     * @param objs the map of all models, keyed by model name
     * @return the post-processed models map
     */
    @Override
    public Map<String, ModelsMap> postProcessAllModels(Map<String, ModelsMap> objs) {
        Map<String, ModelsMap> result = super.postProcessAllModels(objs);

        for (ModelsMap modelsMap : result.values()) {
            for (ModelMap modelMap : modelsMap.getModels()) {
                CodegenModel model = modelMap.getModel();
                requireWhatAncestorsRequire(model);
                modelMap.put("tsImports", importsFromClassFiles(modelMap));

                boolean isInputDto = model.name.endsWith("Create") ||
                        model.name.endsWith("Update") ||
                        model.name.endsWith("Request") ||
                        model.name.endsWith("Input");

                for (CodegenProperty property : model.vars) {
                    property.vendorExtensions.put("x-is-readonly", readonlyModels && !isInputDto);
                    property.vendorExtensions.put("x-property-name", TypeScriptSnippets.toPropertyKey(property.baseName));
                }
            }
        }

        return result;
    }

    /**
     * Makes a subtype's property required when an ancestor interface requires it.
     *
     * <p>A schema built as {@code allOf: [{$ref: Parent}, {properties: {...}}]} becomes an interface that
     * {@code extends Parent}. When the inline part restates a required parent property without requiring it
     * (typically the discriminator), the child would declare {@code type?: string} against the parent's
     * {@code type: string}, which TypeScript rejects (TS2430). A required redeclaration is compatible, and a
     * redeclaration that narrows the type keeps its narrower type.</p>
     *
     * @param model the model to adjust; models without a parent stay unchanged
     */
    private static void requireWhatAncestorsRequire(CodegenModel model) {
        Set<String> required = new HashSet<>();
        for (CodegenModel ancestor = model.parentModel; ancestor != null; ancestor = ancestor.parentModel) {
            for (CodegenProperty property : ancestor.vars) {
                if (property.required) {
                    required.add(property.baseName);
                }
            }
        }
        for (CodegenProperty property : model.vars) {
            if (required.contains(property.baseName)) {
                property.required = true;
            }
        }
    }

    // =============================================================================================
    // 5) postProcessOperationsWithModels &mdash; Build calls, resources and URL templates, collect imports
    // =============================================================================================

    /**
     * Post-processes all operations for a single API tag. This is the main processing step
     * that prepares the data consumed by the mustache templates.
     *
     * <p>Processing steps:</p>
     * <ol>
     *   <li>Per operation: annotate the path, query and form parameters, build the {@code HttpClient} call and the
     *       URL template literal from the path as the spec writes it (the parent's {@code x-path-from-spec}), and for
     *       a GET also the httpResource function</li>
     *   <li>Per file: set the flags that decide which imports and helpers the file needs, and map the referenced
     *       models to kebab-case file names</li>
     * </ol>
     *
     * @param objs      the operations map for the current API tag
     * @param allModels all models available in the spec
     * @return the post-processed operations map with additional template data
     */
    @Override
    public OperationsMap postProcessOperationsWithModels(OperationsMap objs, List<ModelMap> allModels) {
        OperationsMap result = super.postProcessOperationsWithModels(objs, allModels);

        OperationMap operations = result.getOperations();
        List<CodegenOperation> ops = operations.getOperation();
        List<CodegenOperation> getOperations = new ArrayList<>();

        for (CodegenOperation op : ops) {
            TypeScriptSnippets.ResponseKind response = TypeScriptSnippets.ResponseKind.of(op);
            Map<String, TypeScriptSnippets.Locals> locals = TypeScriptSnippets.allocateLocals(op, TEMPLATE_LOCALS);
            TypeScriptSnippets.processPathParameters(op, locals);
            TypeScriptSnippets.processHeaderParameters(op, locals);
            processQueryParameters(op);
            TypeScriptSnippets.processFormParameters(op);
            TypeScriptSnippets.buildHttpCall(op, response);

            String specPath = op.vendorExtensions.get("x-path-from-spec").toString();
            op.vendorExtensions.put("xPathTemplate", TypeScriptSnippets.buildPathTemplate(op, specPath, false, locals));

            if ("GET".equalsIgnoreCase(op.httpMethod)) {
                op.vendorExtensions.put("x-is-get", true);
                TypeScriptSnippets.buildResourceFunction(op, response, TypeScriptSnippets.buildPathTemplate(op, specPath, true, locals));
                getOperations.add(op);
            }
        }

        operations.put("hasInlineResources", useHttpResource && !separateResources && !getOperations.isEmpty());
        // The service class holds every operation, including the GETs of the inline resources in the same file;
        // the separate resources file holds only the GETs.
        operations.put("hasQueryParams", ops.stream().anyMatch(op -> !op.queryParams.isEmpty()));
        operations.put("hasResourceQueryParams", getOperations.stream().anyMatch(op -> !op.queryParams.isEmpty()));
        operations.put("hasFileResponses", ops.stream()
                .anyMatch(op -> TypeScriptSnippets.ResponseKind.of(op) == TypeScriptSnippets.ResponseKind.FILE));
        operations.put("hasSignalArguments", getOperations.stream()
                .anyMatch(op -> !op.pathParams.isEmpty() || !op.headerParams.isEmpty() || !op.queryParams.isEmpty()));

        result.put("tsImports", toTsImports(ops));
        result.put("resourceTsImports", toTsImports(getOperations));

        return result;
    }

    private List<Map<String, String>> toTsImports(List<CodegenOperation> ops) {
        Set<String> modelImports = new LinkedHashSet<>();
        for (CodegenOperation op : ops) {
            modelImports.addAll(op.imports);
        }
        return modelImports.stream().map(Angular22Generator::tsImport).toList();
    }

    private static Map<String, String> tsImport(String className) {
        return Map.of("classname", className, "filename", classFilename(className));
    }

    /**
     * Processes query parameters for a single operation: generates a TypeScript interface name
     * for the grouped query params and the property name of each parameter in it.
     *
     * <p>Sets vendor extensions on the operation:</p>
     * <ul>
     *   <li>{@code x-params-interface-name} &mdash; PascalCase interface name (e.g., {@code GetJobsParams})</li>
     *   <li>{@code x-all-query-params-optional} &mdash; whether the resource's {@code params} argument may be left out</li>
     * </ul>
     *
     * <p>Sets {@code x-query-key} on each query parameter: the property name in the params interface. It is the
     * parent's identifier for the wire name without the {@code _} that escapes a reserved word, since a property may
     * have that name, and without the {@code Param} suffix of {@link #toParamName}. A digit suffix keeps it unique
     * within the operation, as the parent does for identifiers. {@code x-wire-name-literal} is the wire name as a
     * TypeScript string literal, and {@code x-query-style-args} passes a style other than form with explode on to
     * {@code appendQueryParam}.</p>
     *
     * @param op the operation whose query parameters should be processed
     */
    private void processQueryParameters(CodegenOperation op) {
        if (op.queryParams.isEmpty()) {
            return;
        }
        op.vendorExtensions.put("x-params-interface-name", TypeScriptSnippets.paramsInterfaceName(op));
        op.vendorExtensions.put("x-all-query-params-optional", TypeScriptSnippets.allQueryParamsOptional(op));
        Set<String> keys = new HashSet<>();
        for (CodegenParameter param : op.queryParams) {
            String identifier = super.toParamName(param.baseName);
            String key = identifier.startsWith("_") && isReservedWord(identifier.substring(1)) ? identifier.substring(1) : identifier;
            param.vendorExtensions.put("x-query-key", TypeScriptSnippets.allocate(keys, key));
            param.vendorExtensions.put("x-wire-name-literal", TypeScriptSnippets.stringLiteral(param.baseName));
            param.vendorExtensions.put("x-query-style-args", TypeScriptSnippets.queryStyleArguments(op, param));
        }
    }

    // =============================================================================================
    // Naming Convention Overrides
    // =============================================================================================

    /**
     * Converts a schema name to the kebab-case filename of its model, derived from the class name.
     *
     * <p>The generator calls this with the raw schema name when it writes a model file, e.g. the inline schema
     * {@code getExam_200_response} of class {@code GetExam200Response}. An import holds the class name, to which
     * {@link #toModelName(String)} would add the prefixes and suffixes again, so imports go through
     * {@code classFilename} instead.</p>
     *
     * @param name the schema name (e.g., {@code JobDetailDTO})
     * @return the kebab-case filename without extension (e.g., {@code job-detail-dto})
     */
    @Override
    public String toModelFilename(String name) {
        return classFilename(toModelName(name));
    }

    /**
     * Keeps the models that the parent's {@code postProcessAllModels} imports into a model file, but names each file
     * after its class, as {@link #toModelFilename} and the API imports do. The parent names it
     * {@code toModelFilename(removeModelPrefixSuffix(className))}, which applies {@code modelSuffix} twice when
     * {@code modelNameSuffix} is set too, because the suffix it strips is no longer at the end of the name.
     */
    private static List<Map<String, String>> importsFromClassFiles(ModelMap modelMap) {
        List<?> parentImports = (List<?>) modelMap.get("tsImports");
        return parentImports.stream().map(entry -> tsImport((String) ((Map<?, ?>) entry).get("classname"))).toList();
    }

    private static String classFilename(String className) {
        return Names.toKebabCase(className);
    }

    /**
     * Converts an API tag name to a kebab-case filename.
     *
     * @param name the API tag name (e.g., {@code JobResource})
     * @return the kebab-case filename without extension (e.g., {@code job-resource})
     */
    @Override
    public String toApiFilename(String name) {
        return Names.toKebabCase(name);
    }

    /**
     * Converts an API tag name to a PascalCase class name with the {@code Api} suffix.
     *
     * @param name the API tag name (e.g., {@code job-resource})
     * @return the PascalCase class name (e.g., {@code JobResourceApi})
     */
    @Override
    public String toApiName(String name) {
        return StringUtils.camelize(name) + "Api";
    }

    /**
     * Returns the TypeScript identifier of a parameter. On top of the parent's escaping of reserved words, a name that
     * the generated method bodies declare or call (e.g. a query parameter {@code url} next to {@code const url = ...})
     * gets the suffix {@code Param}. The parent names every parameter here before it copies
     * the parameter into the operation's lists, so every copy carries the same name; the wire name ({@code baseName})
     * stays.
     *
     * @param name the parameter name in the OpenAPI document
     * @return the identifier used for the parameter in the generated code
     */
    @Override
    public String toParamName(String name) {
        String identifier = super.toParamName(name);
        return TEMPLATE_LOCALS.contains(identifier) ? identifier + "Param" : identifier;
    }

    /**
     * Cleans up operation IDs by stripping leading underscores and trailing digits
     * that the OpenAPI spec sometimes adds to disambiguate overloaded endpoints.
     *
     * @param operationId the raw operation ID from the OpenAPI spec
     * @return the cleaned operation ID, or {@code "operation"} if the result would be blank
     */
    @Override
    public String toOperationId(String operationId) {
        String name = super.toOperationId(operationId);
        String normalized = name.replaceFirst("^_+", "");
        normalized = normalized.replaceFirst("\\d+$", "");
        if (normalized.isBlank()) {
            normalized = "operation";
        }
        return normalized;
    }

    // =============================================================================================
    // Output Directory Configuration
    // =============================================================================================

    /**
     * Returns the generator type, which determines how the OpenAPI Generator CLI categorizes it.
     *
     * @return {@link CodegenType#CLIENT}
     */
    @Override
    public CodegenType getTag() {
        return CodegenType.CLIENT;
    }

    /**
     * Returns the output folder for generated API files.
     *
     * @return the path to the {@code api/} subdirectory within the output folder
     */
    @Override
    public String apiFileFolder() {
        return outputFolder + File.separator + "api";
    }

    /**
     * Returns the output folder for generated model files.
     *
     * @return the path to the {@code model/} subdirectory within the output folder
     */
    @Override
    public String modelFileFolder() {
        return outputFolder + File.separator + "model";
    }
}
