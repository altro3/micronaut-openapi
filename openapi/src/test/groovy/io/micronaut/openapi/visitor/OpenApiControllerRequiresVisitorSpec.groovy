package io.micronaut.openapi.visitor

import io.micronaut.context.env.Environment
import io.micronaut.openapi.AbstractOpenApiTypeElementSpec
import io.swagger.v3.oas.models.Paths

class OpenApiControllerRequiresVisitorSpec extends AbstractOpenApiTypeElementSpec {

    void "test requires env for controller"() {

        given:
        System.setProperty(OpenApiConfigProperty.MICRONAUT_CONFIG_FILE_LOCATIONS, "project:/src/test/resources/")
        System.setProperty(Environment.ENVIRONMENTS_PROPERTY, "env1,env2")

        buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

@Requires(env = {"env3"})
@Controller
class Controller1 {

    @Post("/c1")
    public HttpResponse<String> publicEndpoint() {
        return null;
    }
}

@Requires(env = {"env1"})
@Controller
class Controller2 {

    @Post("/c2")
    public HttpResponse<String> publicEndpoint() {
        return null;
    }
}

@jakarta.inject.Singleton
class MyBean {}
''')
        when:
        Paths paths = Utils.testReference?.paths

        then:
        paths
        paths.size() == 1
        paths."/c2"

        cleanup:
        System.clearProperty(Environment.ENVIRONMENTS_PROPERTY)
        System.clearProperty(OpenApiConfigProperty.MICRONAUT_CONFIG_FILE_LOCATIONS)
    }

    void "test requires not env for controller"() {

        given:
        System.setProperty(OpenApiConfigProperty.MICRONAUT_CONFIG_FILE_LOCATIONS, "project:/src/test/resources/")
        System.setProperty(Environment.ENVIRONMENTS_PROPERTY, "env1,env2")

        buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

@Requires(notEnv="env3")
@Controller
class Controller1 {

    @Post("/c1")
    public HttpResponse<String> publicEndpoint() {
        return null;
    }
}

@Requires(notEnv="env1")
@Controller
class Controller2 {

    @Post("/c2")
    public HttpResponse<String> publicEndpoint() {
        return null;
    }
}

@jakarta.inject.Singleton
class MyBean {}
''')
        when:
        Paths paths = Utils.testReference?.paths

        then:
        paths
        paths.size() == 1
        paths."/c1"

        cleanup:
        System.clearProperty(Environment.ENVIRONMENTS_PROPERTY)
        System.clearProperty(OpenApiConfigProperty.MICRONAUT_CONFIG_FILE_LOCATIONS)
    }

    void "test requires not env and env for controller"() {

        given:
        System.setProperty(OpenApiConfigProperty.MICRONAUT_CONFIG_FILE_LOCATIONS, "project:/src/test/resources/")
        System.setProperty(Environment.ENVIRONMENTS_PROPERTY, "env1,env2")

        buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

@Requires(env="env1",notEnv="env3")
@Controller
class Controller1 {

    @Post("/c1")
    public HttpResponse<String> publicEndpoint() {
        return null;
    }
}

@Requires(env="env2",notEnv="env1")
@Controller
class Controller2 {

    @Post("/c2")
    public HttpResponse<String> publicEndpoint() {
        return null;
    }
}

@jakarta.inject.Singleton
class MyBean {}
''')
        when:
        Paths paths = Utils.testReference?.paths

        then:
        paths
        paths.size() == 1
        paths."/c1"

        cleanup:
        System.clearProperty(Environment.ENVIRONMENTS_PROPERTY)
        System.clearProperty(OpenApiConfigProperty.MICRONAUT_CONFIG_FILE_LOCATIONS)
    }

    void "test requires multiple env for controller"() {

        given:
        System.setProperty(OpenApiConfigProperty.MICRONAUT_CONFIG_FILE_LOCATIONS, "project:/src/test/resources/")
        System.setProperty(Environment.ENVIRONMENTS_PROPERTY, "env1,env2")

        buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

@Requires(env={"env1","env2"})
@Controller
class Controller1 {

    @Post("/c1")
    public HttpResponse<String> publicEndpoint() {
        return null;
    }
}

@Requires(env={"env2","env3"})
@Controller
class Controller2 {

    @Post("/c2")
    public HttpResponse<String> publicEndpoint() {
        return null;
    }
}

@Requires(env={"env3","env4"})
@Controller
class Controller3 {

    @Post("/c3")
    public HttpResponse<String> publicEndpoint() {
        return null;
    }
}

@jakarta.inject.Singleton
class MyBean {}
''')
        when:
        Paths paths = Utils.testReference?.paths

        then:
        paths
        paths.size() == 2
        paths."/c1"
        paths."/c2"

        cleanup:
        System.clearProperty(Environment.ENVIRONMENTS_PROPERTY)
        System.clearProperty(OpenApiConfigProperty.MICRONAUT_CONFIG_FILE_LOCATIONS)
    }

    void "test requires multiple notenv for controller"() {

        given:
        System.setProperty(OpenApiConfigProperty.MICRONAUT_CONFIG_FILE_LOCATIONS, "project:/src/test/resources/")
        System.setProperty(Environment.ENVIRONMENTS_PROPERTY, "env1,env2")

        buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

@Requires(notEnv={"env1","env2"})
@Controller
class Controller1 {

    @Post("/c1")
    public HttpResponse<String> publicEndpoint() {
        return null;
    }
}

@Requires(notEnv={"env3"})
@Controller
class Controller2 {

    @Post("/c2")
    public HttpResponse<String> publicEndpoint() {
        return null;
    }
}

@jakarta.inject.Singleton
class MyBean {}
''')
        when:
        Paths paths = Utils.testReference?.paths

        then:
        paths
        paths.size() == 1
        paths."/c2"

        cleanup:
        System.clearProperty(Environment.ENVIRONMENTS_PROPERTY)
        System.clearProperty(OpenApiConfigProperty.MICRONAUT_CONFIG_FILE_LOCATIONS)
    }

    void "test requires multiple annotations with env for controller"() {

        given:
        System.setProperty(OpenApiConfigProperty.MICRONAUT_CONFIG_FILE_LOCATIONS, "project:/src/test/resources/")
        System.setProperty(Environment.ENVIRONMENTS_PROPERTY, "env1,env2")

        buildBeanDefinition('test.MyBean', '''
package test;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

@Requires(env="env1")
@Requires(env={"env2","env3"})
@Requires(env={"env3","env4"})
@Controller
class Controller1 {

    @Post("/c1")
    public HttpResponse<String> publicEndpoint() {
        return null;
    }
}

@Requires(env={"env1"})
@Requires(env={"env2"})
@Controller
class Controller2 {

    @Post("/c2")
    public HttpResponse<String> publicEndpoint() {
        return null;
    }
}

@Requires(env={"env1"})
@Requires(env={"env3"})
@Controller
class Controller3 {

    @Post("/c3")
    public HttpResponse<String> publicEndpoint() {
        return null;
    }
}

@jakarta.inject.Singleton
class MyBean {}
''')
        when:
        Paths paths = Utils.testReference?.paths

        then:
        paths
        paths.size() == 1
        paths."/c2"

        cleanup:
        System.clearProperty(Environment.ENVIRONMENTS_PROPERTY)
        System.clearProperty(OpenApiConfigProperty.MICRONAUT_CONFIG_FILE_LOCATIONS)
    }
}
