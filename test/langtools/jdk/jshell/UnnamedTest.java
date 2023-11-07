/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8315851 8315588
 * @summary Tests for unnamed variables
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jshell
 * @build Compiler KullaTesting TestingInputStream ExpectedDiagnostic
 * @run testng UnnamedTest
 */

import java.util.function.Consumer;

import jdk.jshell.SourceCodeAnalysis;
import jdk.jshell.VarSnippet;
import org.testng.Assert;
import org.testng.annotations.Test;

import jdk.jshell.JShell;

import static jdk.jshell.SourceCodeAnalysis.Completeness.COMPLETE;
import static jdk.jshell.SourceCodeAnalysis.Completeness.DEFINITELY_INCOMPLETE;

public class UnnamedTest extends KullaTesting {

    @Test
    public void unnamed() {
        VarSnippet sn1 = varKey(assertEval("int _ = 0;"));
        VarSnippet sn2 = varKey(assertEval("String _ = \"x\";"));
        Assert.assertEquals(getState().varValue(sn1), "0");
        Assert.assertEquals(getState().varValue(sn2), "\"x\"");
    }

    static final String[] definitely_incomplete = new String[]{
            "int _ = ",
            "int m(String v, int r) {\n" +
                    "    try {\n" +
                    "        return Integer.parseInt(v, r);\n" +
                    "    } catch (NumberFormatException _) {",
            "try (final Lock _ = ",
            "try (Lock _ = null) {\n" +
                "            try (Lock _ = null) {",
            "for (var _ : strs",
            "TwoParams p1 = (_, _) ->",
            "for (int _ = 0, _ = 1, x = 1;",
            "if (r instanceof R(_"
    };

    static final String[] complete = new String[]{
            "int _ = 42;",
            "int m(String v, int r) {\n" +
                    "    try {\n" +
                    "        return Integer.parseInt(v, r);\n" +
                    "    } catch (NumberFormatException _) { } }",
            "try (final Lock _ = TEST) {}",
            "try (Lock _ = null) {\n" +
                    "            try (Lock _ = null) { } }",
            "for (var _ : strs) { }",
            "TwoParams p1 = (_, _) -> {};",
            "for (int _ = 0, _ = 1, x = 1; x <= 1 ; x++) {}",
            "if (r instanceof R(_)) { }"
    };

    private void assertStatus(String input, SourceCodeAnalysis.Completeness status, String source) {
        String augSrc;
        switch (status) {
            case COMPLETE_WITH_SEMI:
                augSrc = source + ";";
                break;

            case DEFINITELY_INCOMPLETE:
                augSrc = null;
                break;

            case CONSIDERED_INCOMPLETE:
                augSrc = source + ";";
                break;

            case EMPTY:
            case COMPLETE:
            case UNKNOWN:
                augSrc = source;
                break;

            default:
                throw new AssertionError();
        }
        assertAnalyze(input, status, augSrc);
    }

    private void assertStatus(String[] ins, SourceCodeAnalysis.Completeness status) {
        for (String input : ins) {
            assertStatus(input, status, input);
        }
    }

    @Test
    public void test_definitely_incomplete() {
        assertStatus(definitely_incomplete, DEFINITELY_INCOMPLETE);
    }

    @Test
    public void test_definitely_complete() {
        assertStatus(complete, COMPLETE);
    }

    @Override
    public void setUp(Consumer<JShell.Builder> bc) {
        super.setUp(bc.andThen(b -> b.compilerOptions("--enable-preview", "--source", System.getProperty("java.specification.version"))));
    }
}
