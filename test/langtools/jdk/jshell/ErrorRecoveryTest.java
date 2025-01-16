/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8270139 8273039 8286895 8332230
 * @summary Verify error recovery in JShell
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jdeps/com.sun.tools.javap
 *          jdk.jshell/jdk.internal.jshell.tool
 * @library /tools/lib
 * @build KullaTesting TestingInputStream ExpectedDiagnostic toolbox.ToolBox Compiler
 * @run testng ErrorRecoveryTest
 */

import org.testng.annotations.Test;
import static jdk.jshell.Snippet.Status.NONEXISTENT;
import static jdk.jshell.Snippet.Status.RECOVERABLE_NOT_DEFINED;
import static jdk.jshell.Snippet.Status.REJECTED;

@Test
public class ErrorRecoveryTest extends KullaTesting {

    public void testExceptionErrors() {
        assertEval("import java.lang.annotation.Repeatable;");
        assertEval("""
                   @Repeatable(FooContainer.class)
                   @interface Foo { int value(); }
                   """,
                   ste(MAIN_SNIPPET, NONEXISTENT, RECOVERABLE_NOT_DEFINED, false, null));
    }

    public void testBrokenName() {
        assertEval("int strictfp = 0;",
                   DiagCheck.DIAG_ERROR,
                   DiagCheck.DIAG_IGNORE,
                   ste(MAIN_SNIPPET, NONEXISTENT, REJECTED, false, null));
    }

    public void testBooleanPatternExpression() {
        assertEval("Number n = 0;");
        assertEval("if (!n instanceof Integer i) {}",
                   DiagCheck.DIAG_ERROR,
                   DiagCheck.DIAG_IGNORE,
                   ste(MAIN_SNIPPET, NONEXISTENT, REJECTED, false, null));
    }

    //JDK-8332230:
    public void testAnnotationsd() {
        assertEval("k=aa:a.@a",
                   DiagCheck.DIAG_ERROR,
                   DiagCheck.DIAG_IGNORE,
                   ste(MAIN_SNIPPET, NONEXISTENT, REJECTED, false, null));
    }
}
