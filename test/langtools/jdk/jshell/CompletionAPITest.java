/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8366691
 * @summary Test JShell Completion API
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jdeps/com.sun.tools.javap
 *          jdk.jshell/jdk.jshell:open
 * @build toolbox.ToolBox toolbox.JarTask toolbox.JavacTask
 * @build KullaTesting TestingInputStream Compiler
 * @run testng CompletionAPITest
 */

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.QualifiedNameable;
import jdk.jshell.SourceCodeAnalysis.CompletionContext;
import jdk.jshell.SourceCodeAnalysis.CompletionState;
import jdk.jshell.SourceCodeAnalysis.ElementSuggestion;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class CompletionAPITest extends KullaTesting {

    //TODO: should we pull types from the index automagically?
    @Test
    public void testAPI() {
        waitIndexingFinished();
        assertEval("String str = \"\";");
        List<String> actual;
        actual = completionSuggestions("str.", state -> {
            assertEquals(state.completionContext(), EnumSet.of(CompletionContext.QUALIFIED));
        });
        assertTrue(actual.contains("java.lang.String.length()"), String.valueOf(actual));
        actual = completionSuggestions("java.lang.", state -> {
            assertEquals(state.completionContext(), EnumSet.of(CompletionContext.QUALIFIED));
        });
        assertTrue(actual.contains("java.lang.String"), String.valueOf(actual));
        actual = completionSuggestions("java.", state -> {
            assertEquals(state.completionContext(), EnumSet.of(CompletionContext.QUALIFIED));
        });
        assertTrue(actual.contains("java.lang"), String.valueOf(actual));
        assertEval("@interface Ann2 { }");
        assertEval("@interface Ann1 { Ann2 value(); }");
        actual = completionSuggestions("@Ann", state -> {
            assertEquals(state.completionContext(), EnumSet.of(CompletionContext.TYPES_AS_ANNOTATIONS));
        });
        assertTrue(actual.containsAll(Set.of("Ann1", "Ann2")), String.valueOf(actual));
        actual = completionSuggestions("@Ann1(", state -> {
            assertEquals(state.completionContext(), EnumSet.of(CompletionContext.ANNOTATION_ATTRIBUTE,
                                                               CompletionContext.TYPES_AS_ANNOTATIONS));
        });
        assertTrue(actual.contains("Ann2"), String.valueOf(actual));
        actual = completionSuggestions("import static java.lang.String.", state -> {
            assertEquals(state.completionContext(), EnumSet.of(CompletionContext.QUALIFIED, CompletionContext.NO_PAREN));
        });
        assertTrue(actual.contains("java.lang.String.valueOf(int arg0)"), String.valueOf(actual));
        actual = completionSuggestions("java.util.function.IntFunction<String> f = String::", state -> {
            assertEquals(state.completionContext(), EnumSet.of(CompletionContext.QUALIFIED, CompletionContext.NO_PAREN));
        });
        assertTrue(actual.contains("java.lang.String.valueOf(int arg0)"), String.valueOf(actual));
    }

    private List<String> completionSuggestions(String input, Consumer<CompletionState> stateValidator) {
        return getAnalysis().completionSuggestions(input, input.length(), (state, suggestions) -> {
            stateValidator.accept(state);
            return suggestions.stream()
                           .map(this::convertElement)
                           .toList();
        });
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
