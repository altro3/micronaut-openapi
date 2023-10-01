package io.micronaut.openapi.visitor;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;

import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.openapi.adoc.OpenApiToAdocConverter;
import io.micronaut.openapi.visitor.group.OpenApiInfo;

import static io.micronaut.openapi.visitor.FileUtils.EXT_ADOC;
import static io.micronaut.openapi.visitor.FileUtils.EXT_JSON;
import static io.micronaut.openapi.visitor.FileUtils.EXT_YAML;
import static io.micronaut.openapi.visitor.FileUtils.EXT_YML;
import static io.micronaut.openapi.visitor.FileUtils.createDirectories;
import static io.micronaut.openapi.visitor.FileUtils.getDefaultFilePath;
import static io.micronaut.openapi.visitor.FileUtils.resolve;
import static io.micronaut.openapi.visitor.OpenApiConfigProperty.MICRONAUT_OPENAPI_ADOC_OUTPUT_DIR_PATH;
import static io.micronaut.openapi.visitor.OpenApiConfigProperty.MICRONAUT_OPENAPI_ADOC_OUTPUT_FILENAME;

/**
 * Method to convert final openapi file to adoc format.
 *
 * @since 5.2.0
 */
public final class AdocModule {

    private AdocModule() {
    }

    /**
     * Convert and save to file openAPI object in adoc format.
     *
     * @param openApiInfo openApiInfo object
     * @param props openapi-adoc properties
     * @param context visitor context
     */
    public static void convert(OpenApiInfo openApiInfo, Map<String, String> props, VisitorContext context) {

        try {
            var writer = new StringWriter();
            OpenApiToAdocConverter.convert(openApiInfo.getOpenApi(), props, writer);

            var adoc = writer.toString();

            var outputPath = getOutputPath(openApiInfo, props, context);
            context.info("Writing AsciiDoc OpenAPI file to destination: " + outputPath);
            context.getClassesOutputPath().ifPresent(path -> {
                // add relative paths for the specPath, and its parent META-INF/swagger
                // so that micronaut-graal visitor knows about them
                context.addGeneratedResource(path.relativize(outputPath).toString());
            });

            if (Files.exists(outputPath)) {
                Files.writeString(outputPath, adoc, StandardOpenOption.APPEND);
            } else {
                Files.writeString(outputPath, adoc);
            }
        } catch (Exception e) {
            context.warn("Can't convert to ADoc format\n" + Utils.printStackTrace(e), null);
        }
    }

    private static Path getOutputPath(OpenApiInfo openApiInfo, Map<String, String> props, VisitorContext context) {

        var fileName = props.get(MICRONAUT_OPENAPI_ADOC_OUTPUT_FILENAME);
        if (StringUtils.isEmpty(fileName)) {

            var openApiFilename = openApiInfo.getFilename();

            if (openApiFilename.endsWith(EXT_JSON)
                || openApiFilename.endsWith(EXT_YML)
                || openApiFilename.endsWith(EXT_YAML)) {
                fileName = openApiFilename.substring(0, openApiFilename.lastIndexOf('.'));
            }
            fileName += EXT_ADOC;
        }

        Path outputPath;
        String outputDir = props.get(MICRONAUT_OPENAPI_ADOC_OUTPUT_DIR_PATH);
        if (StringUtils.isNotEmpty(outputDir)) {
            outputPath = resolve(context, Paths.get(outputDir));
        } else {
            outputPath = getDefaultFilePath(fileName, context).get().getParent();
        }
        outputPath = outputPath.resolve(fileName);
        createDirectories(outputPath, context);

        return outputPath;
    }
}
