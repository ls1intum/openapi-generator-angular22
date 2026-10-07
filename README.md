# OpenAPI Generator for Angular 22

[![Build](https://github.com/ls1intum/openapi-generator-angular22/actions/workflows/build.yml/badge.svg)](https://github.com/ls1intum/openapi-generator-angular22/actions)
[![Maven Central](https://img.shields.io/maven-central/v/de.tum.cit.aet/openapi-generator-angular22)](https://search.maven.org/artifact/de.tum.cit.aet/openapi-generator-angular22)

A custom [OpenAPI Generator](https://openapi-generator.tech/) for generating modern **Angular 22** TypeScript client code with best practices.

## Features

- **Signal-based `httpResource`** for GET requests (reactive, auto-refetching)
- **Injectable services** with `inject()` function for mutations (POST, PUT, DELETE)
- **Standalone services** (`providedIn: 'root'`, no NgModules required)
- **Strict TypeScript** with `readonly` modifiers on response models
- **Clean file organization** (separate files for services vs resources)
- **Compatible** with openapi-generator ecosystem (CLI, Maven, Gradle)

## Generated Code Example

### Models
```typescript
export interface Course {
    readonly id: number;
    readonly title: string;
    readonly shortName: string;
    readonly description?: string;
    readonly startDate?: string;
    readonly endDate?: string;
    readonly semester?: string;
    readonly testCourse?: boolean;
    readonly onlineCourse?: boolean;
    readonly maxComplaints?: number;
    readonly maxTeamComplaints?: number;
    readonly maxComplaintTimeDays?: number;
    readonly studentGroupName?: string;
    readonly teachingAssistantGroupName?: string;
    readonly editorGroupName?: string;
    readonly instructorGroupName?: string;
    readonly color?: string;
    readonly courseIcon?: string;
}

export interface CourseCreate {
    title: string;
    shortName: string;
    description?: string;
    startDate?: string;
    endDate?: string;
    semester?: string;
    testCourse?: boolean;
    onlineCourse?: boolean;
    color?: string;
}

export interface CourseUpdate {
    title?: string;
    description?: string;
    startDate?: string;
    endDate?: string;
    semester?: string;
    color?: string;
}
```

### API Service (Mutations)
```typescript
@Injectable({ providedIn: 'root' })
export class CourseApi {
    private readonly http = inject(HttpClient);
    private readonly basePath = '/api';

    createCourse(courseCreate: CourseCreate): Observable<Course> {
        const url = `${this.basePath}/courses`;
        return this.http.post<Course>(url, courseCreate);
    }

    deleteCourse(courseId: number): Observable<void> {
        const url = `${this.basePath}/courses/${courseId}`;
        return this.http.delete<void>(url);
    }

    updateCourse(courseId: number, courseUpdate: CourseUpdate): Observable<Course> {
        const url = `${this.basePath}/courses/${courseId}`;
        return this.http.put<Course>(url, courseUpdate);
    }
}
```

### Resources (GET with httpResource)
```typescript
import { appendQueryParam } from './query-params';

const BASE_PATH = '/api';

export interface GetAllCoursesParams {
    onlyActive?: boolean;
    page?: number;
    size?: number;
}

export function getAllCoursesResource(params?: Signal<GetAllCoursesParams>): HttpResourceRef<Array<Course> | undefined> {
    return httpResource<Array<Course>>(() => {
        const queryParams = params?.() ?? {};
        const searchParams = new URLSearchParams();
        appendQueryParam(searchParams, 'onlyActive', queryParams.onlyActive);
        appendQueryParam(searchParams, 'page', queryParams.page);
        appendQueryParam(searchParams, 'size', queryParams.size);
        const query = searchParams.toString();
        return `${BASE_PATH}/courses${query ? `?${query}` : ''}`;
    });
}

export function getCourseResource(courseId: Signal<number | undefined> | number): HttpResourceRef<Course | undefined> {
    return httpResource<Course>(() => {
        const courseIdValue = typeof courseId === 'function' ? courseId() : courseId;
        if (courseIdValue === undefined) {
            return undefined;
        }
        return `${BASE_PATH}/courses/${courseIdValue}`;
    });
}
```

### Query Parameters

`api/query-params.ts` holds `appendQueryParam`, which every API and resources file with query parameters imports. It
sends each parameter in its declared `style` and `explode`:

- `form` with explode, the default, repeats the key of an array or set and sends an object as one key per property.
  A nested object gets dotted keys (`range.min=1`), which is how Spring binds a query DTO, since OpenAPI defines no
  format for it.
- Without explode, `form`, `spaceDelimited` and `pipeDelimited` join an array, or an object's keys and values, with a
  comma, a space or a pipe.
- `deepObject` sends `filter[name]=x`. A nested object goes out as `filter[range][min]=1` and an array property as
  repeated `filter[ids]` keys, conventions OpenAPI leaves open.
- A parameter declared with JSON content, `application/json` or a `+json` type, goes out as JSON. Content of
  another media type, such as `text/plain`, goes out in the default style.

Outside JSON content, the helper leaves out `null` and `undefined` values and items, and empty arrays, sets and
objects. Dates go out as ISO 8601. OpenAPI defines no query format for an object or array inside an array, or for a
parameter that is an array under `deepObject`, so the helper throws, naming the property, when it gets one. A style
that OpenAPI does not allow in a query, such as `simple`, fails generation.

### Multipart Bodies

Each multipart field follows its `encoding`. A field, or each item of an array or set except null ones, becomes one
part. A binary goes out as it is. A string, number, boolean or enum value goes out as text, which is OpenAPI's
default, and a `Date` as ISO 8601, or as a full date for `format: date`. An untyped value, such as an item of
`items: {}`, goes out by what it holds at runtime, so files stay files. Objects and maps go out as one JSON part, and
so does any field whose `encoding` names `application/json` or a `+json` media type. Spring reads a `@RequestPart List<String>` from one JSON part, so declare
that encoding for it:

```yaml
encoding:
  tags:
    contentType: application/json
```

## Installation

### Gradle (Artemis, other AET projects)

Add to your `build.gradle.kts`:

```kotlin
plugins {
    id("org.openapi.generator") version "7.18.0"
}

dependencies {
    // Add as a dependency to the openapi generator
    openapiGenerator("de.tum.cit.aet:openapi-generator-angular22:1.0.0")
}

openApiGenerate {
    generatorName.set("angular22")
    inputSpec.set("$projectDir/src/main/resources/openapi.yaml")
    outputDir.set("$buildDir/generated/openapi")
    
    configOptions.set(mapOf(
        "useHttpResource" to "true",
        "useInjectFunction" to "true",
        "separateResources" to "true",
        "readonlyModels" to "true"
    ))
}
```

Add to your `build.gradle` (Groovy DSL):

```groovy
plugins {
    id 'org.openapi.generator' version '7.18.0'
}

dependencies {
    // Add as a dependency to the openapi generator
    openapiGenerator 'de.tum.cit.aet:openapi-generator-angular22:1.0.0'
}

openApiGenerate {
    generatorName = 'angular22'
    inputSpec = "$projectDir/src/main/resources/openapi.yaml"
    outputDir = "$buildDir/generated/openapi"
    configOptions = [
        useHttpResource : 'true',
        useInjectFunction: 'true',
        separateResources: 'true',
        readonlyModels   : 'true'
    ]
}
```

### Maven

```xml
<plugin>
    <groupId>org.openapitools</groupId>
    <artifactId>openapi-generator-maven-plugin</artifactId>
    <version>7.18.0</version>
    <executions>
        <execution>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <generatorName>angular22</generatorName>
                <inputSpec>${project.basedir}/src/main/resources/openapi.yaml</inputSpec>
                <output>${project.build.directory}/generated-sources/openapi</output>
                <configOptions>
                    <useHttpResource>true</useHttpResource>
                    <useInjectFunction>true</useInjectFunction>
                    <separateResources>true</separateResources>
                    <readonlyModels>true</readonlyModels>
                </configOptions>
            </configuration>
        </execution>
    </executions>
    <dependencies>
        <dependency>
            <groupId>de.tum.cit.aet</groupId>
            <artifactId>openapi-generator-angular22</artifactId>
            <version>1.0.0</version>
        </dependency>
    </dependencies>
</plugin>
```

### CLI

```bash
# Download the generator JAR
wget https://github.com/ls1intum/openapi-generator-angular22/releases/download/v1.0.0/openapi-generator-angular22-1.0.0.jar

# Generate code
java -cp openapi-generator-angular22-1.0.0.jar:openapi-generator-cli-7.18.0.jar \
    org.openapitools.codegen.OpenAPIGenerator generate \
    -g angular22 \
    -i openapi.yaml \
    -o ./generated
```

## Configuration Options

| Option              | Default | Description                                                 |
|---------------------|---------|-------------------------------------------------------------|
| `useHttpResource`   | `true`  | Use `httpResource` for GET requests instead of `HttpClient` |
| `useInjectFunction` | `true`  | Use `inject()` function instead of constructor injection    |
| `separateResources` | `true`  | Generate separate `*-resources.ts` files for GET operations |
| `readonlyModels`    | `true`  | Add `readonly` modifier to response model properties        |

An array with `uniqueItems: true` is typed `Array<T>`, not `Set<T>`. JSON has no sets: a response arrives as an
array, and `JSON.stringify`, which Angular uses for request bodies, sends a `Set` as `{}`. A `typeMappings` entry
`set: 'Set'` restores the old type.

## Usage in Components

```typescript
import { Component, computed, signal, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { 
    CourseApi,
    getCourseResource, 
    getAllCoursesResource,
    Course, 
    CourseCreate,
    CourseUpdate 
} from './generated';

@Component({
    selector: 'app-course-list',
    standalone: true,
    template: `
        @if (courses.isLoading()) {
            <div class="spinner">Loading...</div>
        }

        @if (courses.hasValue()) {
            @for (course of courses.value() ?? []; track course.id) {
                <div (click)="selectCourse(course.id)">
                    {{ course.title }}
                </div>
            }
        }

        @if (selectedCourse.hasValue()) {
            <div class="details">
                Selected: {{ selectedCourse.value()?.shortName }}
            </div>
        }
    `
})
export class CourseListComponent {
    private readonly courseApi = inject(CourseApi);

    // Reactive state
    protected readonly page = signal(0);
    protected readonly selectedCourseId = signal<number | undefined>(undefined);

    // Resources - automatically refetch when signals change
    protected readonly courses = getAllCoursesResource(
        computed(() => ({ page: this.page(), size: 20, onlyActive: true }))
    );

    protected readonly selectedCourse = getCourseResource(
        computed(() => this.selectedCourseId() ?? -1)
    );

    selectCourse(id: number): void {
        this.selectedCourseId.set(id);
    }

    async createCourse(data: CourseCreate): Promise<void> {
        const created = await firstValueFrom(this.courseApi.createCourse(data));
        console.log('Created:', created);
        this.courses.reload(); // Refresh the list
    }

    async updateCourse(courseId: number, data: CourseUpdate): Promise<void> {
        const updated = await firstValueFrom(this.courseApi.updateCourse(courseId, data));
        console.log('Updated:', updated);
        this.courses.reload(); // Refresh the list
    }

    async deleteCourse(courseId: number): Promise<void> {
        await firstValueFrom(this.courseApi.deleteCourse(courseId));
        this.courses.reload(); // Refresh the list
    }
}
```

## Building from Source

```bash
git clone https://github.com/ls1intum/openapi-generator-angular22.git
cd openapi-generator-angular22
./gradlew build
```

The tests type-check the generated code with the TypeScript compiler pinned in `src/test/typescript`, so the build
needs Node.js and npm.

## Trying the Example Generator

```bash
./gradlew generateExample
```

This generates Angular client code into `build/generated/example` using `example/example-openapi.yaml`.

## Publishing

The generator is published to **Maven Central**, so consumers resolve it from a plain `mavenCentral()`
repository with no authentication. Releases are automated: pushing a `v*` tag runs the publish job in
[`.github/workflows/build.yml`](.github/workflows/build.yml), which uploads the signed artifacts to the
Central Portal. See **[RELEASING.md](RELEASING.md)** for the one-time account/secret setup and the full
release procedure.

```bash
# Build the artifacts into your local Maven repository (no signing key required) — this is how
# downstream projects (e.g. Artemis) build the generator from source to regenerate their client.
./gradlew publishToMavenLocal
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## License

MIT License - see [LICENSE](LICENSE) file.

## Related Projects

- [Artemis](https://github.com/ls1intum/Artemis) - Interactive Learning with Automated Feedback
- [OpenAPI Generator](https://openapi-generator.tech/) - The base generator framework
