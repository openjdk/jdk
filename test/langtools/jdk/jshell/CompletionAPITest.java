/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8366691 8375015
 * @summary Test JShell Completion API
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jdeps/com.sun.tools.javap
 *          jdk.jshell/jdk.jshell:open
 * @build toolbox.ToolBox toolbox.JarTask toolbox.JavacTask
 * @build KullaTesting TestingInputStream Compiler
 * @run junit CompletionAPITest
 */

import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import jdk.jshell.SourceCodeAnalysis.CompletionContext;
import jdk.jshell.SourceCodeAnalysis.CompletionState;
import jdk.jshell.SourceCodeAnalysis.ElementSuggestion;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class CompletionAPITest extends KullaTesting {

    private static final long TIMEOUT = 20_000;

    @Test
    public void testAPI() {
        waitIndexingFinished();
        assertEval("String str = \"\";");
        List<String> actual;
        actual = completionSuggestions("str.", (state, suggestions) -> {
            assertEquals(EnumSet.of(CompletionContext.QUALIFIED), state.completionContext());
        });
        assertTrue(actual.contains("java.lang.String.length()"), String.valueOf(actual));
        actual = completionSuggestions("java.lang.", (state, suggestions) -> {
            assertEquals(EnumSet.of(CompletionContext.QUALIFIED), state.completionContext());
        });
        assertTrue(actual.contains("java.lang.String"), String.valueOf(actual));
        actual = completionSuggestions("java.", (state, suggestions) -> {
            assertEquals(EnumSet.of(CompletionContext.QUALIFIED), state.completionContext());
        });
        assertTrue(actual.contains("java.lang"), String.valueOf(actual));
        assertEval("@interface Ann2 { }");
        assertEval("@interface Ann1 { Ann2 value(); }");
        actual = completionSuggestions("@Ann", (state, suggestions) -> {
            assertEquals(EnumSet.of(CompletionContext.TYPES_AS_ANNOTATIONS), state.completionContext());
        });
        assertTrue(actual.containsAll(Set.of("Ann1", "Ann2")), String.valueOf(actual));
        actual = completionSuggestions("@Ann1(", (state, suggestions) -> {
            assertEquals(EnumSet.of(CompletionContext.ANNOTATION_ATTRIBUTE,
                                    CompletionContext.TYPES_AS_ANNOTATIONS),
                         state.completionContext());
        });
        assertTrue(actual.contains("Ann2"), String.valueOf(actual));
        actual = completionSuggestions("import static java.lang.String.", (state, suggestions) -> {
            assertEquals(EnumSet.of(CompletionContext.QUALIFIED, CompletionContext.NO_PAREN), state.completionContext());
        });
        assertTrue(actual.contains("java.lang.String.valueOf(int arg0)"), String.valueOf(actual));
        actual = completionSuggestions("java.util.function.IntFunction<String> f = String::", (state, suggestions) -> {
            assertEquals(EnumSet.of(CompletionContext.QUALIFIED, CompletionContext.NO_PAREN), state.completionContext());
        });
        assertTrue(actual.contains("java.lang.String.valueOf(int arg0)"), String.valueOf(actual));
        actual = completionSuggestions("str.^len", (state, suggestions) -> {
            assertEquals(EnumSet.of(CompletionContext.QUALIFIED), state.completionContext());
        });
        assertTrue(actual.contains("java.lang.String.length()"), String.valueOf(actual));
        actual = completionSuggestions("^@Depr", (state, suggestions) -> {
            assertEquals(EnumSet.of(CompletionContext.TYPES_AS_ANNOTATIONS), state.completionContext());
        });
        assertTrue(actual.contains("java.lang.Deprecated"), String.valueOf(actual));
        assertEval("import java.util.*;");
        actual = completionSuggestions("^ArrayL", (state, suggestions) -> {
            TypeElement arrayList =
                suggestions.stream()
                           .filter(el -> el.element() != null)
                           .map(el -> el.element())
                           .filter(el -> el.getKind() == ElementKind.CLASS)
                           .map(el -> (TypeElement) el)
                           .filter(el -> el.getQualifiedName().contentEquals("java.util.ArrayList"))
                           .findAny()
                           .orElseThrow();
            assertTrue(state.availableUsingSimpleName(arrayList));
            assertEquals(EnumSet.noneOf(CompletionContext.class), state.completionContext());
        });
        assertTrue(actual.contains("java.util.ArrayList"), String.valueOf(actual));
        completionSuggestions("(new java.util.ArrayList<String>()).", (state, suggestions) -> {
            List<String> elsWithTypes =
                suggestions.stream()
                           .filter(el -> el.element() != null)
                           .map(el -> el.element())
                           .filter(el -> el.getKind() == ElementKind.METHOD)
                           .map(el -> el.getSimpleName() + state.typeUtils()
                                                                .asMemberOf((DeclaredType) state.selectorType(), el)
                                                                .toString())
                           .toList();
            assertTrue(elsWithTypes.contains("add(java.lang.String)boolean"));
        });
    }

    @Test
    public void testDocumentation() throws Exception {
        waitIndexingFinished();

        Path classes = prepareZip();
        getState().addToClasspath(classes.toString());

        AtomicReference<Supplier<String>> documentation = new AtomicReference<>();
        AtomicReference<Reference<Element>> clazz = new AtomicReference<>();
        completionSuggestions("jshelltest.JShellTest", (state, suggestions) -> {
            ElementSuggestion test =
                    suggestions.stream()
                               .filter(el -> el.element() != null)
                               .filter(el -> el.element().getKind() == ElementKind.CLASS)
                               .filter(el -> ((TypeElement) el.element()).getQualifiedName().contentEquals("jshelltest.JShellTest"))
                               .findAny()
                               .orElseThrow();
            documentation.set(test.documentation());
            clazz.set(new WeakReference<>(test.element()));
        });

        //throw away the JavacTaskPool, so that the cached javac instances are dropped:
        getState().addToClasspath("undefined");

        long start = System.currentTimeMillis();

        while (clazz.get().get() != null && (System.currentTimeMillis() - start) < TIMEOUT) {
            System.gc();
            Thread.sleep(100);
        }

        assertNull(clazz.get().get());
        assertEquals("JShellTest 0 ", documentation.get().get());
    }

    @Test
    public void testSignature() {
        waitIndexingFinished();

        assertEval("void test(int i) {}");
        assertEval("void test(int i, int j) {}");
        assertSignature("test(|", true, "void test(int i):0", "void test(int i, int j):0");
        assertSignature("test(0, |", true, "void test(int i, int j):1");
    }

    private List<String> completionSuggestions(String input,
                                               BiConsumer<CompletionState, List<? extends ElementSuggestion>> validator) {
        int expectedAnchor = input.indexOf('^');

        if (expectedAnchor != (-1)) {
            input = input.substring(0, expectedAnchor) + input.substring(expectedAnchor + 1);
        }

        AtomicInteger mergedAnchor = new AtomicInteger(-1);

        List<String> result = getAnalysis().completionSuggestions(input, input.length(), (state, suggestions) -> {
            validator.accept(state, suggestions);

            if (expectedAnchor != (-1)) {
                for (ElementSuggestion sugg : suggestions) {
                    if (mergedAnchor.get() == (-1)) {
                        mergedAnchor.set(sugg.anchor());
                    } else {
                        assertEquals(mergedAnchor.get(), sugg.anchor());
                    }
                }
            }

            return suggestions.stream()
                           .map(this::convertElement)
                           .toList();
        });

        if (expectedAnchor != (-1)) {
            assertEquals(expectedAnchor, mergedAnchor.get());
        }

        return result;
    }

    private String convertElement(ElementSuggestion suggestion) {
        if (suggestion.keyword() != null) {
            return suggestion.keyword();
        }

        Element el = suggestion.element();

        if (el.getKind().isClass() || el.getKind().isInterface() || el.getKind() == ElementKind.PACKAGE) {
            String qualifiedName = ((QualifiedNameable) el).getQualifiedName().toString();
            if (qualifiedName.startsWith("REPL.$JShell$")) {
                String[] parts = qualifiedName.split("\\.", 3);

                return parts[2];
            } else {
                return qualifiedName;
            }
        } else if (el.getKind().isField()) {
            return ((QualifiedNameable) el.getEnclosingElement()).getQualifiedName().toString() +
                   "." +
                   el.getSimpleName();
        } else if (el.getKind() == ElementKind.CONSTRUCTOR || el.getKind() == ElementKind.METHOD) {
            String name = el.getKind() == ElementKind.CONSTRUCTOR ? "" : "." + el.getSimpleName();
            ExecutableElement method = (ExecutableElement) el;

            return ((QualifiedNameable) el.getEnclosingElement()).getQualifiedName().toString() +
                    name +
                    method.getParameters()
                          .stream()
                          .map(var -> var.asType().toString() + " " + var.getSimpleName())
                          .collect(Collectors.joining(", ", "(", ")"));
        } else {
            return el.getSimpleName().toString();
        }
    }

    private Path prepareZip() {
        String clazz =
                "package jshelltest;\n" +
                "/**JShellTest 0" +
                " */\n" +
                "public class JShellTest {\n" +
                "}\n";

        Path srcZip = Paths.get("src.zip");

        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(srcZip))) {
            out.putNextEntry(new JarEntry("jshelltest/JShellTest.java"));
            out.write(clazz.getBytes());
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }

        compiler.compile(clazz);

        try {
            Field availableSources = Class.forName("jdk.jshell.SourceCodeAnalysisImpl").getDeclaredField("availableSourcesOverride");
            availableSources.setAccessible(true);
            availableSources.set(null, Arrays.asList(srcZip));
        } catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException | ClassNotFoundException ex) {
            throw new IllegalStateException(ex);
        }

        return compiler.getClassDir();
    }
    //where:
        private final Compiler compiler = new Compiler();

    static {
        try {
            //disable reading of paramater names, to improve stability:
            Class<?> analysisClass = Class.forName("jdk.jshell.SourceCodeAnalysisImpl");
            Field params = analysisClass.getDeclaredField("COMPLETION_EXTRA_PARAMETERS");
            params.setAccessible(true);
            params.set(null, List.of());
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
