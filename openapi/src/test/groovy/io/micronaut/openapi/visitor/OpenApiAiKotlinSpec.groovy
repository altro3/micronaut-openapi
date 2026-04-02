package io.micronaut.openapi.visitor

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import spock.lang.Ignore
import spock.util.environment.RestoreSystemProperties

class OpenApiAiKotlinSpec extends AbstractKotlinCompilerSpec {

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

    void "test kotlin value class wrapping"() {
        given:
        buildBeanDefinition('test.ValueController', '''
package test

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import kotlin.jvm.JvmInline

@JvmInline
value class UserId(val value: String)

@Controller("/values")
class ValueController {
    @Get("/{id}")
    fun getById(id: UserId): UserId = id
}

@jakarta.inject.Singleton
public class MyBean
''')

        when:
        var openApi = Utils.testReference

        then:
        // Проверяем параметр в пути
        var parameter = openApi.paths['/values/{id}'].get.parameters[0]
        parameter.schema.type == 'string'

        // Проверяем схему ответа
        var responseSchema = openApi.paths['/values/{id}'].get.responses['200'].content['application/json'].schema
        responseSchema.type == 'string'
    }

    void "test kotlin enum with json property"() {
        given:
        buildBeanDefinition('test.MyController', '''
package test

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import com.fasterxml.jackson.annotation.JsonProperty

@Controller("/enums")
class MyController {

    // Should use values from @JsonProperty in the generated schema
    @Get("/status")
    fun getStatus(): Status = Status.ACTIVE
}

enum class Status {
    @JsonProperty("active_user") ACTIVE,
    @JsonProperty("deleted_user") DELETED
}

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        var schema = openApi.components.schemas['Status']
        schema.enum != null
        schema.enum.contains("active_user")
        schema.enum.contains("deleted_user")
    }

    void "test json node field in kotlin data class"() {
        given:
        buildBeanDefinition('test.MyController', '''
package test

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import com.fasterxml.jackson.databind.JsonNode

@Controller("/nodes")
class MyController {

    @Get("/wrap")
    fun getWrap(): Wrapper = TODO()
}

data class Wrapper(
    val id: String,
    val rawData: JsonNode // Should be interpreted as type: object
)

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        var schema = openApi.components.schemas['Wrapper']
        var rawDataSchema = schema.properties['rawData']

        // Check if the fix prevents recursive scanning of Jackson's JsonNode
        rawDataSchema != null
        rawDataSchema.type == 'object'
        !openApi.components.schemas.containsKey('JsonNode')
    }

    void "test kotlin nullability"() {
        given:
        buildBeanDefinition('test.MyController', '''
package test

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Body

@Controller("/nullability")
class MyController {

    @Post("/check")
    fun check(@Body request: NullableRequest) {}
}

data class NullableRequest(
    val id: String,           // Not-null, should be required
    val note: String?         // Nullable, should NOT be required
)

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        var schema = openApi.components.schemas['NullableRequest']

        // Non-nullable Kotlin property should be required
        schema.required.contains("id")

        // Nullable Kotlin property should not be required
        !schema.required.contains("note")
    }

    void "test kotlin suspend function"() {
        given:
        buildBeanDefinition('test.MyController', '''
package test

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Controller("/suspend")
class MyController {

    // The compiler adds a Continuation parameter here. 
    // It must be ignored by the generator.
    @Get("/data")
    suspend fun getData(name: String): String = "hello $name"
}

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        var operation = openApi.paths['/suspend/data'].get
        // Only 'name' should be present, no 'continuation'
        operation.parameters.size() == 1
        operation.parameters[0].name == 'name'

        // Response should be string, not Continuation or Coroutine object
        operation.responses['200'].content['application/json'].schema.type == 'string'
    }

    void "test nested generics resolution"() {
        given:
        buildBeanDefinition('test.MyController', '''
package test

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Controller("/generics")
class MyController {

    // Testing complex nested structure resolution
    @Get("/nested")
    fun getNested(): List<Map<String, Int>> = emptyList()
}

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        var responseSchema = openApi.paths['/generics/nested'].get.responses['200'].content['application/json'].schema

        // Should be an array of objects (maps)
        responseSchema.type == 'array'
        responseSchema.items.type == 'object'
        // Map values should be integers
        responseSchema.items.additionalProperties.type == 'integer'
    }

    void "test schema hidden property"() {
        given:
        buildBeanDefinition('test.MyController', '''
package test

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Body
import io.swagger.v3.oas.annotations.media.Schema

@Controller("/hidden")
class MyController {

    @Post("/save")
    fun save(@Body request: SecretData) {}
}

data class SecretData(
    val publicId: String,
    @get:Schema(hidden = true) val secretKey: String // Should be hidden from OpenAPI
)

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        var schema = openApi.components.schemas['SecretData']
        schema.properties.containsKey('publicId')
        // secretKey must not be present in the properties map
        !schema.properties.containsKey('secretKey')
    }

    void "test kotlin sealed class with discriminator"() {
        given:
        buildBeanDefinition('test.MyController', '''
package test

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.swagger.v3.oas.annotations.media.Schema

@Controller("/polymorphic")
class MyController {

    // Should generate oneOf: [Success, Error] with type discriminator
    @Get("/result")
    fun getResult(): Result = Result.Success("ok")
}

@Schema(
    discriminatorProperty = "type",
    oneOf = [Result.Success::class, Result.Error::class]
)
sealed class Result {
    @Schema(name = "Success")
    data class Success(val data: String, val type: String = "SUCCESS") : Result()
    @Schema(name = "Error")
    data class Error(val message: String, val type: String = "ERROR") : Result()
}

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        var schema = openApi.components.schemas['Result']
        schema.oneOf != null
        schema.oneOf.size() == 2
        schema.discriminator != null
        schema.discriminator.propertyName == 'type'
    }

    void "test circular references"() {
        given:
        buildBeanDefinition('test.MyController', '''
package test

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Controller("/circular")
class MyController {

    // Self-referencing structure
    @Get("/tree")
    fun getTree(): TreeNode = TODO()
}

data class TreeNode(
    val name: String,
    val children: List<TreeNode>? // Circular reference here
)

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        var schema = openApi.components.schemas['TreeNode']
        schema.properties['children'].type == 'array'
        // Should be a $ref to TreeNode, not a recursive expansion
        schema.properties['children'].items.'$ref' == '#/components/schemas/TreeNode'
    }

    void "test kotlin internal and ignored properties visibility"() {
        given:
        buildBeanDefinition('test.MyController', '''
package test

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import com.fasterxml.jackson.annotation.JsonIgnore

@Controller("/visibility")
class MyController {

    @Get("/data")
    fun getData(): Data = Data("test", "secret", "internal-value")
}

class Data(
    val publicName: String,
    
    @get:JsonIgnore 
    val hiddenToken: String, // Should be ignored by SchemaDefinitionUtils
    
    internal val internalRef: String // Should be VISIBLE in the schema as a regular property
)

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        var schema = openApi.components.schemas['Data']

        // Standard public property
        schema.properties.containsKey('publicName')

        // Internal property should be present (standard Micronaut behavior)
        schema.properties.containsKey('internalRef')
        schema.properties['internalRef'].type == 'string'

        // Explicitly ignored property must be missing
        !schema.properties.containsKey('hiddenToken')
    }

    void "test kotlin flow response"() {
        given:
        buildBeanDefinition('test.MyController', '''
package test

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Controller("/streams")
class MyController {

    // Flow<String> should be represented as an array of strings
    @Get("/flow")
    fun getFlow(): Flow<String> = flowOf("a", "b")
}

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        var responseSchema = openApi.paths['/streams/flow'].get.responses['200'].content['application/json'].schema

        // Flow must be unwrapped to array
        responseSchema.type == 'array'
        responseSchema.items.type == 'string'
    }

    void "test multiple json node types"() {
        given:
        buildBeanDefinition('test.MyController', '''
package test

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Body

@Controller("/nodes")
class MyController {

    @Post("/double")
    fun process(@Body request: DoubleNodeRequest) {}
}

data class DoubleNodeRequest(
    val jacksonNode: com.fasterxml.jackson.databind.JsonNode,
    val micronautNode: io.micronaut.json.tree.JsonNode
)

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        var schema = openApi.components.schemas['DoubleNodeRequest']

        // Both should be simple objects thanks to your PR #2651
        schema.properties['jacksonNode'].type == 'object'
        schema.properties['micronautNode'].type == 'object'

        // No recursive leakage
        !openApi.components.schemas.containsKey('JsonNode')
    }

    void "test kotlin type alias"() {
        given:
        buildBeanDefinition('test.MyController', '''
package test

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

typealias UserMap = Map<String, Long>

@Controller("/aliases")
class MyController {

    @Get("/map")
    fun getMap(): UserMap = emptyMap()
}

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        var responseSchema = openApi.paths['/aliases/map'].get.responses['200'].content['application/json'].schema
        // Should be resolved to an object with additionalProperties: integer (long)
        responseSchema.type == 'object'
        responseSchema.additionalProperties.type == 'integer'
        responseSchema.additionalProperties.format == 'int64'
    }

    void "test recursive generics"() {
        given:
        buildBeanDefinition('test.MyController', '''
package test

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

interface Node<T : Node<T>> {
    val parent: T?
}

class StringNode(override val parent: StringNode?, val value: String) : Node<StringNode>

@Controller("/nodes")
class MyController {

    @Get("/recursive")
    fun getNode(): StringNode = TODO()
}

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        var schema = openApi.components.schemas['StringNode']
        schema.properties.containsKey('parent')
        schema.properties.containsKey('value')

        // In 6.20.x for Kotlin nullable refs, it uses allOf to wrap the $ref
        var parentProperty = schema.properties['parent']
        parentProperty.nullable == true
        parentProperty.allOf != null
        parentProperty.allOf[0].'$ref' == '#/components/schemas/StringNode'

        schema.properties['value'].type == 'string'
    }

    void "test generic inheritance resolution"() {
        given:
        buildBeanDefinition('test.MyController', '''
package test

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import jakarta.inject.Singleton

@Controller("/generic")
class MyController {

    @Get("/data")
    fun getData(): StringResponse = StringResponse("ok")
}

open class BaseResponse<T>(val data: T)
class StringResponse(data: String) : BaseResponse<String>(data)

@Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        // StringResponse should have 'data' property of type 'string'
        var schema = openApi.components.schemas['StringResponse']
        schema.properties['data'].type == 'string'
    }

    void "test kotlin validation annotations"() {
        given:
        buildBeanDefinition('test.MyController', '''
package test

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Body
import jakarta.validation.constraints.*

@Controller("/validation")
class MyController {

    @Post("/save")
    fun save(@Body @jakarta.validation.Valid request: ValidRequest) {}
}

data class ValidRequest(
    @field:NotBlank @field:Size(min = 3, max = 20) val name: String,
    @field:Min(1) @field:Max(100) val age: Int
)

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        var schema = openApi.components.schemas['ValidRequest']
        var name = schema.properties['name']
        var age = schema.properties['age']

        name.minLength == 3
        name.maxLength == 20
        age.minimum == 1
        age.maximum == 100
    }

    void "test list of json nodes"() {
        given:
        buildBeanDefinition('test.MyController', '''
package test

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import com.fasterxml.jackson.databind.JsonNode

@Controller("/nodes")
class MyController {

    @Get("/list")
    fun getNodes(): List<JsonNode> = emptyList()
}

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        var responseSchema = openApi.paths['/nodes/list'].get.responses['200'].content['application/json'].schema

        // Should be array of objects
        responseSchema.type == 'array'
        responseSchema.items.type == 'object'

        // Ensure no recursion leaked
        !openApi.components?.schemas?.containsKey('JsonNode')
    }

    void "test custom type mapping for json node"() {
        given:
        buildBeanDefinition('test.MyController', '''
package test

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.swagger.v3.oas.annotations.media.Schema
import com.fasterxml.jackson.databind.JsonNode

@Controller("/custom")
class MyController {

    // Overriding JsonNode with a specific string format for this endpoint
    @get:Schema(type = "string", format = "json")
    val data: JsonNode = TODO()

    @Get("/info")
    fun getInfo(): JsonNode = data
}

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        var responseSchema = openApi.paths['/custom/info'].get.responses['200'].content['application/json'].schema

        // Explicit annotation should have priority over your global JsonNode fix
        responseSchema.type == 'string'
        responseSchema.format == 'json'
    }

    void "test kotlin singleton object response"() {
        given:
        buildBeanDefinition('test.ObjectController', '''
package test

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Controller("/objects")
class ObjectController {
    @Get("/const")
    fun getConst(): Config = Config
}

object Config {
    val version = "1.0.0"
    val env = "prod"
}

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        var schema = openApi.components.schemas['Config']
        schema != null
        schema.type == 'object'
        schema.properties.containsKey('version')
        schema.properties['env'].type == 'string'
    }

    @RestoreSystemProperties
    void "test naming strategy snake_case"() {
        given:
        // Включаем snake_case через системное свойство для теста
        System.setProperty("micronaut.openapi.property.naming.strategy", "SNAKE_CASE")

        buildBeanDefinition('test.NamingController', '''
package test

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Body

@Controller("/naming")
class NamingController {
    @Post("/save")
    fun save(@Body request: UserProfile) {}
}

data class UserProfile(
    val firstName: String,
    val lastName: String
)

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        var schema = openApi.components.schemas['UserProfile']
        // Поля должны быть переименованы в snake_case в схеме
        schema.properties.containsKey('first_name')
        schema.properties.containsKey('last_name')
        !schema.properties.containsKey('firstName')
    }

    void "test custom wrapper unwrapping"() {
        given:
        buildBeanDefinition('test.WrapperController', '''
package test

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Controller("/wrapper")
class WrapperController {
    @Get("/data")
    fun getData(): MyResult<String> = MyResult("success")
}

data class MyResult<T>(val data: T)

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        // Проверяем, что MyResult_String_ создается корректно с вложенным типом string
        var schema = openApi.components.schemas.find { it.key.startsWith('MyResult') }
        schema.value.properties['data'].type == 'string'
    }

    @Ignore
    void "test deeply nested generics with wildcards"() {
        given:
        buildBeanDefinition('test.ComplexController', '''
package test

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Body

@Controller("/complex")
class ComplexController {
    @Post("/data")
    fun process(@Body data: Map<String, List<Set<List<Int>>>>) {}
}

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        var operation = openApi.paths['/complex/data'].post
        var schema = operation.requestBody.content['application/json'].schema

        // Map<String, ...> -> type: object, additionalProperties: ...
        schema.type == 'object'
        // List<...> -> type: array
        schema.additionalProperties.type == 'array'
        // Set<Int> -> type: array, items: integer, uniqueItems: true
        var setLayer = schema.additionalProperties.items
        setLayer.type == 'array'
        setLayer.uniqueItems == true
        setLayer.items.type == 'integer'
    }

    void "test complex nested architecture with sealed classes and value types"() {
        given:
        buildBeanDefinition('test.NotificationController', '''
package test

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Body
import io.swagger.v3.oas.annotations.media.Schema
import kotlin.jvm.JvmInline

@JvmInline
value class EventId(val value: String)

@Schema(discriminatorProperty = "type")
sealed class Notification {
    data class Email(val address: String, val subject: String) : Notification()
    data class Sms(val phoneNumber: String) : Notification()
}

open class BaseEnvelope<T>(
    val id: EventId,
    val payload: T
)

class NotificationBatch(
    id: EventId,
    payload: List<Notification>,
    val metadata: Map<String, List<Map<String, Int>>>
) : BaseEnvelope<List<Notification>>(id, payload)

@Controller("/notifications")
class NotificationController {
    @Post("/send")
    fun sendBatch(@Body batch: NotificationBatch): NotificationBatch = batch
}

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        // 1. Verify NotificationBatch uses allOf for inheritance
        var batchSchema = openApi.components.schemas['NotificationBatch']
        batchSchema.allOf != null
        batchSchema.allOf.any { it.'$ref' != null } // Reference to BaseEnvelope

        // 2. Verify the specific properties of NotificationBatch (metadata)
        var metadataPart = batchSchema.allOf.find { it.properties?.containsKey('metadata') }
        metadataPart.properties['metadata'].type == 'object'
        metadataPart.properties['metadata'].additionalProperties.type == 'array'

        // 3. Verify the generated Base class with resolved generics
        // Micronaut created a unique name for the generic combination
        var baseSchemaName = openApi.components.schemas.keySet().find { it.startsWith('BaseEnvelope') }
        var baseSchema = openApi.components.schemas[baseSchemaName]

        baseSchema.properties['id'].type == 'string' // EventId is correctly unwrapped
        baseSchema.properties['payload'].type == 'array'
        baseSchema.properties['payload'].items.'$ref' == '#/components/schemas/Notification'

        // 4. Verify Notification is present (even if it's currently an empty object with discriminator)
        openApi.components.schemas.containsKey('Notification')
        openApi.components.schemas['Notification'].discriminator.propertyName == 'type'
    }

    void "test diverse controller method signatures and request bean"() {
        given:
        buildBeanDefinition('test.MegaController', '''
package test

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.*
import io.micronaut.http.multipart.CompletedFileUpload
import io.micronaut.core.annotation.Introspected
import java.util.Optional
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

@Controller("/mega")
class MegaController {

    // 1. Array in Query and Enum in Header
    @Get("/list")
    fun getList(
        @QueryValue ids: List<Long>,
        @Header("X-Mode") mode: DisplayMode = DisplayMode.SIMPLE
    ): List<String> = emptyList()

    // 2. Multipart File Upload - Fixed class name to CompletedFileUpload
    @Post(value = "/upload", consumes = [MediaType.MULTIPART_FORM_DATA])
    fun upload(
        @Part file: CompletedFileUpload,
        @Part attributes: Map<String, String>,
        request: HttpRequest<*> // This should be ignored by OpenAPI
    ) = "ok"

    // 3. RequestBean - flattening logic
    @Get("/search")
    fun search(@RequestBean request: SearchRequest): HttpResponse<String> = HttpResponse.ok()

    // 4. Form URL Encoded with multiple @Body fields
    @Put(value = "/update", consumes = [MediaType.APPLICATION_FORM_URLENCODED])
    fun update(
        @Body("user_id") userId: String,
        @Body("is_active") active: Boolean = true
    ) {}
}

@Introspected
data class SearchRequest(
    @QueryValue @field:NotBlank val q: String,
    @QueryValue(defaultValue = "10") @field:Min(1) val limit: Int,
    @Header("X-Tenant-Id") val tenant: String?
)

enum class DisplayMode { SIMPLE, DETAILED }

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        // --- 1. Query Arrays & Enums ---
        var listOp = openApi.paths['/mega/list'].get
        listOp.parameters.find { it.name == 'ids' }.schema.type == 'array'

        // --- 2. Multipart Form Data & Ignored Params ---
        var uploadOp = openApi.paths['/mega/upload'].post
        var multipartProps = uploadOp.requestBody.content['multipart/form-data'].schema.properties
        multipartProps['file'].type == 'string'
        multipartProps['file'].format == 'binary'

        // Ensure HttpRequest is NOT in the parameters list
        !uploadOp.parameters?.any { it.name == 'request' }

        // --- 3. RequestBean ---
        var searchOp = openApi.paths['/mega/search'].get
        var qParam = searchOp.parameters.find { it.name == 'q' }
        qParam.required == true

        var limitParam = searchOp.parameters.find { it.name == 'limit' }
        limitParam.schema.minimum == 1

        // --- 4. Form URL Encoded ---
        var updateOp = openApi.paths['/mega/update'].put
        var formProps = updateOp.requestBody.content['application/x-www-form-urlencoded'].schema.properties
        formProps.containsKey('user_id')
        formProps['is_active'].type == 'boolean'
    }

    void "test generic controller inheritance with nested wrappers"() {
        given:
        buildBeanDefinition('test.UserController', '''
package test

import io.micronaut.http.annotation.*
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

// 1. Generic wrapper for all API responses
data class ApiResponse<T>(
    val data: T,
    val metadata: Map<String, String> = emptyMap()
)

// 2. Generic interface with OpenAPI annotations
interface Readable<T, ID> {
    @Get("/{id}")
    fun getById(id: ID): ApiResponse<T>
}

// 3. Abstract base class to check annotation merging
abstract class AbstractCrudController<T, ID> : Readable<T, ID> {
    @Post("/")
    abstract fun save(@Body entity: T): ApiResponse<ID>
}

// 4. Concrete implementation
@Controller("/users")
class UserController : AbstractCrudController<UserDto, Long>() {
    
    override fun getById(id: Long): ApiResponse<UserDto> = TODO()

    override fun save(entity: UserDto): ApiResponse<Long> = TODO()

    // Adding a recursive structure check
    @Get("/tree")
    fun getTree(): ApiResponse<TreeNode> = TODO()
}

@Introspected
data class UserDto(val username: String, val email: String)

@Introspected
data class TreeNode(
    val name: String,
    val children: List<TreeNode>? = null // Recursive reference
)

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        // --- 1. Verify GET /users/{id} (Inherited from interface) ---
        var getOp = openApi.paths['/users/{id}'].get
        getOp != null
        var getResponseSchema = getOp.responses['200'].content['application/json'].schema
        // Should resolve to ApiResponse_UserDto_
        getResponseSchema.'$ref'.contains('ApiResponse_UserDto_')

        var userDtoSchema = openApi.components.schemas['UserDto']
        userDtoSchema.properties.containsKey('username')

        // --- 2. Verify POST /users (Inherited from abstract class) ---
        var postOp = openApi.paths['/users'].post
        postOp != null
        var postRequestSchema = postOp.requestBody.content['application/json'].schema
        postRequestSchema.'$ref' == '#/components/schemas/UserDto'

        var postResponseSchema = postOp.responses['200'].content['application/json'].schema
        // Should resolve to ApiResponse_Long_
        postResponseSchema.'$ref'.contains('ApiResponse_Long_')

        // --- 3. Verify Recursive TreeNode ---
        var treeNodeSchema = openApi.components.schemas['TreeNode']
        treeNodeSchema.properties['children'].type == 'array'
        treeNodeSchema.properties['children'].items.'$ref' == '#/components/schemas/TreeNode'

        // --- 4. Verify Metadata in Wrapper ---
        var wrapperName = openApi.components.schemas.keySet().find { it.startsWith('ApiResponse') }
        var wrapperSchema = openApi.components.schemas[wrapperName]
        wrapperSchema.properties['metadata'].type == 'object'
        wrapperSchema.properties['metadata'].additionalProperties.type == 'string'
    }

    void "test mega complex polymorphism and generic bounds"() {
        given:
        buildBeanDefinition('test.TaskController', '''
package test

import io.micronaut.http.annotation.*
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import kotlin.jvm.JvmInline

// 1. Value Class
@JvmInline
value class TaskId(val value: String)

// 2. Interface-based Polymorphism (The Hard Way)
@Schema(
    description = "Root task interface",
    anyOf = [Job::class, Step::class],
    discriminatorProperty = "kind"
)
interface Task {
    val kind: String
    val id: TaskId
}

@Introspected
data class Job(
    override val id: TaskId,
    override val kind: String = "JOB",
    val steps: List<Step>
) : Task

@Introspected
data class Step(
    override val id: TaskId,
    override val kind: String = "STEP",
    val duration: Long
) : Task

// 3. Generic Wrapper with Out-Projection (Bounded Wildcard)
data class TaskResponse<out T : Task>(
    val items: List<T>,
    val total: Int,
    val context: Map<String, List<TaskId>> // Value class in nested collection
)

@Controller("/tasks")
class TaskController {

    // 4. Endpoint returning generic polymorphic list
    @Get("/active")
    fun getActiveTasks(): TaskResponse<Task> = TODO()

    // 5. Complex Map with Object Keys (Stress test for JSON/YAML emitter)
    @Post("/map")
    fun processMap(@Body data: Map<TaskId, List<Job>>): Map<String, TaskId> = TODO()
}

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        // --- 1. Verify Interface Polymorphism ---
        var taskSchema = openApi.components.schemas['Task']
        taskSchema.anyOf != null
        taskSchema.anyOf.size() == 2
        taskSchema.anyOf.any { it.'$ref' == '#/components/schemas/Job' }
        taskSchema.anyOf.any { it.'$ref' == '#/components/schemas/Step' }
        taskSchema.discriminator.propertyName == 'kind'

        // --- 2. Verify Generic Resolution with Bounds ---
        // Should produce TaskResponse_Task_ or similar
        var responseSchemaName = openApi.components.schemas.keySet().find { it.startsWith('TaskResponse') }
        var responseSchema = openApi.components.schemas[responseSchemaName]

        var itemsSchema = responseSchema.properties['items']
        itemsSchema.type == 'array'
        itemsSchema.items.'$ref' == '#/components/schemas/Task'

        // --- 3. Verify Value Class in deep nesting ---
        // Map<String, List<TaskId>> -> TaskId must be string
        var contextSchema = responseSchema.properties['context']
        contextSchema.additionalProperties.type == 'array'
        contextSchema.additionalProperties.items.type == 'string'

        // --- 4. Verify Map with Object (Value Class) Keys ---
        // OpenAPI/JSON supports only string keys.
        // Micronaut OpenAPI must force TaskId to string even if used as a key.
        var postOp = openApi.paths['/tasks/map'].post
        var requestSchema = postOp.requestBody.content['application/json'].schema
        requestSchema.type == 'object'
        // Check that it's still a map (additionalProperties) and not a corrupted object
        requestSchema.additionalProperties.type == 'array'
        requestSchema.additionalProperties.items.'$ref' == '#/components/schemas/Job'

        // --- 5. Verify Response Map ---
        var responseMapSchema = postOp.responses['200'].content['application/json'].schema
        responseMapSchema.additionalProperties.type == 'string' // TaskId unwrapped
    }

    void "test ultimate boss - mutual recursion and dynamic schemas"() {
        given:
        buildBeanDefinition('test.BossController', '''
package test

import io.micronaut.http.annotation.*
import io.micronaut.core.annotation.Introspected
import com.fasterxml.jackson.annotation.JsonAnyGetter
import io.swagger.v3.oas.annotations.media.Schema
import kotlin.jvm.JvmInline

@JvmInline
value class NodeType(val code: String)

@Introspected
data class Alpha(
    val id: String,
    val beta: Beta?
)

@Introspected
data class Beta(
    val name: String,
    val relatedAlphas: List<Alpha> = emptyList()
)

interface LinkedNode<T : LinkedNode<T>> {
    val next: T?
}

@Introspected
data class ConcreteNode(
    override val next: ConcreteNode?,
    val value: String
) : LinkedNode<ConcreteNode>

@Introspected
@Schema(description = "Entity with dynamic properties")
class DynamicEntity(
    val coreId: String,
    @get:JsonAnyGetter
    @Schema(description = "Dynamic metadata properties")
    val extraData: Map<String, Any> = emptyMap()
)

@Controller("/boss")
class BossController {

    @Get("/result")
    fun getResult(): Result<Alpha> = Result.success(Alpha("1", null))

    @Post("/recursive")
    fun processNodes(@Body node: ConcreteNode): List<ConcreteNode> = listOf(node)

    @Get("/dynamic")
    fun getDynamic(): DynamicEntity = DynamicEntity("id")
}

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        // --- 1. Verify Mutual Recursion (Alpha <-> Beta) ---
        var alphaSchema = openApi.components.schemas['Alpha']
        var betaSchema = openApi.components.schemas['Beta']

        // Single object reference uses allOf wrapper for nullable: true
        alphaSchema.properties['beta'].allOf[0].$ref == '#/components/schemas/Beta'

        // Array items use direct $ref
        betaSchema.properties['relatedAlphas'].items.$ref == '#/components/schemas/Alpha'

        // --- 2. Verify Self-referencing (ConcreteNode -> ConcreteNode) ---
        var nodeSchema = openApi.components.schemas['ConcreteNode']
        // 'next' is nullable ConcreteNode? -> uses allOf
        nodeSchema.properties['next'].allOf[0].$ref == '#/components/schemas/ConcreteNode'

        // --- 3. Verify Dynamic Properties (@JsonAnyGetter) ---
        var dynamicSchema = openApi.components.schemas['DynamicEntity']
        dynamicSchema.properties.containsKey('coreId')
        !dynamicSchema.properties.containsKey('extraData')
        dynamicSchema.additionalProperties != null

        // --- 4. Verify Kotlin Result Unwrapping ---
        var resultOp = openApi.paths['/boss/result'].get
        var responseSchema = resultOp.responses['200'].content['application/json'].schema

        // Result is a container, so it should unwrap to a direct ref or allOf-ref to Alpha
        (responseSchema.$ref ?: responseSchema.allOf[0].$ref) == '#/components/schemas/Alpha'

        // --- 5. Verify Cleanup ---
        !openApi.components.schemas.containsKey('Result')
    }

    void "test shadow boss - delegation and aliasing"() {
        given:
        buildBeanDefinition('test.ShadowController', '''
package test

import io.micronaut.http.annotation.*
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import kotlin.jvm.JvmInline

// 1. Value class for a specialized string
@JvmInline
value class SecretToken(val value: String)

// 2. Interface to delegate
interface Identifiable {
    @get:Schema(description = "The unique identifier", example = "UUID-123")
    val id: String
}

class IdentifiableImpl(override val id: String) : Identifiable

// 3. Class using Delegation
@Introspected
class DelegatedUser(
    val email: String,
    identifiable: Identifiable // Pass delegate
) : Identifiable by identifiable // DELEGATION MAGIC HERE

@Controller("/shadow")
class ShadowController {

    // 4. Test Aliasing and Complex Result
    @Get("/token")
    fun getToken(): Map<SecretToken, List<DelegatedUser>> = TODO()

    // 5. Test property name override on complex objects
    @Post("/alias")
    fun testAlias(@Body request: AliasRequest): AliasRequest = request
}

@Introspected
data class AliasRequest(
    @get:Schema(name = "renamed_user")
    val originalUser: DelegatedUser
)

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        // --- 1. Verify Delegation (DelegatedUser) ---
        var userSchema = openApi.components.schemas['DelegatedUser']
        // 'email' is direct property
        userSchema.properties.containsKey('email')
        // 'id' is DELEGATED property - it MUST be present in DelegatedUser schema
        userSchema.properties.containsKey('id')
        userSchema.properties['id'].description == "The unique identifier"
        userSchema.properties['id'].example == "UUID-123"

        // --- 2. Verify Value Class as Map Key ---
        var tokenOp = openApi.paths['/shadow/token'].get
        var tokenResponse = tokenOp.responses['200'].content['application/json'].schema
        // Map<SecretToken, List<DelegatedUser>>
        // SecretToken must be unwrapped to string as a key
        tokenResponse.type == 'object'
        tokenResponse.additionalProperties.type == 'array'
        tokenResponse.additionalProperties.items.get$ref() == '#/components/schemas/DelegatedUser'

        // --- 3. Verify Property Alias (@get:Schema(name = ...)) ---
        var aliasSchema = openApi.components.schemas['AliasRequest']
        // 'originalUser' should be RENAME to 'renamed_user'
        !aliasSchema.properties.containsKey('originalUser')
        aliasSchema.properties.containsKey('renamed_user')
        aliasSchema.properties['renamed_user'].get$ref() == '#/components/schemas/DelegatedUser'
    }

    void "test chameleon boss - overlapping hierarchy and masking"() {
        given:
        buildBeanDefinition('test.ChameleonController', '''
package test

import io.micronaut.http.annotation.*
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

// 1. Sealed Hierarchy A
@Schema(discriminatorProperty = "a_type")
sealed class Alpha {
    data class A1(val value: String) : Alpha()
    data class A2(val timestamp: Instant) : Alpha()
}

// 2. Sealed Hierarchy B, containing Hierarchy A
@Schema(discriminatorProperty = "b_type")
sealed class Beta {
    data class B1(val alpha: Alpha) : Beta()
    data class B2(val tags: Map<String, Alpha>) : Beta()
}

// 3. The Chameleon: Private Masking
@Introspected
open class SecretBase {
    open val secretCode: String = "TOP_SECRET"
}

@Introspected
class PublicUser(
    val username: String,
    @get:Schema(hidden = true) override val secretCode: String = "CLEANED"
) : SecretBase()

// 4. Recursive Self-Inheriting List (The "Collection" Boss)
@Introspected
class NodeList : ArrayList<NodeList>() {
    val metadata: String = "list-info"
}

@Controller("/chameleon")
class ChameleonController {

    @Post("/process")
    fun process(@Body data: Map<String, Beta>): Beta = TODO()

    @Get("/user")
    fun getUser(): PublicUser = TODO()

    @Get("/nodes")
    fun getNodes(): NodeList = TODO()
}

@jakarta.inject.Singleton
public class MyBean {}
''')

        when:
        var openApi = Utils.testReference

        then:
        // --- 1. Verify Nested Sealed Hierarchies ---
        var betaSchema = openApi.components.schemas['Beta']
        betaSchema.oneOf.size() == 2

        var b1Schema = openApi.components.schemas['B1']
        // B1.alpha must point to Alpha (which is also a oneOf)
        b1Schema.properties['alpha'].allOf.$ref == '#/components/schemas/Alpha'

        var alphaSchema = openApi.components.schemas['Alpha']
        // Ensure Instant is a string, not a complex object
        var a2Schema = openApi.components.schemas['A2']
        a2Schema.properties['timestamp'].type == 'string'
        a2Schema.properties['timestamp'].format == 'date-time'

        // --- 2. Verify Property Masking (@Schema(hidden = true) on override) ---
        var userSchema = openApi.components.schemas['PublicUser']
        userSchema.properties.containsKey('username')
        // secretCode MUST NOT be present even though it exists in SecretBase
        !userSchema.properties.containsKey('secretCode')

        // --- 3. Verify Self-Inheriting List ---
        // This is tricky. NodeList is a List AND has its own properties.
        // OpenAPI usually represents this as an 'array' with properties
        // OR ignores properties and keeps it an 'array'.
        // Correct behavior: it should be an array or use allOf.
        var nodeSchema = openApi.components.schemas['NodeList']
        nodeSchema.type == 'array'
        // If the generator is smart, it might include 'metadata' via allOf
    }
}
