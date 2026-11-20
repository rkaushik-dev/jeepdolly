package io.github.rkaushikdev.jeepdolly.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

/**
 * Checks for the type rules the original enforced at run time and Java enforces at compile time.
 *
 * <p>The original refused {@code Inches(14.5)} and {@code Inches(96) + 6} with a type error when
 * the line ran. In Java neither line compiles, so the test that pinned the refusal pins the
 * signature instead: the only way in takes the whole number type, and the arithmetic takes only
 * its own kind. Each check fails loudly if it finds nothing to check.
 */
public final class Signatures {

    private Signatures() {}

    /** Every public constructor of {@code type} takes exactly {@code parameters}, in that order. */
    public static void constructorsTakeOnly(Class<?> type, Class<?>... parameters) {
        List<Constructor<?>> found = Arrays.stream(type.getConstructors()).toList();
        assertThat(found).as("public constructors of %s", type.getSimpleName()).isNotEmpty();
        for (Constructor<?> constructor : found) {
            assertThat(constructor.getParameterTypes())
                    .as("a constructor of %s", type.getSimpleName())
                    .containsExactly(parameters);
        }
    }

    /** Every public instance method {@code name} of {@code type} takes exactly {@code parameters}. */
    public static void methodTakesOnly(Class<?> type, String name, Class<?>... parameters) {
        List<Method> found = Arrays.stream(type.getMethods())
                .filter(method -> method.getName().equals(name))
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .toList();
        assertThat(found).as("methods %s.%s", type.getSimpleName(), name).isNotEmpty();
        for (Method method : found) {
            assertThat(method.getParameterTypes())
                    .as("%s.%s", type.getSimpleName(), name)
                    .containsExactly(parameters);
        }
    }
}
