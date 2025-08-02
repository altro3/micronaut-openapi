/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.openapi.visitor;

import io.micronaut.core.annotation.Internal;

/**
 * Micronaut HTTP annotation class names.
 *
 * @since 6.18.0
 */
@Internal
public interface MnAnnotation {

    String ANN_STATUS = "io.micronaut.http.annotation.Status";
    String ANN_BODY = "io.micronaut.http.annotation.Body";
    String ANN_HEADER = "io.micronaut.http.annotation.Header";
    String ANN_HEADERS = "io.micronaut.http.annotation.Headers";
    String ANN_PART = "io.micronaut.http.annotation.Part";
    String ANN_PATH_VARIABLE = "io.micronaut.http.annotation.PathVariable";
    String ANN_QUERY_VALUE = "io.micronaut.http.annotation.QueryValue";
    String ANN_COOKIE_VALUE = "io.micronaut.http.annotation.CookieValue";
    String ANN_REQUEST_BEAN = "io.micronaut.http.annotation.RequestBean";
    String ANN_REQUEST_ATTRIBUTE = "io.micronaut.http.annotation.RequestAttribute";

    String ANN_CONTROLLER = "io.micronaut.http.annotation.Controller";
    String ANN_ENDPOINT = "io.micronaut.management.endpoint.annotation.Endpoint";
    String ANN_HTTP_METHOD_MAPPING = "io.micronaut.http.annotation.HttpMethodMapping";
    String ANN_URI_MAPPING = "io.micronaut.http.annotation.UriMapping";
    String ANN_CONSUMES = "io.micronaut.http.annotation.Consumes";
    String ANN_PRODUCES = "io.micronaut.http.annotation.Produces";
}
