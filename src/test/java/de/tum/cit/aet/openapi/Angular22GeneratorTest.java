package de.tum.cit.aet.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;

class Angular22GeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesServicesAndResourcesForTutorialGroups() throws IOException {
        generateFixture("fixtures/tutorial-groups-openapi.yaml", Map.of());

        String api = Files.readString(tempDir.resolve("api/tutorial-group-api.ts"));
        assertContains(api, "export class TutorialGroupApi");
        assertContains(api, "import { TutorialGroupDetailData } from '../model/tutorial-group-detail-data';");
        assertContains(api, "import { CreateOrUpdateTutorialGroupRequest } from '../model/create-or-update-tutorial-group-request';");
        // GETs stay available as Observable methods next to the resources.
        assertContains(api, "getTutorialGroups(courseId: number, registered?: boolean, campus?: Array<string>): Observable<Array<TutorialGroupDetailData>>");
        assertContains(api, "const url = `${this.basePath}/tutorialgroup/courses/${courseId}/tutorial-groups${queryString ? `?${queryString}` : ''}`;");
        assertContains(api, "return this.http.post<TutorialGroupDetailData>(url, createOrUpdateTutorialGroupRequest);");
        assertContains(api, "return this.http.delete<void>(url);");
        assertContains(api, "return this.http.get(url, { responseType: 'blob', observe: 'response' });");
        assertContains(api, "return this.http.get(url, { responseType: 'text' });");

        String resources = Files.readString(tempDir.resolve("api/tutorial-group-resources.ts"));
        assertContains(resources, "export function getTutorialGroupsResource(courseId: Signal<number | undefined> | number, params?: Signal<GetTutorialGroupsParams>): HttpResourceRef<Array<TutorialGroupDetailData> | undefined>");
        assertContains(resources, "return `${BASE_PATH}/tutorialgroup/courses/${courseIdValue}/tutorial-groups${query ? `?${query}` : ''}`;");
        // An id that is not known yet (for example before the route resolved) skips the request instead of calling .../undefined.
        assertContains(resources, "if (courseIdValue === undefined) {\n            return undefined;\n        }");
        assertContains(resources, "export function searchTutorialGroupsResource(courseId: Signal<number | undefined> | number, params: Signal<SearchTutorialGroupsParams>)");
        // Non-JSON GETs must not go through the JSON parser.
        assertContains(resources, "getTutorialGroupAvatarResource(courseId: Signal<number | undefined> | number, tutorialGroupId: Signal<number | undefined> | number): HttpResourceRef<Blob | undefined>");
        assertContains(resources, "return httpResource.blob(() => {");
        assertContains(resources, "return httpResource.text(() => {");
        assertFalse(resources.contains("httpResource<Blob>"));
        assertFalse(resources.contains("httpResource<string>"));

        String configurationModel = Files.readString(tempDir.resolve("model/tutorial-group-configuration.ts"));
        assertContains(configurationModel, "import type { TutorialGroupFreePeriod } from './tutorial-group-free-period';");

        assertContains(Files.readString(tempDir.resolve("model/tutorial-group-detail-data.ts")), "readonly title: string;");
        String requestModel = Files.readString(tempDir.resolve("model/create-or-update-tutorial-group-request.ts"));
        assertContains(requestModel, "title: string;");
        assertFalse(requestModel.contains("readonly title"));
    }

    @Test
    void generatesObservableGetsOnlyWhenHttpResourceIsDisabled() throws IOException {
        generateFixture("fixtures/tutorial-groups-openapi.yaml", Map.of("useHttpResource", "false", "separateResources", "false"));

        assertFalse(Files.exists(tempDir.resolve("api/tutorial-group-resources.ts")));
        String api = Files.readString(tempDir.resolve("api/tutorial-group-api.ts"));
        assertContains(api, "getTutorialGroups(courseId: number, registered?: boolean, campus?: Array<string>): Observable<Array<TutorialGroupDetailData>>");
        assertFalse(api.contains("httpResource"));
    }

    @Test
    void generatesApisWhoseClassNameIsShorterThanTheServiceSuffix() throws IOException {
        // FaqApi has fewer characters than the parent generator's default service suffix "Service".
        generateFixture("fixtures/short-tag-openapi.yaml", Map.of());

        assertContains(Files.readString(tempDir.resolve("api/faq-api.ts")), "export class FaqApi");
    }

    @Test
    void appendsMultipartFieldsAsBlobsStringsOrJsonParts() throws IOException {
        generateFixture("fixtures/multipart-openapi.yaml", Map.of());

        String api = Files.readString(tempDir.resolve("api/upload-api.ts"));
        // Objects, arrays of objects and maps go out as one JSON part each, which Spring's @RequestPart reads.
        assertContains(api, """
                        if (course !== undefined && course !== null) {
                            formData.append('course', new Blob([JSON.stringify(course)], { type: 'application/json' }));
                        }
                """);
        assertContains(api, "formData.append('pages', new Blob([JSON.stringify(Array.from(pages))], { type: 'application/json' }));");
        assertContains(api, "formData.append('labels', new Blob([JSON.stringify(labels)], { type: 'application/json' }));");
        // File parts are typed File: FormData sends a File with its name, and a plain Blob as "blob".
        assertContains(api, "createUpload(course: CourseCreate, file?: File, files?: Array<File>, name?: string,");
        // Only Blob becomes File, so a nullable item keeps its null.
        assertContains(api, "nullableFiles?: Array<File | null>");
        // Binary fields are appended as they are.
        assertContains(api, "formData.append('file', file);");
        assertContains(api, "files.forEach(item => { if (item !== undefined && item !== null) { formData.append('files', item); } });");
        // Scalars and enums become strings, the only non-Blob value FormData accepts.
        assertContains(api, "formData.append('name', String(name));");
        assertContains(api, "formData.append('count', String(count));");
        assertContains(api, "formData.append('ratio', String(ratio));");
        assertContains(api, "formData.append('active', String(active));");
        assertContains(api, "formData.append('mode', String(mode));");
        assertContains(api, "return this.http.post<CourseCreate>(url, formData);");
    }

    @Test
    void passesDeleteRequestBodiesInTheOptionsObject() throws IOException {
        generateFixture("fixtures/delete-body-openapi.yaml", Map.of());

        String api = Files.readString(tempDir.resolve("api/user-api.ts"));
        // HttpClient.delete takes (url, options); a body passed as the second argument selects the wrong overload.
        assertContains(api, "return this.http.delete<void>(url, { body: bulkUserDeletionRequest });");
        assertContains(api, "return this.http.delete<DeletionSummary>(url, { body: permanentUserDeletionRequest });");
    }

    @Test
    void readsTextResponsesOfNonGetOperationsAsText() throws IOException {
        generateFixture("fixtures/text-response-openapi.yaml", Map.of());

        String api = Files.readString(tempDir.resolve("api/programming-exercise-api.ts"));
        // A text/plain body is not JSON; without responseType: 'text' HttpClient fails to parse it.
        assertContains(api, "return this.http.put(url, generateTestsRequest, { responseType: 'text' });");
        assertContains(api, "return this.http.post(url, null, { responseType: 'text' });");
        assertContains(api, "return this.http.delete(url, { body: revokeTokenRequest, responseType: 'text' });");
        // A JSON string keeps the JSON parser.
        assertContains(api, """
                    renameExercise(exerciseId: number): Observable<string> {
                        const url = `${this.basePath}/api/programming-exercises/${exerciseId}/name`;
                        return this.http.post<string>(url, null);
                """);
    }

    @Test
    void returnsTheFullResponseWhenASuccessResponseDeclaresHeaders() throws IOException {
        generateFixture("fixtures/response-headers-openapi.yaml", Map.of("observeResponseHeaders", "true"));

        String api = Files.readString(tempDir.resolve("api/question-api.ts"));
        assertContains(api, """
                    getQuestions(page?: number): Observable<HttpResponse<Array<Question>>> {
                """);
        assertContains(api, "return this.http.get<Array<Question>>(url, { observe: 'response' });");
        // A text body keeps its responseType next to observe, or HttpClient parses it as JSON.
        assertContains(api, """
                    exportQuestions(): Observable<HttpResponse<string>> {
                        const url = `${this.basePath}/api/questions/export`;
                        return this.http.get(url, { responseType: 'text', observe: 'response' });
                """);
        // A 201 with a Location header and no body.
        assertContains(api, """
                    createQuestion(question: Question): Observable<HttpResponse<void>> {
                        const url = `${this.basePath}/api/questions`;
                        return this.http.post<void>(url, question, { observe: 'response' });
                """);
        assertContains(api, "return this.http.delete<number>(url, { body: requestBody, observe: 'response' });");
        // A file download observes the response anyway; observe appears once.
        assertContains(api, "return this.http.get(url, { responseType: 'blob', observe: 'response' });");
        // A header on an error response only: HttpErrorResponse already exposes it, so the body stays the result.
        assertContains(api, """
                    getQuestion(questionId: number): Observable<Question> {
                        const url = `${this.basePath}/api/questions/${questionId}`;
                        return this.http.get<Question>(url);
                """);
    }

    @Test
    void returnsTheBodyOfOperationsWithResponseHeadersByDefault() throws IOException {
        generateFixture("fixtures/response-headers-openapi.yaml", Map.of());

        String api = Files.readString(tempDir.resolve("api/question-api.ts"));
        assertContains(api, """
                    getQuestions(page?: number): Observable<Array<Question>> {
                """);
        assertContains(api, "return this.http.get<Array<Question>>(url);");
        assertContains(api, "return this.http.post<void>(url, question);");
    }

    @Test
    void sendsDeclaredHeaderParameters() throws IOException {
        generateFixture("fixtures/header-params-openapi.yaml", Map.of());

        String api = Files.readString(tempDir.resolve("api/repository-api.ts"));
        assertContains(api, """
                        const url = `${this.basePath}/api/exercises/${exerciseId}/repository${queryString ? `?${queryString}` : ''}`;
                        const headers: Record<string, string> = Object.create(null);
                        if (authorization !== undefined && authorization !== null) {
                            headers['Authorization'] = String(authorization);
                        }
                        if (xTraceId !== undefined && xTraceId !== null) {
                            headers['X-Trace-Id'] = String(xTraceId);
                        }
                        return this.http.get<RepositoryFiles>(url, { headers });
                """);
        assertContains(api, "return this.http.post<void>(url, repositoryFeedback, { headers });");
        assertContains(api, "return this.http.get(url, { headers, responseType: 'text' });");
        assertContains(api, "return this.http.get<RepositoryFiles>(url);");

        String resources = Files.readString(tempDir.resolve("api/repository-resources.ts"));
        // Header values are function arguments like path parameters; an optional one stays optional only while
        // every later argument is optional too.
        assertContains(resources, "export function getRepositoryResource(exerciseId: Signal<number | undefined> | number, authorization: Signal<string | undefined> | string, xTraceId?: Signal<string | undefined> | string, params?: Signal<GetRepositoryParams>): HttpResourceRef<RepositoryFiles | undefined>");
        assertContains(resources, """
                        const headers: Record<string, string> = Object.create(null);
                        const authorizationValue = typeof authorization === 'function' ? authorization() : authorization;
                        if (authorizationValue === undefined) {
                            return undefined;
                        }
                        if (authorizationValue !== undefined && authorizationValue !== null) {
                            headers['Authorization'] = String(authorizationValue);
                        }
                        const xTraceIdValue = typeof xTraceId === 'function' ? xTraceId() : xTraceId;
                        if (xTraceIdValue !== undefined && xTraceIdValue !== null) {
                            headers['X-Trace-Id'] = String(xTraceIdValue);
                        }
                """);
        // With headers the resource returns the request object form, without them the plain URL string.
        assertContains(resources, "return { url: `${BASE_PATH}/api/exercises/${exerciseIdValue}/repository${query ? `?${query}` : ''}`, headers };");
        assertContains(resources, "return { url: `${BASE_PATH}/api/exercises/${exerciseIdValue}/repository/token`, headers };");
        assertContains(resources, "return `${BASE_PATH}/api/exercises/${exerciseIdValue}`;");
    }

    @Test
    void renamesParametersThatCollideWithTemplateLocals() throws IOException {
        generateFixture("fixtures/local-name-collision-openapi.yaml", Map.of());

        String api = Files.readString(tempDir.resolve("api/link-api.ts"));
        // The TypeScript name changes, the wire name stays.
        assertContains(api, """
                    getLinkPreview(urlParam: string): Observable<LinkPreview> {
                        const queryParams = new URLSearchParams();
                        appendQueryParam(queryParams, 'url', urlParam);
                        const queryString = queryParams.toString();
                        const url = `${this.basePath}/api/link-preview${queryString ? `?${queryString}` : ''}`;
                """);
        assertContains(api, "search(queryParam: string, paramsParam: number, headersParam?: string, queryStringParam?: string, queryParamsParam?: string, searchParamsParam?: string, appendQueryParamParam?: string): Observable<Array<LinkPreview>>");
        assertContains(api, "const queryParamPath = encodeURIComponent(String(queryParam));");
        assertContains(api, "appendQueryParam(queryParams, 'queryString', queryStringParam);");
        assertContains(api, "appendQueryParam(queryParams, 'queryParams', queryParamsParam);");
        assertContains(api, "const url = `${this.basePath}/api/search/${queryParamPath}/${paramsParam}${queryString ? `?${queryString}` : ''}`;");
        assertContains(api, "headers['headers'] = String(headersParam);");
        assertContains(api, "upload(formDataParam?: string): Observable<void>");
        assertContains(api, "formData.append('formData', String(formDataParam));");

        String resources = Files.readString(tempDir.resolve("api/link-resources.ts"));
        assertContains(resources, "appendQueryParam(searchParams, 'url', queryParams.url);");
        assertContains(resources, "export function searchResource(queryParam: Signal<string | undefined> | string, paramsParam: Signal<number | undefined> | number, headersParam?: Signal<string | undefined> | string, searchParamsParam?: Signal<string | undefined> | string, params?: Signal<SearchParams>)");
        assertContains(resources, "const queryParamValue = typeof queryParam === 'function' ? queryParam() : queryParam;");
        assertContains(resources, "const headersParamValue = typeof headersParam === 'function' ? headersParam() : headersParam;");
        assertContains(resources, "return { url: `${BASE_PATH}/api/search/${queryParamPath}/${paramsParamValue}${query ? `?${query}` : ''}`, headers };");
    }

    @Test
    void escapesReservedWordsInEveryParameterIdentifier() throws IOException {
        generateFixture("fixtures/reserved-parameter-openapi.yaml", Map.of());

        String api = Files.readString(tempDir.resolve("api/package-api.ts"));
        assertContains(api, "getPackage(_default: number, _package: string, _function?: string, _new?: boolean): Observable<string>");
        assertContains(api, "const _packagePath = encodeURIComponent(String(_package));");
        assertContains(api, "const url = `${this.basePath}/api/packages/${_default}/${_packagePath}${queryString ? `?${queryString}` : ''}`;");

        String resources = Files.readString(tempDir.resolve("api/package-resources.ts"));
        assertContains(resources, "export function getPackageResource(_default: Signal<number | undefined> | number, _package: Signal<string | undefined> | string, _function?: Signal<string | undefined> | string, params?: Signal<GetPackageParams>)");
        assertContains(resources, "return { url: `${BASE_PATH}/api/packages/${_defaultValue}/${_packagePath}${query ? `?${query}` : ''}`, headers };");
        assertContains(resources, "new?: boolean;");
    }

    @Test
    void givesEachQueryParameterItsOwnKey() throws IOException {
        generateFixture("fixtures/query-key-openapi.yaml", Map.of());

        String resources = Files.readString(tempDir.resolve("api/page-resources.ts"));
        assertContains(resources, """
                export interface GetPagesParams {
                    pageSize?: number;
                    pageSize2?: number;
                    _2fa?: boolean;
                    new?: boolean;
                }
                """);
        assertContains(resources, """
                        appendQueryParam(searchParams, 'page_size', queryParams.pageSize);
                        appendQueryParam(searchParams, 'pageSize', queryParams.pageSize2);
                        appendQueryParam(searchParams, '2fa', queryParams._2fa);
                        appendQueryParam(searchParams, 'new', queryParams.new);
                """);
    }

    @Test
    void namesModelPropertiesExactlyLikeTheirJsonKeys() throws IOException {
        generateFixture("fixtures/reserved-property-openapi.yaml", Map.of());

        // Interfaces describe the JSON as it is, so a property name must be its JSON key. Reserved words are valid
        // property names; keys that are not identifiers are quoted.
        assertContains(Files.readString(tempDir.resolve("model/result-summary.ts")), """
                export interface ResultSummary {
                    readonly final?: boolean;
                    readonly delete: boolean;
                    readonly class?: string;
                    readonly 'x-y'?: string;
                    readonly first_name?: string;
                    readonly score?: number;
                }
                """);
        // Parameters are identifiers, so reserved words stay escaped there.
        assertContains(Files.readString(tempDir.resolve("api/result-api.ts")), "getResults(_final?: boolean): Observable<Array<ResultSummary>>");
    }

    @Test
    void quotesPropertyKeysThatAreNotIdentifiers() throws IOException, InterruptedException {
        generateFixture("fixtures/reserved-property-openapi.yaml", Map.of());

        NodeProcess.assertSucceeds(Path.of("src/test/typescript"), "A property key does not parse back to its JSON key",
                "model-keys.test.mjs", tempDir.resolve("model/escapes.ts").toString(), "Escapes",
                "it's", "back\\slash", "line\nbreak", "tab\tand \"quote\"");
    }

    @Test
    void writesInlineModelsToTheFileTheirImportsName() throws IOException {
        generateFixture("fixtures/inline-model-openapi.yaml", Map.of());

        // The inline response schema is named getExam_200_response; its class is GetExam200Response.
        assertContains(Files.readString(tempDir.resolve("api/exam-api.ts")), "import { GetExam200Response } from '../model/get-exam200response';");
        assertContains(Files.readString(tempDir.resolve("api/exam-resources.ts")), "import { GetExam200Response } from '../model/get-exam200response';");
        assertContains(Files.readString(tempDir.resolve("model/get-exam200response.ts")), "export interface GetExam200Response {");
        assertFalse(Files.exists(tempDir.resolve("model/get-exam-200-response.ts")));
    }

    @Test
    void requiresPropertiesThatTheParentRequires() throws IOException {
        generateFixture("fixtures/subtype-openapi.yaml", Map.of());

        assertContains(Files.readString(tempDir.resolve("model/exercise.ts")), """
                export interface Exercise {
                    readonly type: string;
                }
                """);
        assertContains(Files.readString(tempDir.resolve("model/text-exercise.ts")), """
                export interface TextExercise extends Exercise {
                    readonly type: string;
                    readonly name?: string;
                }
                """);
    }

    @Test
    void keepsPropertiesThatASubtypeNarrows() throws IOException {
        generateFixture("fixtures/subtype-narrowing-openapi.yaml", Map.of());

        assertContains(Files.readString(tempDir.resolve("model/quiz-participation.ts")), """
                export interface QuizParticipation extends Participation {
                    readonly exercise: QuizExercise;
                    readonly submitted?: boolean;
                }
                """);
    }

    @Test
    void rendersOneOfSchemasAsUnionsOfTheirBranches() throws IOException {
        generateFixture("fixtures/one-of-openapi.yaml", Map.of());

        assertContains(Files.readString(tempDir.resolve("model/question-create.ts")), """
                import type { MultipleChoiceQuestionCreate } from './multiple-choice-question-create';
                import type { ShortAnswerQuestionCreate } from './short-answer-question-create';

                export type QuestionCreate = MultipleChoiceQuestionCreate | ShortAnswerQuestionCreate;
                """);
        // A primitive branch keeps its type and needs no import; an array branch imports its item model.
        assertContains(Files.readString(tempDir.resolve("model/answer-value.ts")), """
                import type { AnswerOption } from './answer-option';

                export type AnswerValue = string | number | Array<AnswerOption>;
                """);
        // A model inside a nested array or map branch is imported too.
        assertContains(Files.readString(tempDir.resolve("model/grid.ts")), """
                import type { Cell } from './cell';

                export type Grid = Array<Array<Cell>> | { [key: string]: Array<Cell>; };
                """);
        // An inline enum branch contributes its values; its base type would absorb the other members.
        assertContains(Files.readString(tempDir.resolve("model/difficulty.ts")), """
                import type { Level } from './level';

                export type Difficulty = Level | 'easy' | 'hard' | 1 | 2;
                """);
        // A string enum value with a format is still a string, so it keeps its quotes.
        assertContains(Files.readString(tempDir.resolve("model/deadline.ts")),
                "export type Deadline = '2020-01-01' | '2020-01-01T00:00:00Z' | number;");
        // A recursive union refers to itself without importing itself (TS2440).
        String jsonValue = Files.readString(tempDir.resolve("model/json-value.ts"));
        assertContains(jsonValue, "export type JsonValue = string | number | Array<JsonValue> | { [key: string]: JsonValue; };");
        assertFalse(jsonValue.contains("import "), jsonValue);
        // The parent's normalizer turns a {type: 'null'} branch into nullable: true.
        assertContains(Files.readString(tempDir.resolve("model/hint.ts")), "export type Hint = AnswerOption | string | null;");
        // The discriminator of each branch is a single-value enum, which a switch on it narrows the union by.
        assertContains(Files.readString(tempDir.resolve("model/multiple-choice-question-create.ts")),
                "export type MultipleChoiceQuestionCreateTypeEnum = 'multiple-choice';");
    }

    @Test
    void declaresTheInheritedPropertiesOfASubtypeOfAUnion() throws IOException {
        generateFixture("fixtures/one-of-openapi.yaml", Map.of());

        assertContains(Files.readString(tempDir.resolve("model/question.ts")), "export type Question = MultipleChoiceQuestion | ShortAnswerQuestion;");
        // An interface cannot extend a union, so the subtype states the parent's properties, and none of its sibling's.
        // It imports the models of nested array properties, its own and the inherited ones.
        assertContains(Files.readString(tempDir.resolve("model/multiple-choice-question.ts")), """
                import type { Mark } from './mark';
                import type { AnswerOption } from './answer-option';

                export interface MultipleChoiceQuestion {
                    readonly title?: string;
                    readonly marks?: Array<Array<Mark>>;
                    readonly type: MultipleChoiceQuestionTypeEnum;
                    readonly answerOptions?: Array<AnswerOption>;
                }
                """);
        assertContains(Files.readString(tempDir.resolve("model/short-answer-question.ts")), """
                import type { Mark } from './mark';
                import type { Cell } from './cell';
                """);
    }

    @Test
    void intersectsTheObjectBranchesWithTheRootPropertiesTheyLose() throws IOException {
        generateFixture("fixtures/one-of-openapi.yaml", Map.of());

        // RefinementSuccess does not require type and neither branch declares status; the integer branch has no properties.
        assertContains(Files.readString(tempDir.resolve("model/refinement-response.ts")), """
                export type RefinementResponse = (RefinementSuccess | RefinementFailure) & {
                    readonly type: string;
                    readonly status?: RefinementResponseStatusEnum;
                } | number;

                export type RefinementResponseStatusEnum = 'open' | 'closed';
                """);
        assertContains(Files.readString(tempDir.resolve("model/annotated.ts")), """
                export type Annotated = { [key: string]: string; } & {
                    readonly note?: string;
                } | string;
                """);
    }

    @Test
    void passesTheDeclaredStyleOfAQueryParameterToTheHelper() throws IOException {
        generateFixture("fixtures/query-style-openapi.yaml", Map.of());

        String styled = """
                appendQueryParam(%1$s, 'filter', %2$sfilter, 'deepObject', true);
                appendQueryParam(%1$s, 'ids', %2$sids, 'form', false);
                appendQueryParam(%1$s, 'point', %2$spoint, 'form', false);
                appendQueryParam(%1$s, 'pipes', %2$spipes, 'pipeDelimited', false);
                appendQueryParam(%1$s, 'spaces', %2$sspaces, 'spaceDelimited', false);
                appendQueryParam(%1$s, 'jsonPoint', %2$sjsonPoint, 'json');
                appendQueryParam(%1$s, 'vendorPoint', %2$svendorPoint, 'json');
                appendQueryParam(%1$s, 'textName', %2$stextName);
                appendQueryParam(%1$s, 'page', %2$spage);
                """;
        assertContains(Files.readString(tempDir.resolve("api/score-api.ts")), styled.formatted("queryParams", "").indent(8));
        assertContains(Files.readString(tempDir.resolve("api/score-resources.ts")), styled.formatted("searchParams", "queryParams.").indent(8));
    }

    @Test
    void rejectsAQueryStyleThatOpenApiDoesNotAllow() {
        RuntimeException error = assertThrows(RuntimeException.class, () -> generateFixture("invalid/query-simple-style-openapi.yaml", Map.of()));
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        assertEquals("Query parameter ids of operation listItems has style simple, which OpenAPI does not allow in a query", cause.getMessage());
    }

    @Test
    void typesUniqueItemsArraysAsArrays() throws IOException {
        generateFixture("fixtures/object-query-openapi.yaml", Map.of());

        // JSON has no sets: JSON.parse returns an array, and JSON.stringify, which Angular uses for request bodies,
        // turns a Set into {} wherever it sits.
        assertContains(Files.readString(tempDir.resolve("api/score-api.ts")), "teamIds?: Array<number>");
        assertContains(Files.readString(tempDir.resolve("model/participation-score-search.ts")), "readonly exerciseIds?: Array<number>;");
    }

    @Test
    void passesEveryQueryParameterToTheQueryHelper() throws IOException {
        generateFixture("fixtures/object-query-openapi.yaml", Map.of());

        String api = Files.readString(tempDir.resolve("api/score-api.ts"));
        assertContains(api, """
                        const queryParams = new URLSearchParams();
                        appendQueryParam(queryParams, 'search', search);
                        appendQueryParam(queryParams, 'includeTeams', includeTeams);
                        appendQueryParam(queryParams, 'teamIds', teamIds);
                        const queryString = queryParams.toString();
                """);
        assertContains(api, "import { appendQueryParam } from './query-params';");

        String resources = Files.readString(tempDir.resolve("api/score-resources.ts"));
        assertContains(resources, """
                        const searchParams = new URLSearchParams();
                        appendQueryParam(searchParams, 'search', queryParams.search);
                        appendQueryParam(searchParams, 'includeTeams', queryParams.includeTeams);
                        appendQueryParam(searchParams, 'teamIds', queryParams.teamIds);
                        const query = searchParams.toString();
                """);
        assertContains(resources, "import { appendQueryParam } from './query-params';");
    }

    private void generateFixture(String fixture, Map<String, Object> additionalProperties) {
        CodegenConfigurator configurator = new CodegenConfigurator()
                .setGeneratorName(Angular22Generator.GENERATOR_NAME)
                .setInputSpec(Path.of("src/test/resources").resolve(fixture).toAbsolutePath().toString())
                .setOutputDir(tempDir.toString());
        additionalProperties.forEach(configurator::addAdditionalProperty);
        new DefaultGenerator().opts(configurator.toClientOptInput()).generate();
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected), () -> "Expected generated output to contain:\n" + expected + "\n\nActual output:\n" + actual);
    }
}
