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
package io.micronaut.openapi.view;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.openapi.visitor.OpenApiApplicationVisitor;
import io.micronaut.openapi.visitor.Utils;

import static io.micronaut.openapi.visitor.OpenApiApplicationVisitor.MICRONAUT_INTERNAL_OPENAPI_FILENAME;

/**
 * Abstract View Config.
 *
 * @author croudet
 */
abstract class AbstractViewConfig {

    protected String prefix;
    protected String jsUrl = "";
    protected String specUrl;
    protected String finalUrlPrefix;
    protected String resourcesContextPath = "/res";
    protected String templatePath;
    protected boolean isDefaultJsUrl = true;
    protected boolean copyResources = true;
    protected boolean withFinalUrlPrefixCache = true;
    protected Map<String, Object> options = new HashMap<>();

    /**
     * An AbstractViewConfig.
     *
     * @param prefix The configuration key prefix.
     */
    protected AbstractViewConfig(String prefix) {
        this.prefix = prefix;
    }

    /**
     * Returns the converter associated with the key.
     *
     * @param key A key.
     *
     * @return A converter or null.
     */
    protected abstract Function<String, Object> getConverter(String key);

    protected abstract List<String> getResources();

    public String getTemplatePath() {
        return templatePath;
    }

    public abstract String render(String template, VisitorContext context);

    /**
     * Adds an option.
     *
     * @param entry The user specified entry.
     */
    protected void addAttribute(Map.Entry<String, String> entry) {
        String key = entry.getKey().substring(prefix.length());
        Function<String, Object> converter = getConverter(key);
        if (converter != null) {
            options.put(key, converter.apply(entry.getValue()));
        }
    }

    /**
     * Converts to html attributes.
     *
     * @return A String.
     */
    protected String toHtmlAttributes() {
        return options.entrySet().stream().map(e -> e.getKey() + "=\"" + e.getValue() + '"')
            .collect(Collectors.joining(" "));
    }

    protected String getFinalUrlPrefix(OpenApiViewConfig.RendererType rendererType, VisitorContext context) {
        if (finalUrlPrefix != null && withFinalUrlPrefixCache) {
            return finalUrlPrefix;
        }

        // process micronaut.openapi.server.context.path
        String serverContextPath = OpenApiApplicationVisitor.getConfigurationProperty(OpenApiApplicationVisitor.MICRONAUT_OPENAPI_CONTEXT_SERVER_PATH, context);
        if (serverContextPath == null) {
            serverContextPath = StringUtils.EMPTY_STRING;
        }
        String finalUrl = serverContextPath.startsWith(OpenApiViewConfig.SLASH) ? serverContextPath : OpenApiViewConfig.SLASH + serverContextPath;
        if (!finalUrl.endsWith(OpenApiViewConfig.SLASH)) {
            finalUrl += OpenApiViewConfig.SLASH;
        }

        // process micronaut.server.context-path
        String contextPath = OpenApiApplicationVisitor.getConfigurationProperty(OpenApiApplicationVisitor.MICRONAUT_SERVER_CONTEXT_PATH, context);
        if (contextPath == null) {
            contextPath = StringUtils.EMPTY_STRING;
        }
        finalUrl += contextPath.startsWith(OpenApiViewConfig.SLASH) ? contextPath.substring(1) : contextPath;
        if (!finalUrl.endsWith(OpenApiViewConfig.SLASH)) {
            finalUrl += OpenApiViewConfig.SLASH;
        }

        // standard path
        finalUrl += rendererType.getTemplatePath();
        finalUrl += finalUrl.endsWith(OpenApiViewConfig.SLASH) ? resourcesContextPath.substring(1) : resourcesContextPath;
        if (!finalUrl.endsWith(OpenApiViewConfig.SLASH)) {
            finalUrl += OpenApiViewConfig.SLASH;
        }

        finalUrlPrefix = finalUrl;

        return finalUrl;
    }

    /**
     * Builds and parse a View Config.
     *
     * @param <T> A View config type.
     * @param cfg A View config.
     * @param defaultOptions The default options.
     * @param properties The options to parse.
     * @param context Visitor context.
     *
     * @return A View config.
     */
    static <T extends AbstractViewConfig> T fromProperties(T cfg, Map<String, Object> defaultOptions, Map<String, String> properties, VisitorContext context) {

        String copyResources = properties.get(cfg.prefix + "copy-resources");
        if (StringUtils.isNotEmpty(copyResources) && "false".equalsIgnoreCase(copyResources)) {
            cfg.copyResources = false;
        }

        String specUrl = properties.get(cfg.prefix + "spec.url");
        if (specUrl != null) {
            cfg.specUrl = specUrl.replace(Utils.PLACEHOLDER_PREFIX + "filename" + Utils.PLACEHOLDER_POSTFIX,
                context != null ? context.get(MICRONAUT_INTERNAL_OPENAPI_FILENAME, String.class).orElse("") : "");
        }

        String jsUrl = properties.get(cfg.prefix + "js.url");
        if (StringUtils.isNotEmpty(jsUrl)) {
            cfg.jsUrl = jsUrl;
            cfg.isDefaultJsUrl = false;
        } else {
            String resourcesContextPath = properties.get(cfg.prefix + "resources.context.path");
            if (StringUtils.isNotEmpty(resourcesContextPath)) {
                cfg.resourcesContextPath = resourcesContextPath.startsWith("/") ? resourcesContextPath : "/" + resourcesContextPath;
            }
        }

        String templatePath = properties.get(cfg.prefix + "template.path");
        if (StringUtils.isNotEmpty(templatePath)) {
            cfg.templatePath = templatePath;
        }

        cfg.options.putAll(defaultOptions);
        properties.entrySet().stream().filter(entry -> entry.getKey().startsWith(cfg.prefix))
            .forEach(cfg::addAttribute);
        return cfg;
    }

    /**
     * Converts to a Boolean.
     *
     * @param v The input.
     *
     * @return A Boolean.
     */
    static Object asBoolean(String v) {
        return Boolean.valueOf(v);
    }

    /**
     * Converts to an Integer.
     *
     * @param v The input.
     *
     * @return An Integer.
     */
    static Object asInt(String v) {
        return Integer.valueOf(v);
    }

    /**
     * Converts to a String.
     *
     * @param v The input.
     *
     * @return A String.
     */
    static Object asString(String v) {
        return v;
    }

    /**
     * Converts to a quoted String.
     *
     * @param v The input.
     *
     * @return A quoted String.
     */
    static Object asQuotedString(String v) {
        return v == null ? null : '"' + v + '"';
    }

    /**
     * Converts to an enum.
     *
     * @param <T> An Enum class.
     *
     * @author croudet
     */
    static class EnumConverter<T extends Enum<T>> implements Function<String, Object> {

        private final Class<T> type;

        /**
         * EnumConverter.
         *
         * @param type An Enum type.
         */
        EnumConverter(Class<T> type) {
            this.type = type;
        }

        /**
         * Converts to an Enum.
         */
        @Override
        public Object apply(String v) {
            return v == null ? null : Enum.valueOf(type, v.toUpperCase(Locale.US));
        }
    }
}
