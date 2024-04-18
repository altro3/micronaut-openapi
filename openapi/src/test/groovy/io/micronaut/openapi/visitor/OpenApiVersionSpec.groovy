package io.micronaut.openapi.visitor

import io.micronaut.openapi.AbstractOpenApiTypeElementSpec
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import spock.util.environment.RestoreSystemProperties

class OpenApiVersionSpec extends AbstractOpenApiTypeElementSpec {

    void "test build OpenAPI with routing versions parameter"() {

        setup:
        System.setProperty("micronaut.router.versioning.enabled", "true")
        System.setProperty("micronaut.router.versioning.parameter.enabled", "true")

        when:
        buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.core.version.annotation.Version;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;

import jakarta.inject.Singleton;

@Controller("/versioned")
class VersionedController {

    @Version("1")
    @Get("/hello")
    String helloV1() {
        return "helloV1";
    }

    @Version("2")
    @Get("/hello")
    String helloV2() {
        return "helloV2";
    }

    @Post("/common")
    String common() {
        return null;
    }
}

@Singleton
class MyBean {}
''')
        then:
        Utils.testReference != null

        when:
        OpenAPI openAPI = Utils.testReference
        Operation operation = openAPI.paths."/versioned/hello".get

        then:

        operation.parameters
        operation.parameters.size() == 1
        operation.parameters.get(0).name == "api-version"
        operation.parameters.get(0).in == "query"
        operation.parameters.get(0).schema.type == "string"

        cleanup:
        System.clearProperty("micronaut.router.versioning.enabled")
        System.clearProperty("micronaut.router.versioning.parameter.enabled")
    }

    void "test build OpenAPI with routing versions header"() {

        setup:
        System.setProperty("micronaut.router.versioning.enabled", "true")
        System.setProperty("micronaut.router.versioning.header.enabled", "true")

        when:
        buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.core.version.annotation.Version;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import jakarta.inject.Singleton;

@Controller("/versioned")
class VersionedController {

    @Version("1")
    @Get("/hello")
    String helloV1() {
        return "helloV1";
    }

    @Version("2")
    @Get("/hello")
    String helloV2() {
        return "helloV2";
    }
}

@Singleton
class MyBean {}
''')
        then:
        Utils.testReference != null

        when:
        OpenAPI openAPI = Utils.testReference
        Operation operation = openAPI.paths."/versioned/hello".get

        then:

        operation.parameters
        operation.parameters.size() == 1
        operation.parameters.get(0).name == "X-API-VERSION"
        operation.parameters.get(0).in == "header"
        operation.parameters.get(0).schema.type == "string"

        cleanup:
        System.clearProperty("micronaut.router.versioning.enabled")
        System.clearProperty("micronaut.router.versioning.header.enabled")
    }

    void "test build OpenAPI with routing versions by parameter and header"() {

        setup:
        System.setProperty("micronaut.router.versioning.enabled", "true")
        System.setProperty("micronaut.router.versioning.parameter.enabled", "true")
        System.setProperty("micronaut.router.versioning.header.enabled", "true")

        when:
        buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.core.version.annotation.Version;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import jakarta.inject.Singleton;

@Controller("/versioned")
class VersionedController {

    @Version("1")
    @Get("/hello")
    String helloV1() {
        return "helloV1";
    }

    @Version("2")
    @Get("/hello")
    String helloV2() {
        return "helloV2";
    }
}

@Singleton
class MyBean {}
''')
        then:
        Utils.testReference != null

        when:
        OpenAPI openAPI = Utils.testReference
        Operation operation = openAPI.paths."/versioned/hello".get

        then:

        operation.parameters
        operation.parameters.size() == 2
        operation.parameters.get(0).name == "api-version"
        operation.parameters.get(0).in == "query"
        operation.parameters.get(0).schema.type == "string"

        operation.parameters.get(1).name == "X-API-VERSION"
        operation.parameters.get(1).in == "header"
        operation.parameters.get(1).schema.type == "string"

        cleanup:
        System.clearProperty("micronaut.router.versioning.enabled")
        System.clearProperty("micronaut.router.versioning.parameter.enabled")
        System.clearProperty("micronaut.router.versioning.header.enabled")
    }

    void "test build OpenAPI with routing versions by parameter and header with custom names"() {

        setup:
        System.setProperty("micronaut.router.versioning.enabled", "true")
        System.setProperty("micronaut.router.versioning.parameter.enabled", "true")
        System.setProperty("micronaut.router.versioning.parameter.names", "myApiParam1,myApiParam2")
        System.setProperty("micronaut.router.versioning.header.enabled", "true")
        System.setProperty("micronaut.router.versioning.header.names", "myApiHeader1,myApiHeader2")

        when:
        buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.core.version.annotation.Version;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import jakarta.inject.Singleton;

@Controller("/versioned")
class VersionedController {

    @Version("1")
    @Get("/hello")
    String helloV1() {
        return "helloV1";
    }

    @Version("2")
    @Get("/hello")
    String helloV2() {
        return "helloV2";
    }
}

@Singleton
class MyBean {}
''')
        then:
        Utils.testReference != null

        when:
        OpenAPI openAPI = Utils.testReference
        Operation operation = openAPI.paths."/versioned/hello".get

        then:

        operation.parameters
        operation.parameters.size() == 4

        def found1 = false
        def found2 = false
        def found3 = false
        def found4 = false

        operation.parameters.forEach {
            if (it.name == "myApiHeader1") {
                it.in == "header"
                it.schema.type == "string"
                found1 = true
            } else if (it.name == "myApiHeader2") {
                it.in == "header"
                it.schema.type == "string"
                found2 = true
            } else if (it.name == "myApiParam1") {
                it.in == "query"
                it.schema.type == "string"
                found3 = true
            } else if (it.name == "myApiParam2") {
                it.in == "query"
                it.schema.type == "string"
                found4 = true
            }
        }

        found1 && found2 && found3 && found4

        cleanup:
        System.clearProperty("micronaut.router.versioning.enabled")
        System.clearProperty("micronaut.router.versioning.parameter.enabled")
        System.clearProperty("micronaut.router.versioning.parameter.names")
        System.clearProperty("micronaut.router.versioning.header.enabled")
        System.clearProperty("micronaut.router.versioning.header.names")
    }

    void "test build OpenAPI with routing versions by parameter with different apis"() {

        setup:
        System.setProperty("micronaut.router.versioning.enabled", "true")
        System.setProperty("micronaut.router.versioning.parameter.enabled", "true")

        when:
        buildBeanDefinition('test.MyBean', '''
package test;

import java.util.Map;

import io.micronaut.core.version.annotation.Version;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

import jakarta.inject.Singleton;

@Controller("/versioned")
class VersionedController {

    @Version("1")
    @Post("/hello")
    MyResponse1 helloV1(@Body MyBodyV1 body) {
        return null;
    }

    @Version("2")
    @Post("/hello")
    MyResponse2 helloV2(@Body MyBodyV2 body) {
        return null;
    }
}

class MyBodyV1 {

    public String prop1;
}

class MyBodyV2 {

    public String prop1;
    public String prop2;
    public Map<String, Object> objects;
}

class MyResponse1 {

    public String res1;
}

class MyResponse2 {

    public String res2;
}
@Singleton
class MyBean {}
''')
        then:
        Utils.testReference != null

        when:
        OpenAPI openAPI = Utils.testReference
        Operation operation = openAPI.paths."/versioned/hello".post

        then:

        operation.parameters
//        operation.parameters.size() == 4
//        operation.parameters.get(0).name == "myApiParam1"
//        operation.parameters.get(0).in == "query"
//        operation.parameters.get(0).schema.type == "string"
//        operation.parameters.get(1).name == "myApiParam2"
//        operation.parameters.get(1).in == "query"
//        operation.parameters.get(1).schema.type == "string"
//
//        operation.parameters.get(2).name == "myApiHeader1"
//        operation.parameters.get(2).in == "header"
//        operation.parameters.get(2).schema.type == "string"
//        operation.parameters.get(3).name == "myApiHeader2"
//        operation.parameters.get(3).in == "header"
//        operation.parameters.get(3).schema.type == "string"

        cleanup:
        System.clearProperty("micronaut.router.versioning.enabled")
        System.clearProperty("micronaut.router.versioning.parameter.enabled")
    }

    @RestoreSystemProperties
    void "test group schemas with version"() {

        setup:
        System.setProperty("micronaut.router.versioning.enabled", "true")
        System.setProperty("micronaut.router.versioning.parameter.enabled", "true")

        when:
        buildBeanDefinition("test.MyBean", '''
package test;

import io.micronaut.core.version.annotation.Version;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@Controller("/demo")
class MyController {

    @Version("v1")
    @Get
    HelloResponseV1 indexV1(String id) {
        return null;
    }

    @Version("v2")
    @Get
    HelloResponseV2 indexV2(String name) {
        return null;
    }
}

@Serdeable.Serializable
class HelloResponseV1 {
    
    public String message;
}

@Serdeable.Serializable
class HelloResponseV2 {
    
    public String message;
}

@OpenAPIDefinition(
    info = @Info(
        title = "Title My API",
        version = "0.0",
        description = "My API"
    )
)
class Application {
}

@jakarta.inject.Singleton
public class MyBean {}
''')

        then:
        def openApis = Utils.testReferences
        openApis
        openApis.size() == 2

        def apiV1 = openApis.get(Pair.of(null, "v1")).getOpenApi()
        def apiV2 = openApis.get(Pair.of(null, "v2")).getOpenApi()

        apiV1.paths.'/demo'.get.responses.'200'.content.'application/json'.schema.$ref == '#/components/schemas/HelloResponseV1'
        apiV2.paths.'/demo'.get.responses.'200'.content.'application/json'.schema.$ref == '#/components/schemas/HelloResponseV2'
    }
}
