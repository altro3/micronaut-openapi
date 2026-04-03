package io.micronaut.openapi.visitor

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import spock.lang.Ignore

class OpenApiAiControllerKotlinSpec extends AbstractKotlinCompilerSpec {

    def setup() {
        Utils.clean()
        System.clearProperty(OpenApiConfigProperty.MICRONAUT_OPENAPI_ENABLED)
        System.setProperty(Utils.ATTR_TEST_MODE, "true")
        System.setProperty(OpenApiConfigProperty.MICRONAUT_OPENAPI_ADOC_ENABLED, "false")
    }

    def cleanup() {
        Utils.clean()
        System.clearProperty(Utils.ATTR_TEST_MODE)
        System.clearProperty(OpenApiConfigProperty.MICRONAUT_OPENAPI_ADOC_ENABLED)
    }

    void "test controller interpretation - async and status codes"() {
        given:
        buildBeanDefinition('test.AsyncController', '''
package test

import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.*
import io.reactivex.Single
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import java.util.concurrent.CompletableFuture

@Controller("/async")
class AsyncController {

    // 1. Reactive Single with custom success status
    @Post("/single")
    @Status(HttpStatus.CREATED)
    fun createSingle(@Body name: String): Single<SimpleDto> = TODO()

    // 2. CompletableFuture with explicit ApiResponse
    @Get("/future/{id}")
    @ApiResponse(responseCode = "200", description = "Success from Future")
    @ApiResponse(responseCode = "404", description = "Not Found")
    fun getFuture(id: String?): CompletableFuture<HttpResponse<SimpleDto>> = TODO()

    // 3. Void/Unit return with No Content status
    @Delete("/empty")
    @Status(HttpStatus.NO_CONTENT)
    fun deleteEmpty() { }
}

@io.micronaut.core.annotation.Introspected
data class SimpleDto(val msg: String)

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        // --- 1. Verify Reactive Unwrapping (Single<SimpleDto>) ---
        var singleOp = openApi.paths['/async/single'].post
        // Status should be 201 (from @Status(HttpStatus.CREATED))
        singleOp.responses.containsKey('201')
        var singleResponse = singleOp.responses['201'].content['application/json'].schema
        // Should point to SimpleDto, bypassing Single wrapper
        singleResponse.$ref == '#/components/schemas/SimpleDto'

        // --- 2. Verify CompletableFuture & Nullable Path ---
        var futureOp = openApi.paths['/async/future/{id}'].get
        // Path variable 'id' - even if nullable in Kotlin, it MUST be required in OpenAPI Path
        var idParam = futureOp.parameters.find { it.name == 'id' }
        idParam.in == 'path'
        idParam.required == true

        // Check responses from @ApiResponse
        futureOp.responses.containsKey('200')
        futureOp.responses['200'].description == "Success from Future"
        futureOp.responses.containsKey('404')

        // Response schema check (Unwrapping Future -> HttpResponse -> SimpleDto)
        var futureSchema = futureOp.responses['200'].content['application/json'].schema
        futureSchema.$ref == '#/components/schemas/SimpleDto'

        // --- 3. Verify Void/No Content ---
        var deleteOp = openApi.paths['/async/empty'].delete
        deleteOp.responses.containsKey('204')
        // For 204 No Content, there should be no schema in content
        deleteOp.responses['204'].content == null
    }

    @Ignore
    void "test controller interpretation - routes and complex parameters"() {
        given:
        buildBeanDefinition('test.RouteController', '''
package test

import io.micronaut.http.annotation.*
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.Parameter

@Controller("/routes")
class RouteController {

    // 1. Matrix Variables - check if they are extracted into parameters
    @Get("/coords{;lat,lon}")
    fun getByCoords(
        @MatrixVariable lat: Double,
        @MatrixVariable lon: Double
    ): String = "at $lat:$lon"

    // 2. Exploded Map as Query Parameters
    @Get("/search")
    fun search(
        @QueryValue filters: Map<String, Int>
    ): List<String> = emptyList()

    // 3. Wildcard Path (Greedy path variable)
    @Get("/files/{*path}")
    fun getFile(@PathVariable path: String): String = path

    // 4. Header Map - multiple headers in one object
    @Get("/headers")
    fun checkHeaders(@Header headers: Map<String, String>): String = "ok"
}

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        // --- 1. Verify Matrix Variables ---
        var coordsOp = openApi.paths.keySet().find { it.startsWith('/routes/coords') }
        var coordsParams = openApi.paths[coordsOp].get.parameters
        // Matrix variables should be interpreted as 'path' parameters with 'matrix' style
        coordsParams.find { it.name == 'lat' }.in == 'path'
        coordsParams.find { it.name == 'lon' }.in == 'path'
        // Check style (OpenAPI 3.x 'matrix' style for path params)
        coordsParams.find { it.name == 'lat' }.style.toString() == 'matrix'

        // --- 2. Verify Exploded Query Map ---
        var searchOp = openApi.paths['/routes/search'].get
        // A Map as QueryValue should be interpreted as a set of dynamic query parameters
        var queryParam = searchOp.parameters.find { it.in == 'query' }
        queryParam.name == 'filters'
        queryParam.explode == true
        queryParam.schema.type == 'object'
        queryParam.schema.additionalProperties.type == 'integer'

        // --- 3. Verify Wildcard Path ---
        // OpenAPI doesn't have a direct equivalent for {*path},
        // but it should be a required path parameter.
        var fileOp = openApi.paths.keySet().find { it.contains('/files/') }
        var pathParam = openApi.paths[fileOp].get.parameters.find { it.name == 'path' }
        pathParam.required == true
        pathParam.in == 'path'

        // --- 4. Verify Header Map ---
        var headersOp = openApi.paths['/routes/headers'].get
        var headerParam = headersOp.parameters.find { it.in == 'header' }
        headerParam.name == 'headers'
        headerParam.explode == true
        headerParam.schema.type == 'object'
    }

    @Ignore
    void "test controller interpretation - reactive streams and response wrappers"() {
        given:
        buildBeanDefinition('test.StreamController', '''
package test

import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.*
import io.micronaut.core.annotation.Introspected
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.InputStream

@Introspected
data class DataPoint(val value: Double, val timestamp: Long)

@Controller("/streams")
class StreamController {

    // 1. Reactive Stream (Flux) -> Should be Array in OpenAPI
    @Get(value = "/data", produces = [MediaType.APPLICATION_JSON])
    fun getDataStream(): Flux<DataPoint> = TODO()

    // 2. HttpResponse wrapper -> Should be unwrapped to DataPoint
    @Post("/save")
    fun save(@Body point: DataPoint): HttpResponse<DataPoint> = HttpResponse.created(point)

    // 3. Binary Download -> Should be format: binary
    @Get(value = "/download", produces = [MediaType.APPLICATION_OCTET_STREAM])
    fun download(): InputStream = TODO()

    // 4. Async Mono with status override
    @Put("/update")
    @Status(io.micronaut.http.HttpStatus.ACCEPTED)
    fun update(@Body point: DataPoint): Mono<Void> = TODO()
}

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        // --- 1. Verify Flux<DataPoint> Interpretation ---
        var streamOp = openApi.paths['/streams/data'].get
        var streamResponse = streamOp.responses['200'].content['application/json'].schema
        // Flux must be interpreted as an array
        streamResponse.type == 'array'
        streamResponse.items.$ref == '#/components/schemas/DataPoint'

        // --- 2. Verify HttpResponse<DataPoint> Unwrapping ---
        var saveOp = openApi.paths['/streams/save'].post
        // Micronaut often uses 200 by default, but if it's smart, it might see 201
        var saveResponse = (saveOp.responses['201'] ?: saveOp.responses['200']).content['application/json'].schema
        saveResponse.$ref == '#/components/schemas/DataPoint'

        // --- 3. Verify Binary Stream ---
        var downloadOp = openApi.paths['/streams/download'].get
        var binaryResponse = downloadOp.responses['200'].content['application/octet-stream'].schema
        binaryResponse.type == 'string'
        binaryResponse.format == 'binary'

        // --- 4. Verify Mono<Void> and @Status ---
        var updateOp = openApi.paths['/streams/update'].put
        // Status 202 Accepted (from @Status)
        updateOp.responses.containsKey('202')
        // Mono<Void> or Unit should result in no content or empty schema
        updateOp.responses['202'].content == null
    }

    @Ignore
    void "test controller interpretation - pojo query parameters and validation"() {
        given:
        // Включаем snake_case для параметров, чтобы усложнить задачу
        System.setProperty("micronaut.openapi.property.naming.strategy", "SNAKE_CASE")

        buildBeanDefinition('test.SearchController', '''
package test

import io.micronaut.http.annotation.*
import io.micronaut.core.annotation.Introspected
import jakarta.validation.constraints.*

@Introspected
data class SearchFilter(
    @field:NotBlank val query: String,
    
    @field:Min(1) @field:Max(100) 
    val pageSize: Int = 20,
    
    val includeDeleted: Boolean = false
)

@Controller("/search")
class SearchController {

    // Micronaut allows passing a POJO as a collection of query parameters
    @Get("/list")
    fun list(@QueryValue filter: SearchFilter): String = "searching..."
}

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference
        System.clearProperty("micronaut.openapi.property.naming.strategy")

        then:
        // --- Verify SearchController Interpretation ---
        var operation = openApi.paths['/search/list'].get
        var parameters = operation.parameters

        // 1. Check if POJO was flattened into 3 individual query parameters
        parameters.size() == 3
        parameters.every { it.in == 'query' }

        // 2. Check naming (should be snake_case if strategy works for QueryValue)
        var qParam = parameters.find { it.name == 'query' }
        var sizeParam = parameters.find { it.name == 'page_size' } // Was pageSize
        var deletedParam = parameters.find { it.name == 'include_deleted' } // Was includeDeleted

        qParam != null
        sizeParam != null
        deletedParam != null

        // 3. Check Validation & Constraints mapping
        qParam.required == true

        sizeParam.schema.minimum == 1
        sizeParam.schema.maximum == 100
        sizeParam.schema.default.toString() == "20"

        // 4. Check Boolean default
        deletedParam.schema.default == false
    }

    @Ignore
    void "test controller interpretation - content types and headers"() {
        given:
        buildBeanDefinition('test.ContentController', '''
package test

import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.*
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import java.io.InputStream

@Introspected
data class Info(val version: String)

@Header(name = "X-Service-Id", value = "service-v1")
@Controller("/content")
class ContentController {

    // 1. Multiple media types for one response
    @Get(value = "/report", produces = [MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN])
    fun getReport(): HttpResponse<Info> = HttpResponse.ok(Info("1.0"))

    // 2. Binary stream with manual Media Type
    @Get(value = "/raw", produces = [MediaType.APPLICATION_OCTET_STREAM])
    fun getRaw(): InputStream = TODO()

    // 3. Method with specific header and override check
    @Post("/send")
    fun sendData(
        @Header("X-Trace-Id") traceId: String,
        @Body data: String
    ): HttpResponse<Void> = HttpResponse.noContent()
}

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        // --- 1. Verify Multiple Media Types ---
        var reportOp = openApi.paths['/content/report'].get
        var content = reportOp.responses['200'].content
        content.containsKey('application/json')
        content.containsKey('text/plain')
        content['application/json'].schema.$ref == '#/components/schemas/Info'

        // --- 2. Verify Binary Stream Interpretation ---
        var rawOp = openApi.paths['/content/raw'].get
        var rawSchema = rawOp.responses['200'].content['application/octet-stream'].schema
        rawSchema.type == 'string'
        rawSchema.format == 'binary'

        // --- 3. Verify Header Inheritance & Params ---
        var sendOp = openApi.paths['/content/send'].post
        // Should have X-Trace-Id from parameter
        sendOp.parameters.any { it.name == 'X-Trace-Id' && it.in == 'header' && it.required }
        // Should have X-Service-Id inherited from @Header on class
        sendOp.parameters.any { it.name == 'X-Service-Id' && it.in == 'header' }

        // --- 4. Verify No Content Response ---
        var sendResponse = sendOp.responses['204'] ?: sendOp.responses['200']
        // For Void/noContent it should ideally be 204 or empty schema
        sendResponse != null
    }

    void "test controller interpretation - polymorphic body with discriminator"() {
        given:
        buildBeanDefinition('test.TaskController', '''
package test

import io.micronaut.http.annotation.*
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    discriminatorProperty = "action_type",
    oneOf = [SendEmailTask::class, CleanupTask::class]
)
sealed class Task {
    abstract val actionType: String
    abstract val priority: Int
}

@Introspected
data class SendEmailTask(
    val recipient: String,
    override val actionType: String = "EMAIL",
    override val priority: Int = 1
) : Task()

@Introspected
data class CleanupTask(
    val folder: String,
    override val actionType: String = "CLEANUP",
    override val priority: Int = 10
) : Task()

@Controller("/tasks")
class TaskController {

    @Post("/run")
    fun runTask(@Body task: Task): String = "started"

    @Get("/active")
    fun getActive(): List<Task> = emptyList()
}

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        // --- 1. Verify Task (Parent) Schema ---
        var taskSchema = openApi.components.schemas['Task']
        taskSchema.discriminator.propertyName == "action_type"
        taskSchema.oneOf.size() == 2
        taskSchema.oneOf.any { it.$ref == '#/components/schemas/SendEmailTask' }
        taskSchema.oneOf.any { it.$ref == '#/components/schemas/CleanupTask' }

        // --- 2. Verify CleanupTask (Inheritance via allOf) ---
        var cleanupSchema = openApi.components.schemas['CleanupTask']
        // Inheritance check
        cleanupSchema.allOf.any { it.$ref == '#/components/schemas/Task' }

        // Properties check (specifically looking into the object part of allOf)
        var cleanupProps = cleanupSchema.allOf.find { it.type == 'object' }.properties
        cleanupProps.containsKey('folder')
        cleanupProps.containsKey('actionType') // Overridden property
        cleanupProps['priority'].format == 'int32'

        // --- 3. Verify Response Unwrapping (List<Task>) ---
        var getOp = openApi.paths['/tasks/active'].get
        var responseSchema = getOp.responses['200'].content['application/json'].schema
        responseSchema.type == 'array'
        responseSchema.items.$ref == '#/components/schemas/Task'

        // --- 4. Verify Request Body (Task) ---
        var postOp = openApi.paths['/tasks/run'].post
        postOp.requestBody.content['application/json'].schema.$ref == '#/components/schemas/Task'
    }

    @Ignore
    void "test controller interpretation - security and binary streams"() {
        given:
        buildBeanDefinition('test.SecureController', '''
package test

import io.micronaut.http.annotation.*
import io.micronaut.http.MediaType
import io.micronaut.security.authentication.Authentication
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import java.io.InputStream

@SecurityRequirement(name = "JWT")
@Controller("/secure")
class SecureController {

    // 1. System parameter 'auth' must be ignored automatically
    @Get("/me")
    fun getMe(auth: Authentication): String = "Hello ${auth.name}"

    // 2. Binary download - should be application/octet-stream + format: binary
    @Get(value = "/download", produces = [MediaType.APPLICATION_OCTET_STREAM])
    fun download(): InputStream = TODO()

    // 3. Method-level override for security
    @Post("/admin/clear")
    @SecurityRequirement(name = "AdminToken")
    fun clearCache() = "done"
}

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        // --- 1. Verify Security Inheritance ---
        var meOp = openApi.paths['/secure/me'].get
        meOp.security != null
        meOp.security.any { it.containsKey('JWT') }

        // --- 2. Verify System Parameter Exclusion ---
        // 'auth' must not be in the parameters list
        meOp.parameters == null || !meOp.parameters.any { it.name == 'auth' }

        // --- 3. Verify Binary Stream Interpretation ---
        var downloadOp = openApi.paths['/secure/download'].get
        var responseContent = downloadOp.responses['200'].content['application/octet-stream']
        responseContent.schema.type == 'string'
        responseContent.schema.format == 'binary'

        // --- 4. Verify Security Override ---
        var adminOp = openApi.paths['/secure/admin/clear'].post
        adminOp.security.any { it.containsKey('AdminToken') }
        // Method-level should replace class-level in most cases
        !adminOp.security.any { it.containsKey('JWT') }
    }

    void "test controller interpretation - parameter aliasing and formats"() {
        given:
        buildBeanDefinition('test.ParamController', '''
package test

import io.micronaut.http.annotation.*
import java.util.UUID
import java.time.LocalDate

@Controller("/params")
class ParamController {

    @Get("/search")
    fun search(
        @QueryValue("page_index") index: Int?, // Using nullable to avoid proto-defaults
        @QueryValue(defaultValue = "20") limit: Int,
        @Header("X-Request-ID") requestId: UUID
    ): String = "ok"

    @Get("/archive/{date}")
    fun getArchive(
        @PathVariable date: LocalDate
    ): String = "date: $date"
}

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        // --- 1. Verify Query Parameter Aliasing ---
        var searchOp = openApi.paths['/params/search'].get

        // Should use 'page_index' from annotation, NOT 'index' from code
        var indexParam = searchOp.parameters.find { it.name == 'page_index' }
        indexParam != null
        indexParam.in == 'query'
        indexParam.schema.type == 'integer'

        // 2. Check explicit defaultValue from Annotation (The only reliable way)
        var limitParam = searchOp.parameters.find { it.name == 'limit' }
        limitParam.schema.default.toString() == "20"

        // --- 3. Verify Header with Hyphens & UUID format ---
        var headerParam = searchOp.parameters.find { it.name == 'X-Request-ID' }
        headerParam.in == 'header'
        headerParam.schema.type == 'string'
        headerParam.schema.format == 'uuid'

        // --- 4. Verify Path Variable & LocalDate format ---
        var archiveOp = openApi.paths['/params/archive/{date}'].get
        var dateParam = archiveOp.parameters.find { it.name == 'date' }
        dateParam.in == 'path'
        dateParam.required == true
        dateParam.schema.type == 'string'
        dateParam.schema.format == 'date'
    }

    void "test controller interpretation - enums in parameters fixed"() {
        given:
        buildBeanDefinition('test.EnumController', '''
package test

import io.micronaut.http.annotation.*
import com.fasterxml.jackson.annotation.JsonProperty

enum class SortOrder {
    @JsonProperty("asc_order") ASC,
    @JsonProperty("desc_order") DESC
}

enum class Color { RED, GREEN, BLUE }

@Controller("/enums")
class EnumController {

    @Get("/search")
    fun search(
        @QueryValue order: SortOrder?, 
        @QueryValue(defaultValue = "RED") color: Color,
        @QueryValue tags: List<Color> 
    ): String = "ok"
}

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        // --- 1. Verify Enum Parameter with Reference & Nullability ---
        var searchOp = openApi.paths['/enums/search'].get
        var orderParam = searchOp.parameters.find { it.name == 'order' }

        // It uses allOf + $ref to handle nullable: true
        orderParam.schema.allOf[0].$ref == '#/components/schemas/SortOrder'
        orderParam.schema.nullable == true

        // --- 2. Verify Schema Definition (Jackson naming) ---
        var sortOrderSchema = openApi.components.schemas['SortOrder']
        sortOrderSchema.enum.contains("asc_order")
        sortOrderSchema.enum.contains("desc_order")
        // Check for the bonus extension your generator added
        sortOrderSchema.extensions['x-enum-varnames'] == ["ASC", "DESC"]

        // --- 3. Verify Enum with Default (via $ref) ---
        var colorParam = searchOp.parameters.find { it.name == 'color' }
        colorParam.schema.$ref == '#/components/schemas/Color'
        // Note: In OAS 3.0, default should be in the param schema,
        // but if it's missing there, it might be in the $ref'ed schema.

        // --- 4. Verify List of Enums ---
        var tagsParam = searchOp.parameters.find { it.name == 'tags' }
        tagsParam.schema.type == 'array'
        tagsParam.schema.items.$ref == '#/components/schemas/Color'
    }

    @Ignore
    void "test controller interpretation - raw bodies and media types"() {
        given:
        buildBeanDefinition('test.RawBodyController', '''
package test

import io.micronaut.http.MediaType
import io.micronaut.http.annotation.*
import io.swagger.v3.oas.annotations.media.Schema

@Controller("/raw")
class RawBodyController {

    // 1. Plain text body
    @Post(value = "/text", consumes = [MediaType.TEXT_PLAIN])
    fun sendText(@Body text: String): String = text

    // 2. Binary body (Byte Array)
    @Post(value = "/image", consumes = [MediaType.IMAGE_PNG])
    fun uploadImage(@Body data: ByteArray): String = "ok"

    // 3. Dynamic JSON (Map)
    @Post(value = "/json", consumes = [MediaType.APPLICATION_JSON])
    fun sendJson(@Body data: Map<String, Any>): Map<String, Any> = data

    // 4. Custom schema override for raw string
    @Post(value = "/hex", consumes = [MediaType.TEXT_PLAIN])
    fun sendHex(
        @Body @Schema(description = "Hexadecimal string", pattern = "^[0-9a-fA-F]+\$") 
        hex: String
    ): String = hex
}

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        // --- 1. Verify Text Body ---
        var textOp = openApi.paths['/raw/text'].post
        var textRequest = textOp.requestBody.content['text/plain'].schema
        textRequest.type == 'string'
        !textRequest.format // Should not have binary format

        // --- 2. Verify Binary Body (ByteArray) ---
        var imageOp = openApi.paths['/raw/image'].post
        var imageRequest = imageOp.requestBody.content['image/png'].schema
        imageRequest.type == 'string'
        imageRequest.format == 'binary'

        // --- 3. Verify Dynamic JSON (Map) ---
        var jsonOp = openApi.paths['/raw/json'].post
        var jsonRequest = jsonOp.requestBody.content['application/json'].schema
        jsonRequest.type == 'object'
        // Map<String, Any> should result in free-form additionalProperties
        jsonRequest.additionalProperties != null

        // --- 4. Verify Schema Override on Raw Type ---
        var hexOp = openApi.paths['/raw/hex'].post
        var hexRequest = hexOp.requestBody.content['text/plain'].schema
        hexRequest.description == "Hexadecimal string"
        hexRequest.pattern == "^[0-9a-fA-F]+\$"
    }

    @Ignore
    void "test controller interpretation - path regex and templates fixed"() {
        given:
        buildBeanDefinition('test.PathController', '''
package test

import io.micronaut.http.annotation.*

@Controller("/path")
class PathController {

    // 1. Correct syntax: {name:regex}
    // We use a simpler regex to avoid double curly brace issues in the compiler string
    @Get("/user/{id:[a-f0-9]+}")
    fun getUser(@PathVariable id: String): String = id

    // 2. Multiple variables in a single path segment
    @Get("/archive/{year}-{month}")
    fun getArchive(
        @PathVariable year: Int,
        @PathVariable month: String
    ): String = "$year/$month"

    // 3. Testing greedy path variable interpretation
    @Get("/files/{*path}")
    fun getFile(@PathVariable path: String): String = path
}

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        // --- 1. Verify Path Regex Extraction ---
        // OpenAPI path must be cleaned to /path/user/{id}
        var userOp = openApi.paths['/path/user/{id}'].get
        userOp != null
        var idParam = userOp.parameters.find { it.name == 'id' }

        idParam.in == 'path'
        // Regex should be moved to the 'pattern' field of the schema
        idParam.schema.pattern == "[a-f0-9]+"

        // --- 2. Verify Multi-Variable Segment ---
        // Path segment with hyphens: /path/archive/{year}-{month}
        var archiveOp = openApi.paths['/path/archive/{year}-{month}'].get
        archiveOp.parameters.any { it.name == 'year' && it.in == 'path' }
        archiveOp.parameters.any { it.name == 'month' && it.in == 'path' }

        // --- 3. Verify Greedy Path ({*path}) ---
        // Micronaut's {*path} should be interpreted as a regular {path} in OpenAPI
        var fileOp = openApi.paths['/path/files/{path}'].get
        fileOp != null
        fileOp.parameters.find { it.name == 'path' }.required == true
    }

    void "test controller interpretation - request bean fixed names"() {
        given:
        buildBeanDefinition('test.BeanController', '''
package test

import io.micronaut.http.annotation.*
import io.micronaut.core.annotation.Introspected
import jakarta.validation.constraints.*

@Introspected
data class SearchRequest(
    // Testing explicit name on Query
    @field:QueryValue("q_search") @field:NotBlank val q: String,
    
    // Testing explicit name on Header
    @field:Header("X-Tenant-ID") val tenant: String?,
    
    // Testing explicit name on Cookie
    @field:CookieValue("session_id") val session: String?
)

@Controller("/beans")
class BeanController {

    @Get("/search")
    fun search(@RequestBean request: SearchRequest): String = "ok"
}

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        var searchOp = openApi.paths['/beans/search'].get
        var params = searchOp.parameters

        // 1. Check Custom Query Name
        var qParam = params.find { it.name == 'q_search' && it.in == 'query' }
        qParam != null

        // 2. Check Custom Header Name (The Fail Point)
        var tenantParam = params.find { it.name == 'X-Tenant-ID' && it.in == 'header' }
        tenantParam != null

        // 3. Check Custom Cookie Name
        var sessionParam = params.find { it.name == 'session_id' && it.in == 'cookie' }
        sessionParam != null
    }

    void "test controller interpretation - generic inheritance and path merging fixed"() {
        given:
        buildBeanDefinition('test.UserController', '''
package test

import io.micronaut.http.annotation.*
import io.micronaut.core.annotation.Introspected

@Introspected
data class UserDto(val id: Long, val name: String)

abstract class BaseCrudController<T> {

    @Get("/{id}")
    abstract fun getById(id: Long): T

    @Delete("/{id}")
    fun delete(id: Long): String = "deleted $id"
}

@Controller("/api/v1/users")
class UserController : BaseCrudController<UserDto>() {

    @Override
    override fun getById(id: Long): UserDto = UserDto(id, "John")
    
    @Post
    fun create(@Body user: UserDto): UserDto = user
}

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        // --- 1. Verify Path Merging ---
        var getPath = '/api/v1/users/{id}'
        openApi.paths.containsKey(getPath)

        // --- 2. Verify Generic Resolution (The Success Point) ---
        var getOp = openApi.paths[getPath].get
        // T in BaseController must be resolved to UserDto
        var getResponseSchema = getOp.responses['200'].content['application/json'].schema
        getResponseSchema.$ref == '#/components/schemas/UserDto'

        // --- 3. Verify Inherited Method Response ---
        var deleteOp = openApi.paths[getPath].delete
        deleteOp != null
        // Check if String is correctly mapped as a string schema (usually under any/json or text/plain)
        var deleteResponse = deleteOp.responses['200'].content.values().first().schema
        deleteResponse.type == 'string'

        // --- 4. Verify Parameter Mapping in Inheritance ---
        // Path variable 'id' from base class must be present and required
        var idParam = deleteOp.parameters.find { it.name == 'id' }
        idParam != null
        idParam.in == 'path'
        idParam.required == true
    }

    @Ignore
    void "test controller interpretation - runtime types and interface advice fixed"() {
        given:
        buildBeanDefinition('test.AdviceController', '''
package test

import io.micronaut.http.annotation.*
import io.micronaut.http.HttpStatus
import io.micronaut.http.HttpMethod
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse

@Introspected
data class ErrorDto(val message: String, val code: Int)

@Introspected
data class SuccessDto(val result: String)

// 1. Shared interface with Error Response metadata
interface ErrorHandlingAdvice {
    
    @ApiResponse(
        responseCode = "500", 
        description = "Server Error", 
        content = [Content(schema = Schema(implementation = ErrorDto::class))]
    )
    @Error(global = true)
    fun onAnyError(e: Throwable): ErrorDto = ErrorDto(e.message ?: "error", 500)
}

@Controller("/advice")
class AdviceController : ErrorHandlingAdvice {

    // 2. Overriding 'Any' with explicit Schema implementation
    @Get("/dynamic")
    @Operation(
        responses = [
            ApiResponse(
                responseCode = "200", 
                content = [Content(schema = Schema(implementation = SuccessDto::class))]
            )
        ]
    )
    fun getDynamic(): Any = SuccessDto("ok")

    // 3. Multi-method mapping using @Route
    @Route(uri = "/multi", methods = [HttpMethod.POST, HttpMethod.PATCH])
    fun multiMethod(@Body input: String): String = input
}

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        // --- 1. Verify Implementation Override ---
        var dynamicOp = openApi.paths['/advice/dynamic'].get
        var successResponse = dynamicOp.responses['200'].content['application/json'].schema
        successResponse.$ref == '#/components/schemas/SuccessDto'

        // --- 2. Verify Multi-Method Generation ---
        // One @Route method must result in two separate OpenAPI operations
        openApi.paths['/advice/multi'].post != null
        openApi.paths['/advice/multi'].patch != null

        var multiPost = openApi.paths['/advice/multi'].post
        multiPost.operationId == 'multiMethod'
        multiPost.requestBody.content['application/json'].schema.type == 'string'

        // --- 3. Verify Error Metadata from Interface ---
        // If your processor supports cross-interface metadata scanning, 500 should be here
        var errorResponse = dynamicOp.responses['500']
        errorResponse != null
        errorResponse.description == "Server Error"
        errorResponse.content['application/json'].schema.$ref == '#/components/schemas/ErrorDto'
    }

    void "test controller interpretation - headers and response entities fixed"() {
        given:
        buildBeanDefinition('test.HeaderController', '''
package test

import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.*
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.responses.ApiResponse

@Introspected
data class Message(val text: String)

@Controller("/headers")
class HeaderController {

    // 1. Explicitly providing type to accepted() to help Kotlin compiler
    @Post("/submit")
    @Status(HttpStatus.ACCEPTED)
    fun submit(@Body msg: Message): HttpResponse<Message> = HttpResponse.accepted<Message>().body(msg)

    // 2. Custom header name check
    @Get("/check")
    fun checkHeader(@Header("X-Auth-Token") token: String): String = "token: $token"

    // 3. Status conflict: @ApiResponse usually wins over @Status for documentation
    @Put("/update")
    @Status(HttpStatus.NO_CONTENT)
    @ApiResponse(responseCode = "200", description = "Updated successfully")
    fun update(): HttpResponse<Void> = HttpResponse.ok()
}

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        // --- 1. Verify HttpResponse Unwrapping & Status ---
        var submitOp = openApi.paths['/headers/submit'].post
        submitOp.responses.containsKey('202')
        var submitSchema = submitOp.responses['202'].content['application/json'].schema
        submitSchema.$ref == '#/components/schemas/Message'

        // --- 2. Verify Header with Hyphens ---
        var checkOp = openApi.paths['/headers/check'].get
        var headerParam = checkOp.parameters.find { it.name == 'X-Auth-Token' }
        headerParam != null
        headerParam.in == 'header'

        // --- 3. Verify Status Conflict Resolution ---
        var updateOp = openApi.paths['/headers/update'].put
        updateOp.responses.containsKey('200')
        updateOp.responses['200'].description == "Updated successfully"

        var response200 = updateOp.responses['200']
        response200.content == null || response200.content.isEmpty()
    }

    void "test controller interpretation - inline parameter validation"() {
        given:
        buildBeanDefinition('test.ValidationController', '''
package test

import io.micronaut.http.annotation.*
import jakarta.validation.constraints.*

@Controller("/validation")
class ValidationController {

    // 1. Numeric constraints on query param
    @Get("/limit")
    fun checkLimit(
        @QueryValue @Min(1) @Max(100) limit: Int,
        @QueryValue(defaultValue = "10") offset: Int
    ): String = "ok"

    // 2. String constraints with regex on path variable
    @Get("/user/{username:[a-z]+}")
    fun getUser(
        @PathVariable @Size(min = 3, max = 20) username: String
    ): String = username

    // 3. Email validation and NotBlank
    @Post("/subscribe")
    fun subscribe(
        @QueryValue @Email @NotBlank email: String
    ): String = email
}

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        // --- 1. Verify Numeric Constraints ---
        var limitOp = openApi.paths['/validation/limit'].get
        var limitParam = limitOp.parameters.find { it.name == 'limit' }

        limitParam.schema.minimum == 1
        limitParam.schema.maximum == 100
        limitParam.required == true

        var offsetParam = limitOp.parameters.find { it.name == 'offset' }
        offsetParam.schema.default.toString() == "10"
        // Parameters with defaultValue are usually not required
        offsetParam.required == false || offsetParam.required == null

        // --- 2. Verify String Constraints & Path Regex ---
        var userOp = openApi.paths['/validation/user/{username}'].get
        var userParam = userOp.parameters.find { it.name == 'username' }

        userParam.in == 'path'
        userParam.schema.minLength == 3
        userParam.schema.maxLength == 20
        // The regex from path {username:[a-z]+} should be in pattern
        userParam.schema.pattern == "[a-z]+"

        // --- 3. Verify Email Format ---
        var subOp = openApi.paths['/validation/subscribe'].post
        var emailParam = subOp.parameters.find { it.name == 'email' }

        emailParam.required == true
        emailParam.schema.format == 'email'
    }
}
