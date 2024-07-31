/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.build.internal.openapi;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Map;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

/**
 * A task which simulates what the Gradle Micronaut plugin
 * would do. Must be used with the test entry point.
 */
public abstract class OpenApiGeneratorTask extends DefaultTask {

    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getOpenApiDefinition();

    @Input
    public abstract Property<String> getGeneratorKind();

    @Input
    public abstract Property<String> getLang();

    @Input
    public abstract Property<Boolean> getGeneratedAnnotation();

    @Input
    public abstract Property<Boolean> getKsp();

    @Input
    public abstract Property<Boolean> getUseOneOfInterfaces();

    @Input
    public abstract Property<Boolean> getClientPath();

    @Input
    public abstract Property<String> getClientId();

    @Input
    @Optional
    public abstract Property<Boolean> getAuth();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Internal
    public Provider<Directory> getGeneratedSourcesDirectory() {
        String lang = getLang().get();
        return lang.equalsIgnoreCase("JAVA") ? getOutputDirectory().dir("src/main/java") : getOutputDirectory().dir("src/main/kotlin");
    }

    @Internal
    public Provider<Directory> getGeneratedTestSourcesDirectory() {
        String lang = getLang().get();
        return lang.equalsIgnoreCase("JAVA") ? getOutputDirectory().dir("src/test/groovy") : getOutputDirectory().dir("src/test/kotlin");
    }

    @Input
    public abstract ListProperty<String> getOutputKinds();

    @Input
    public abstract ListProperty<Map<String, String>> getParameterMappings();

    @Input
    public abstract ListProperty<Map<String, String>> getResponseBodyMappings();

    @Optional
    @Input
    public abstract MapProperty<String, String> getNameMapping();

    @Optional
    @Input
    public abstract Property<String> getApiNamePrefix();

    @Optional
    @Input
    public abstract Property<String> getApiNameSuffix();

    @Optional
    @Input
    public abstract Property<String> getModelNamePrefix();

    @Optional
    @Input
    public abstract Property<String> getModelNameSuffix();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @TaskAction
    public void execute() throws IOException {
        var generatedSourcesDir = getGeneratedSourcesDirectory().get().getAsFile();
        var generatedTestSourcesDir = getGeneratedTestSourcesDirectory().get().getAsFile();
        var lang = getLang().get();
        var generatedAnnotation = getGeneratedAnnotation().get();

        Files.createDirectories(generatedSourcesDir.toPath());
        Files.createDirectories(generatedTestSourcesDir.toPath());
        getProject().getLogger().info("json: {}", getParameterMappings().get());
        getExecOperations().javaexec(javaexec -> {
            javaexec.setClasspath(getClasspath());
            javaexec.getMainClass().set("io.micronaut.openapi.testsuite.GeneratorMain");
            var args = new ArrayList<String>();
            args.add(getGeneratorKind().get());
            args.add(getOpenApiDefinition().get().getAsFile().toURI().toString());
            args.add(getOutputDirectory().get().getAsFile().getAbsolutePath());
            args.add(String.join(",", getOutputKinds().get()));
            args.add(getParameterMappings().get().toString());
            args.add(getResponseBodyMappings().get().toString());
            args.add(lang.toUpperCase());
            args.add(Boolean.toString(generatedAnnotation));
            args.add(Boolean.toString(getKsp().get()));
            args.add(Boolean.toString(getClientPath().get()));
            args.add(Boolean.toString(getUseOneOfInterfaces().get()));
            args.add(getNameMapping().get().toString());
            args.add(getClientId().getOrElse(""));
            args.add(getApiNamePrefix().getOrElse(""));
            args.add(getApiNameSuffix().getOrElse(""));
            args.add(getModelNamePrefix().getOrElse(""));
            args.add(getModelNameSuffix().getOrElse(""));
            args.add(Boolean.toString(getAuth().getOrElse(false)));
            javaexec.args(args);
        });
    }
}
