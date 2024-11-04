/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package compiler.lib.test_generator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Abstract class representing an input template for test generation.
 */
public abstract class InputTemplate {
    private static final Random RANDOM = new Random(32);

    // Integer test values including edge cases
    public static final Integer[] INTEGER_VALUES = {
            -2, -1, 0, 1, 2,
            Integer.MIN_VALUE, Integer.MAX_VALUE
    };

    // Positive integer test values including edge cases
    public static final Integer[] POSITIVE_INTEGER_VALUES = {
            1, 2, Integer.MAX_VALUE
    };

    // Array sizes for testing
    public static final Integer[] ARRAY_SIZES = {
            1, 10, 100, 1_000, 10_000, 100_000, 200_000, 500_000, 1_000_000
    };

    // Non-zero integer values
    public static final Integer[] INTEGER_VALUES_NON_ZERO = {
            -2, -1, 1, 2,
            Integer.MIN_VALUE, Integer.MAX_VALUE
    };

    // Long test values including edge cases
    public static final Long[] LONG_VALUES = {
            -2L, -1L, 0L, 1L, 2L,
            (long) Integer.MIN_VALUE, (long) Integer.MAX_VALUE,
            Long.MIN_VALUE, Long.MAX_VALUE
    };

    // Non-zero long values
    public static final Long[] LONG_VALUES_NON_ZERO = {
            -2L, -1L, 1L, 2L,
            (long) Integer.MIN_VALUE, (long) Integer.MAX_VALUE,
            Long.MIN_VALUE, Long.MAX_VALUE
    };

    // Short test values including edge cases
    public static final Short[] SHORT_VALUES = {
            -2, -1, 0, 1, 2,
            Short.MIN_VALUE, Short.MAX_VALUE
    };

    // Non-zero short values
    public static final Short[] SHORT_VALUES_NON_ZERO = {
            -2, -1, 1, 2,
            Short.MIN_VALUE, Short.MAX_VALUE
    };

    /**
     * Default constructor.
     */
    public InputTemplate() {
        // Initialization if needed
    }

    /**
     * Retrieves a random value from the provided array.
     *
     * @param array Array of values to choose from.
     * @param <T>   Type of the array elements.
     * @return A randomly selected element from the array.
     */
    public static <T> T getRandomValue(T[] array) {
        if (array == null || array.length == 0) {
            throw new IllegalArgumentException("Array must not be null or empty.");
        }
        return array[RANDOM.nextInt(array.length)];
    }

    /**
     * Retrieves a random value from the provided array and converts it to a string.
     *
     * @param array Array of values to choose from.
     * @param <T>   Type of the array elements.
     * @return A string representation of a randomly selected element from the array.
     */
    public static <T> String getRandomValueAsString(T[] array) {
        T value = getRandomValue(array);
        return String.valueOf(value);
    }

    /**
     * Generates a unique identifier based on the current system time in nanoseconds.
     *
     * @return A unique identifier as a string.
     */
    public static String getUniqueId() {
        return String.valueOf(System.nanoTime());
    }

    /**
     * Generates an array of unique positive integers of the specified size.
     *
     * @param size The number of unique integers to generate.
     * @return An array of unique positive integers.
     */
    public static Integer[] getUniquePositiveIntegers(int size) {
        if (size <= 0 || size > (Integer.MAX_VALUE - 1)) {
            throw new IllegalArgumentException("Size must be between 1 and " + (Integer.MAX_VALUE - 1));
        }

        HashSet<Integer> integers = new HashSet<>(size);
        while (integers.size() < size) {
            int number = RANDOM.nextInt(Integer.MAX_VALUE - 1) + 1; // 1 to Integer.MAX_VALUE - 1
            integers.add(number);
        }
        return integers.toArray(new Integer[0]);
    }

    /**
     * Generates Java code based on the provided template and replacements.
     *
     * @param inputTemplate      The code segment template.
     * @param inputReplacements  A list of maps containing replacements for each test.
     * @param testNumber         The unique number for the generated test class.
     * @return A string containing the generated Java code.
     */
    public static String generateJavaCode(CodeSegment inputTemplate,
                                          ArrayList<Map<String, String>> inputReplacements,
                                          long testNumber) {
        String template = """
                import java.util.Objects;
                \\{imports}
                public class GeneratedTest\\{num} {
                    \\{statics}
                    public static void main(String args[]) throws Exception {
                        \\{calls}
                        System.out.println("Passed");
                    }
                    \\{methods}
                }
                """;

        if (inputReplacements == null || inputReplacements.isEmpty()) {
            throw new IllegalArgumentException("Input replacements must not be null or empty.");
        }

        CodeSegment aggregatedCodeSegment = fillTemplate(inputTemplate, inputReplacements.get(0));

        for (int i = 1; i < inputReplacements.size(); i++) {
            CodeSegment currentSegment = fillTemplate(inputTemplate, inputReplacements.get(i));
            aggregatedCodeSegment.appendCalls(currentSegment.getCalls());
            aggregatedCodeSegment.appendMethods(currentSegment.getMethods());
            // Assuming imports are common and handled separately
        }

        Map<String, String> replacements = Map.of(
                "num", String.valueOf(testNumber),
                "statics", aggregatedCodeSegment.getStatics(),
                "calls", aggregatedCodeSegment.getCalls(),
                "methods", aggregatedCodeSegment.getMethods(),
                "imports", aggregatedCodeSegment.getImports()
        );

        return performReplacements(template, replacements);
    }

    /**
     * Fills the template with the provided replacements.
     *
     * @param codeSegment  The original code segment template.
     * @param replacements A map containing replacement values.
     * @return A new CodeSegment with replacements applied.
     */
    private static CodeSegment fillTemplate(CodeSegment codeSegment, Map<String, String> replacements) {
        String statics = performReplacements(codeSegment.getStatics(), replacements);
        String calls = performReplacements(codeSegment.getCalls(), replacements);
        String methods = performReplacements(codeSegment.getMethods(), replacements);
        String imports = performReplacements(codeSegment.getImports(), replacements);
        return new CodeSegment(statics, calls, methods, imports);
    }

    /**
     * Performs placeholder replacements in the template string based on the provided map.
     * It preserves the indentation of the placeholders.
     *
     * @param template     The template string containing placeholders.
     * @param replacements A map of placeholder keys and their replacement values.
     * @return The template string with all placeholders replaced.
     */
    public static String performReplacements(String template, Map<String, String> replacements) {
        if (template == null || replacements == null) {
            throw new IllegalArgumentException("Template and replacements must not be null.");
        }

        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            String pattern = "\\{%s}".formatted(entry.getKey());
            String replacement = entry.getValue();

            // Find the pattern in the template and determine its indentation level
            int index = template.indexOf(pattern);
            if (index != -1) {
                int lineStart = template.lastIndexOf('\n', index);
                String indentation = template.substring(lineStart + 1, index).replaceAll("\\S", "");

                // Add indentation to all lines of the replacement (except the first one)
                String indentedReplacement = replacement.lines()
                        .collect(Collectors.joining("\n%s".formatted(indentation)));
                template = template.replace(pattern, indentedReplacement);
            }
        }
        return template;
    }

    /**
     * Abstract method to retrieve the code segment template.
     *
     * @return The code segment template.
     */
    public abstract CodeSegment getTemplate();

    /**
     * Abstract method to retrieve random replacements for the tests.
     *
     * @param numTests The number of tests to generate replacements for.
     * @return A map containing replacement values for each test.
     */
    public abstract Map<String, String> getRandomReplacements(int numTests);

    /**
     * Abstract method to retrieve compilation flags.
     *
     * @return An array of compile flags.
     */
    public abstract String[] getCompileFlags();

    /**
     * Abstract method to retrieve the number of tests.
     *
     * @return The number of tests.
     */
    public abstract int getNumberOfTests();

    /**
     * Abstract method to retrieve the number of test methods.
     *
     * @return The number of test methods.
     */
    public abstract int getNumberOfTestMethods();
}
