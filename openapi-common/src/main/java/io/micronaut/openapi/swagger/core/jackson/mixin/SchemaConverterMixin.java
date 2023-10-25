/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.openapi.swagger.core.jackson.mixin;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.swagger.v3.oas.models.media.Schema;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * This class is copied from swagger-core library.
 *
 * @since 4.6.0
 */
public abstract class SchemaConverterMixin {

    @JsonIgnore
    public abstract Map<String, Object> getJsonSchema();

    @JsonAnyGetter
    public abstract Map<String, Object> getExtensions();

    @JsonAnySetter
    public abstract void addExtension(String name, Object value);

    @JsonIgnore
    public abstract boolean getExampleSetFlag();

    @JsonInclude(Include.NON_NULL)
    public abstract Object getExample();

    @JsonIgnore
    public abstract Object getJsonSchemaImpl();

    @JsonIgnore
    public abstract BigDecimal getExclusiveMinimumValue();

    @JsonIgnore
    public abstract BigDecimal getExclusiveMaximumValue();

    @JsonIgnore
    public abstract Schema getContains();

    @JsonIgnore
    public abstract String get$id();

    @JsonIgnore
    public abstract String get$anchor();

    @JsonIgnore
    public abstract String get$schema();

    @JsonIgnore
    public abstract Set<String> getTypes();

    @JsonIgnore
    public abstract Map<String, Schema> getPatternProperties();

    @JsonIgnore
    public abstract List<Schema> getPrefixItems();

    @JsonIgnore
    public abstract String getContentEncoding();

    @JsonIgnore
    public abstract String getContentMediaType();

    @JsonIgnore
    public abstract Schema getContentSchema();

    @JsonIgnore
    public abstract Schema getPropertyNames();

    @JsonIgnore
    public abstract Object getUnevaluatedProperties();

    @JsonIgnore
    public abstract Integer getMaxContains();

    @JsonIgnore
    public abstract Integer getMinContains();

    @JsonIgnore
    public abstract Schema getAdditionalItems();

    @JsonIgnore
    public abstract Schema getUnevaluatedItems();

    @JsonIgnore
    public abstract Schema getIf();

    @JsonIgnore
    public abstract Schema getElse();

    @JsonIgnore
    public abstract Schema getThen();

    @JsonIgnore
    public abstract Map<String, Schema> getDependentSchemas();

    @JsonIgnore
    public abstract Map<String, List<String>> getDependentRequired();

    @JsonIgnore
    public abstract String get$comment();

    @JsonIgnore
    public abstract List<Object> getExamples();

    @JsonIgnore
    public abstract Object getConst();

}
