/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test the ignoring of comments and certain modifiers
 * @build KullaTesting TestingInputStream
 * @run testng IgnoreTest
 */

import org.testng.annotations.Test;

import jdk.jshell.MethodSnippet;
import jdk.jshell.TypeDeclSnippet;
import jdk.jshell.VarSnippet;
import static jdk.jshell.Snippet.Status.VALID;
import static jdk.jshell.Snippet.SubKind.*;

@Test
public class IgnoreTest extends KullaTesting {

    public void testComment() {
        assertVarKeyMatch("//t1\n int//t2\n x//t3\n =//t4\n 12//t5\n ;//t6\n",
                true, "x", VAR_DECLARATION_WITH_INITIALIZER_SUBKIND, "int", added(VALID));
        assertVarKeyMatch("//t1\n int//t2\n y//t3\n =//t4\n 12//t5\n ;//t6",
                true, "y", VAR_DECLARATION_WITH_INITIALIZER_SUBKIND, "int", added(VALID));
        assertDeclarationKeyMatch("       //t0\n" +
                        "       int//t0\n" +
                        "       f//t0\n" +
                        "       (//t0\n" +
                        "       int x//t1\n" +
                        "       ) {//t2\n" +
                        "       return x+//t3\n" +
                        "       x//t4\n" +
                        "       ;//t5\n" +
                        "       }//t6",
                false, "f", METHOD_SUBKIND, added(VALID));
    }

    public void testVarModifier() {
        VarSnippet x1 = (VarSnippet) assertDeclareWarn1("public int x1;", "jdk.eval.warn.illegal.modifiers");
        assertVariableDeclSnippet(x1, "x1", "int", VALID, VAR_DECLARATION_SUBKIND, 0, 1);
        VarSnippet x2 = (VarSnippet) assertDeclareWarn1("protected int x2;", "jdk.eval.warn.illegal.modifiers");
        assertVariableDeclSnippet(x2, "x2", "int", VALID, VAR_DECLARATION_SUBKIND, 0, 1);
        VarSnippet x3 = (VarSnippet) assertDeclareWarn1("private int x3;", "jdk.eval.warn.illegal.modifiers");
        assertVariableDeclSnippet(x3, "x3", "int", VALID, VAR_DECLARATION_SUBKIND, 0, 1);
        VarSnippet x4 = (VarSnippet) assertDeclareWarn1("static int x4;", "jdk.eval.warn.illegal.modifiers");
        assertVariableDeclSnippet(x4, "x4", "int", VALID, VAR_DECLARATION_SUBKIND, 0, 1);
        VarSnippet x5 = (VarSnippet) assertDeclareWarn1("final int x5;", "jdk.eval.warn.illegal.modifiers");
        assertVariableDeclSnippet(x5, "x5", "int", VALID, VAR_DECLARATION_SUBKIND, 0, 1);
    }

    public void testMethodModifier() {
        MethodSnippet m1 = (MethodSnippet) assertDeclareWarn1("public void m1() {}", "jdk.eval.warn.illegal.modifiers");
        assertMethodDeclSnippet(m1, "m1", "()void", VALID, 0, 1);
        MethodSnippet m2 = (MethodSnippet) assertDeclareWarn1("protected void m2() {}", "jdk.eval.warn.illegal.modifiers");
        assertMethodDeclSnippet(m2, "m2", "()void", VALID, 0, 1);
        MethodSnippet m3 = (MethodSnippet) assertDeclareWarn1("private void m3() {}", "jdk.eval.warn.illegal.modifiers");
        assertMethodDeclSnippet(m3, "m3", "()void", VALID, 0, 1);
        MethodSnippet m4 = (MethodSnippet) assertDeclareWarn1("static void m4() {}", "jdk.eval.warn.illegal.modifiers");
        assertMethodDeclSnippet(m4, "m4", "()void", VALID, 0, 1);
        MethodSnippet m5 = (MethodSnippet) assertDeclareWarn1("final void m5() {}", "jdk.eval.warn.illegal.modifiers");
        assertMethodDeclSnippet(m5, "m5", "()void", VALID, 0, 1);
    }

    public void testClassModifier() {
        TypeDeclSnippet c1 = (TypeDeclSnippet) assertDeclareWarn1("public class C1 {}", "jdk.eval.warn.illegal.modifiers");
        assertTypeDeclSnippet(c1, "C1", VALID, CLASS_SUBKIND, 0, 1);
        TypeDeclSnippet c2 = (TypeDeclSnippet) assertDeclareWarn1("protected class C2 {}", "jdk.eval.warn.illegal.modifiers");
        assertTypeDeclSnippet(c2, "C2", VALID, CLASS_SUBKIND, 0, 1);
        TypeDeclSnippet c3 = (TypeDeclSnippet) assertDeclareWarn1("private class C3 {}", "jdk.eval.warn.illegal.modifiers");
        assertTypeDeclSnippet(c3, "C3", VALID, CLASS_SUBKIND, 0, 1);
        TypeDeclSnippet c4 = (TypeDeclSnippet) assertDeclareWarn1("static class C4 {}", "jdk.eval.warn.illegal.modifiers");
        assertTypeDeclSnippet(c4, "C4", VALID, CLASS_SUBKIND, 0, 1);
        TypeDeclSnippet c5 = (TypeDeclSnippet) assertDeclareWarn1("final class C5 {}", "jdk.eval.warn.illegal.modifiers");
        assertTypeDeclSnippet(c5, "C5", VALID, CLASS_SUBKIND, 0, 1);
    }

    public void testInsideModifier() {
        assertEval("import static java.lang.reflect.Modifier.*;");
        assertEval("class C {"
                + "public int z;"
                + "final int f = 3;"
                + "protected int a;"
                + "private void m() {}"
                + "static void b() {}"
                + "}");
        assertEval("C.class.getDeclaredField(\"z\").getModifiers() == PUBLIC;", "true");
        assertEval("C.class.getDeclaredField(\"f\").getModifiers() == FINAL;", "true");
        assertEval("C.class.getDeclaredField(\"a\").getModifiers() == PROTECTED;", "true");
        assertEval("C.class.getDeclaredMethod(\"m\").getModifiers() == PRIVATE;", "true");
        assertEval("C.class.getDeclaredMethod(\"b\").getModifiers() == STATIC;", "true");
    }
}
