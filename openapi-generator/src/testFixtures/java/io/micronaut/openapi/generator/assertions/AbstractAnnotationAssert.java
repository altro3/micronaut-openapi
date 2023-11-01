package io.micronaut.openapi.generator.assertions;

import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.google.common.collect.ImmutableMap;
import org.assertj.core.api.ListAssert;
import org.assertj.core.util.CanIgnoreReturnValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

//CHECKSTYLE:OFF
@CanIgnoreReturnValue
public abstract class AbstractAnnotationAssert<ACTUAL extends AbstractAnnotationAssert<ACTUAL>> extends ListAssert<AnnotationExpr> {

    protected AbstractAnnotationAssert(final List<AnnotationExpr> annotationExpr) {
        super(annotationExpr);
    }

    @Override
    public ACTUAL hasSize(final int size) {
        super.hasSize(size);
        return myself();
    }

    public ACTUAL containsWithName(final String name) {
        super
            .withFailMessage("Should have annotation with name: " + name)
            .anyMatch(annotation -> annotation.getNameAsString().equals(name));
        return myself();
    }

    public ACTUAL doesNotContainsWithName(final String name) {
        super
            .withFailMessage("Should have annotation with name: " + name)
            .noneMatch(annotation -> annotation.getNameAsString().equals(name));
        return myself();
    }

    public ACTUAL containsWithNameAndAttributes(final String name, final Map<String, String> attributes) {
        super
            .withFailMessage("Should have annotation with name: " + name + " and attributes: " + attributes + ", but was: " + actual)
            .anyMatch(annotation -> annotation.getNameAsString().equals(name) && hasAttributes(annotation, attributes));
        return myself();
    }

    private static boolean hasAttributes(final AnnotationExpr annotation, final Map<String, String> expectedAttributesToContains) {
        final Map<String, String> actualAttributes;
        if (annotation instanceof SingleMemberAnnotationExpr singleMemberAnnotationExpr) {
            actualAttributes = ImmutableMap.of(
                "value", singleMemberAnnotationExpr.getMemberValue().toString()
            );
        } else if (annotation instanceof NormalAnnotationExpr normalAnnotationExpr) {
            actualAttributes = normalAnnotationExpr.getPairs().stream()
                .collect(Collectors.toMap(NodeWithSimpleName::getNameAsString, pair -> pair.getValue().toString()));
        } else if (annotation instanceof MarkerAnnotationExpr) {
            actualAttributes = new HashMap<>();
        } else {
            throw new IllegalArgumentException("Unexpected annotation expression type for: " + annotation);
        }

        return expectedAttributesToContains.entrySet().stream()
            .allMatch(expected -> Objects.equals(actualAttributes.get(expected.getKey()), expected.getValue()));
    }

    @SuppressWarnings("unchecked")
    private ACTUAL myself() {
        return (ACTUAL) this;
    }
}
