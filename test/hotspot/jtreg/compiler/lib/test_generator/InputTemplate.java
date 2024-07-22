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
public abstract class InputTemplate {
    private static final Random RAND = new Random();
    public static Integer[] integerValues = {
            -2, -1, 0, 1, 2,
            Integer.MIN_VALUE - 2, Integer.MIN_VALUE - 1, Integer.MIN_VALUE, Integer.MIN_VALUE + 1, Integer.MIN_VALUE + 2,
            Integer.MAX_VALUE - 2, Integer.MAX_VALUE - 1, Integer.MAX_VALUE, Integer.MAX_VALUE + 1, Integer.MAX_VALUE + 2
    };
    public static Integer[] positiveIntegerValues = {
            1, 2,
            Integer.MAX_VALUE - 2, Integer.MAX_VALUE - 1, Integer.MAX_VALUE, Integer.MAX_VALUE + 1, Integer.MAX_VALUE + 2
    };


    public static Integer[] integerValuesNonZero = {
            -2, -1, 1, 2,
            Integer.MIN_VALUE - 2, Integer.MIN_VALUE - 1, Integer.MIN_VALUE, Integer.MIN_VALUE + 1, Integer.MIN_VALUE + 2,
            Integer.MAX_VALUE - 2, Integer.MAX_VALUE - 1, Integer.MAX_VALUE, Integer.MAX_VALUE + 1, Integer.MAX_VALUE + 2
    };
    public static Long[] longValues = {
            -2L, -1L, 0L, 1L, 2L,
            Integer.MIN_VALUE - 2L, Integer.MIN_VALUE - 1L, (long) Integer.MIN_VALUE, Integer.MIN_VALUE + 1L, Integer.MIN_VALUE + 2L,
            Integer.MAX_VALUE - 2L, Integer.MAX_VALUE - 1L, (long) Integer.MAX_VALUE, Integer.MAX_VALUE + 1L, Integer.MAX_VALUE + 2L,
            Long.MIN_VALUE - 2L, Long.MIN_VALUE - 1L, Long.MIN_VALUE, Long.MIN_VALUE + 1L, Long.MIN_VALUE + 2L,
            Long.MAX_VALUE - 2L, Long.MAX_VALUE - 1L, Long.MAX_VALUE, Long.MAX_VALUE + 1L, Long.MAX_VALUE + 2L
    };
    public static Long[] longValuesNonZero = {
            -2L, -1L, 1L, 2L,
            Integer.MIN_VALUE - 2L, Integer.MIN_VALUE - 1L, (long) Integer.MIN_VALUE, Integer.MIN_VALUE + 1L, Integer.MIN_VALUE + 2L,
            Integer.MAX_VALUE - 2L, Integer.MAX_VALUE - 1L, (long) Integer.MAX_VALUE, Integer.MAX_VALUE + 1L, Integer.MAX_VALUE + 2L,
            Long.MIN_VALUE - 2L, Long.MIN_VALUE - 1L, Long.MIN_VALUE, Long.MIN_VALUE + 1L, Long.MIN_VALUE + 2L,
            Long.MAX_VALUE - 2L, Long.MAX_VALUE - 1L, Long.MAX_VALUE, Long.MAX_VALUE + 1L, Long.MAX_VALUE + 2L
    };
    public static Short[] shortValuesNonZero = {
            -2, -1,0, 1, 2,
            (short) (Integer.MIN_VALUE & 0xFFFF), (short) ((Integer.MIN_VALUE + 1) & 0xFFFF), (short) Integer.MIN_VALUE, (short) (Integer.MIN_VALUE + 1), (short) (Integer.MIN_VALUE + 2),
            (short) (Integer.MAX_VALUE - 2), (short) (Integer.MAX_VALUE - 1), (short) Integer.MAX_VALUE, (short) (Integer.MAX_VALUE + 1), (short) (Integer.MAX_VALUE + 2),
            (short) -32770, (short) -32769, (short) -32768, (short) -32767, (short) -32766,
            (short) 32765, (short) 32766, (short) 32767, (short) 32768, (short) 32769
    };
    public static Short[] shortValues = {
            -2, -1, 1, 2,
            (short) (Integer.MIN_VALUE & 0xFFFF), (short) ((Integer.MIN_VALUE + 1) & 0xFFFF), (short) Integer.MIN_VALUE, (short) (Integer.MIN_VALUE + 1), (short) (Integer.MIN_VALUE + 2),
            (short) (Integer.MAX_VALUE - 2), (short) (Integer.MAX_VALUE - 1), (short) Integer.MAX_VALUE, (short) (Integer.MAX_VALUE + 1), (short) (Integer.MAX_VALUE + 2),
            (short) -32770, (short) -32769, (short) -32768, (short) -32767, (short) -32766,
            (short) 32765, (short) 32766, (short) 32767, (short) 32768, (short) 32769
    };
    public InputTemplate() {
    }
    public static <T> T getRandomValue(T[] array) {
        return array[RAND.nextInt(array.length)];
    }
    public static <T> String getRandomValueAsString(T[] array) {
        T value = getRandomValue(array);
        return String.valueOf(value);  // Convert any type T to String
    }
    public static String getUniqueId() {
        return String.valueOf(System.nanoTime());
    }
    public static Integer[] getIntegerValues(int size) {

        HashSet<Integer> integers = new HashSet<>(size);
        while (integers.size() < size) {
            int number = RAND.nextInt(Integer.MAX_VALUE - 1) + 1;
            integers.add(number);
        }
        return integers.toArray(new Integer[0]);
    }
    public static String getJavaCode(CodeSegment inputTemplate,  ArrayList<Map<String, String>> inputReplacements, long num) {
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
        CodeSegment CodeSegment = fillTemplate(inputTemplate, inputReplacements.getFirst());
        for (int i =1; i<inputReplacements.size(); i++) {
            CodeSegment codS = fillTemplate(inputTemplate, inputReplacements.get(i));
            CodeSegment.appendCall(codS.getCalls());
            CodeSegment.appendMethods(codS.getMethods());
        }
        Map<String, String> replacements = Map.ofEntries(
                Map.entry("num", String.valueOf(num)),
                Map.entry("statics", CodeSegment.getStatics()),
                Map.entry("calls", CodeSegment.getCalls()),
                Map.entry("methods", CodeSegment.getMethods()),
                Map.entry("imports", CodeSegment.getImports())
        );
        return doReplacements(template, replacements);
    }
    private static CodeSegment fillTemplate(CodeSegment CodeSegment, Map<String, String> replacements) {
        String statics = doReplacements(CodeSegment.getStatics(), replacements);
        String calls = doReplacements(CodeSegment.getCalls(), replacements);
        String methods = doReplacements(CodeSegment.getMethods(), replacements);
        String imports = doReplacements(CodeSegment.getImports(), replacements);
        return new CodeSegment(statics, calls, methods,imports);
    }
    public static String doReplacements(String template, Map<String, String> replacements) {
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
    public abstract CodeSegment getTemplate();
    public abstract Map<String, String> getRandomReplacements(int numTest);
    public abstract String[] getCompileFlags();
    public abstract int getNumberOfTests();
    public  int getNumberOfTestMethods() {
        return 0;
    }
}
