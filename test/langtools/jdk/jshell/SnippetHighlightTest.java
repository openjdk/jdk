/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8274148 8301580 8359497 8374293
 * @summary Check snippet highlighting
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jdeps/com.sun.tools.javap
 *          jdk.jshell/jdk.jshell:open
 * @build toolbox.ToolBox toolbox.JarTask toolbox.JavacTask
 * @build KullaTesting TestingInputStream Compiler
 * @run junit SnippetHighlightTest
 */

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


import jdk.jshell.SourceCodeAnalysis.Highlight;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class SnippetHighlightTest extends KullaTesting {

    @Test
    public void testMemberExpr() {
        assertEval("@Deprecated class TestClass { }");
        assertEval("class TestConstructor { @Deprecated TestConstructor() {} }");
        assertEval("class TestMethod { @Deprecated void test() {} }");
        assertHighlights("TestClass t", "Highlight[start=0, end=9, attributes=[DEPRECATED]]",
                                        "Highlight[start=10, end=11, attributes=[DECLARATION]]");
        assertHighlights("TestClass.class", "Highlight[start=0, end=9, attributes=[DEPRECATED]]",
                                            "Highlight[start=10, end=15, attributes=[KEYWORD]]");
        assertHighlights("new TestConstructor()", "Highlight[start=0, end=3, attributes=[KEYWORD]]",
                                                  "Highlight[start=4, end=19, attributes=[DEPRECATED]]");
        assertHighlights("new TestMethod().test()", "Highlight[start=0, end=3, attributes=[KEYWORD]]",
                                                    "Highlight[start=17, end=21, attributes=[DEPRECATED]]");
        assertHighlights("var v = 0;", "Highlight[start=0, end=3, attributes=[KEYWORD]]",
                                       "Highlight[start=4, end=5, attributes=[DECLARATION]]");
        assertHighlights("int i = switch (0) { case 0: yield 0;};",
                         "Highlight[start=0, end=3, attributes=[KEYWORD]]",
                         "Highlight[start=4, end=5, attributes=[DECLARATION]]",
                         "Highlight[start=8, end=14, attributes=[KEYWORD]]",
                         "Highlight[start=21, end=25, attributes=[KEYWORD]]",
                         "Highlight[start=29, end=34, attributes=[KEYWORD]]");
        assertHighlights("sealed class C permits A {}",
                         "Highlight[start=0, end=6, attributes=[KEYWORD]]",
                         "Highlight[start=7, end=12, attributes=[KEYWORD]]",
                         "Highlight[start=13, end=14, attributes=[DECLARATION]]",
                         "Highlight[start=15, end=22, attributes=[KEYWORD]]");
        assertHighlights("non-sealed class C extends A {}",
                         "Highlight[start=0, end=10, attributes=[KEYWORD]]",
                         "Highlight[start=11, end=16, attributes=[KEYWORD]]",
                         "Highlight[start=17, end=18, attributes=[DECLARATION]]",
                         "Highlight[start=19, end=26, attributes=[KEYWORD]]");
        assertHighlights("interface I {}",
                         "Highlight[start=0, end=9, attributes=[KEYWORD]]",
                         "Highlight[start=10, end=11, attributes=[DECLARATION]]");
        assertHighlights("@interface I {}",
                         "Highlight[start=1, end=10, attributes=[KEYWORD]]",
                         "Highlight[start=11, end=12, attributes=[DECLARATION]]");
        assertHighlights("enum E {A, B;}",
                         "Highlight[start=0, end=4, attributes=[KEYWORD]]",
                         "Highlight[start=5, end=6, attributes=[DECLARATION]]",
                         "Highlight[start=8, end=9, attributes=[DECLARATION]]",
                         "Highlight[start=11, end=12, attributes=[DECLARATION]]");
        assertHighlights("record R(int i) {}",
                         "Highlight[start=0, end=6, attributes=[KEYWORD]]",
                         "Highlight[start=7, end=8, attributes=[DECLARATION]]",
                         "Highlight[start=9, end=12, attributes=[KEYWORD]]",
                         "Highlight[start=13, end=14, attributes=[DECLARATION]]");
        assertHighlights("void method() {}",
                         "Highlight[start=0, end=4, attributes=[KEYWORD]]",
                         "Highlight[start=5, end=11, attributes=[DECLARATION]]");
    }

    @Test
    public void testClassErrorRecovery() { //JDK-8301580
        assertHighlights("""
                         class C {
                            void m
                            {
                                return ;
                            }
                         }
                         """,
                         "Highlight[start=0, end=5, attributes=[KEYWORD]]",
                         "Highlight[start=6, end=7, attributes=[DECLARATION]]",
                         "Highlight[start=13, end=17, attributes=[KEYWORD]]",
                         "Highlight[start=32, end=38, attributes=[KEYWORD]]");
    }

    @Test
    public void testNoCrashOnLexicalErrors() { //JDK-8359497
        assertHighlights("""
                         "
                         """);
    }

    @Test // 8374293: The returned Highlights should not overlap
    public void testHighlightsOverlap() {
        assertHighlights("public void E test()", "Highlight[start=0, end=6, attributes=[KEYWORD]]",
                "Highlight[start=7, end=11, attributes=[KEYWORD]]",
                "Highlight[start=14, end=18, attributes=[DECLARATION]]");
    }

    private void assertHighlights(String code, String... expected) {
        List<String> completions = computeHighlights(code);
        assertEquals(Arrays.asList(expected), completions, "Input: " + code + ", " + completions.toString());
    }

    private List<String> computeHighlights(String code) {
        waitIndexingFinished();

        List<Highlight> highlights =
                getAnalysis().highlights(code);
        return highlights.stream()
                          .map(h -> h.toString())
                          .collect(Collectors.toList());
    }
}
