/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package flagsgenerator;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.ToolProvider;

public class FlagsGenerator {
    public static void main(String... args) throws IOException {
        var compiler = ToolProvider.getSystemJavaCompiler();

        try (var fm = compiler.getStandardFileManager(null, null, null)) {
            JavacTask task = (JavacTask) compiler.getTask(null, null, d -> {}, null, null, fm.getJavaFileObjects(args[0]));
            Trees trees = Trees.instance(task);
            CompilationUnitTree cut = task.parse().iterator().next();

            task.analyze();

            TypeElement clazz = (TypeElement) trees.getElement(new TreePath(new TreePath(cut), cut.getTypeDecls().get(0)));
            Map<Integer, List<String>> flag2Names = new TreeMap<>();
            Map<FlagTarget, Map<Integer, List<String>>> target2FlagBit2Fields = new EnumMap<>(FlagTarget.class);
            Map<String, String> customToString = new HashMap<>();
            Set<String> noToString = new HashSet<>();

            for (VariableElement field : ElementFilter.fieldsIn(clazz.getEnclosedElements())) {
                String flagName = field.getSimpleName().toString();
                for (AnnotationMirror am : field.getAnnotationMirrors()) {
                    switch (am.getAnnotationType().toString()) {
                        case "com.sun.tools.javac.code.Flags.Use" -> {
                            long flagValue = ((Number) field.getConstantValue()).longValue();
                            int flagBit = 63 - Long.numberOfLeadingZeros(flagValue);

                            flag2Names.computeIfAbsent(flagBit, _ -> new ArrayList<>())
                                      .add(flagName);

                            List<?> originalTargets = (List<?>) valueOfValueAttribute(am);
                            originalTargets.stream()
                                           .map(value -> FlagTarget.valueOf(value.toString()))
                                           .forEach(target -> target2FlagBit2Fields.computeIfAbsent(target, _ -> new HashMap<>())
                                                                                   .computeIfAbsent(flagBit, _ -> new ArrayList<>())
                                                                                   .add(flagName));
                        }
                        case "com.sun.tools.javac.code.Flags.CustomToStringValue" -> {
                            customToString.put(flagName, (String) valueOfValueAttribute(am));
                        }
                        case "com.sun.tools.javac.code.Flags.NoToStringValue" -> {
                            noToString.add(flagName);
                        }
                    }
                }
            }

            //verify there are no flag overlaps:
            for (Entry<FlagTarget, Map<Integer, List<String>>> targetAndFlag : target2FlagBit2Fields.entrySet()) {
                for (Entry<Integer, List<String>> flagAndFields : targetAndFlag.getValue().entrySet()) {
                    if (flagAndFields.getValue().size() > 1) {
                        throw new AssertionError("duplicate flag for target: " + targetAndFlag.getKey() +
                                                 ", flag: " + flagAndFields.getKey() +
                                                 ", flags fields: " + flagAndFields.getValue());
                    }
                }
            }

            try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Paths.get(args[1])))) {
                out.println("""
                            package com.sun.tools.javac.code;

                            public enum FlagsEnum {
                            """);
                for (Entry<Integer, List<String>> e : flag2Names.entrySet()) {
                    String constantName = e.getValue().stream().collect(Collectors.joining("_OR_"));
                    String toString = e.getValue()
                                       .stream()
                                       .filter(n -> !noToString.contains(n))
                                       .map(n -> customToString.getOrDefault(n, n.toLowerCase(Locale.US)))
                                       .collect(Collectors.joining(" or "));
                    out.println("    " + constantName + "(1L<<" + e.getKey() + ", \"" + toString + "\"),");
                }
                out.println("""
                                ;

                                private final long value;
                                private final String toString;
                                private FlagsEnum(long value, String toString) {
                                    this.value = value;
                                    this.toString = toString;
                                }
                                public long value() {
                                    return value;
                                }
                                public String toString() {
                                    return toString;
                                }
                            }
                            """);
            }
        }
    }

    private static Object valueOfValueAttribute(AnnotationMirror am) {
        return am.getElementValues()
                 .values()
                 .iterator()
                 .next()
                 .getValue();
    }

    private enum FlagTarget {
        BLOCK,
        CLASS,
        METHOD,
        MODULE,
        PACKAGE,
        TYPE_VAR,
        VARIABLE;
    }
}
