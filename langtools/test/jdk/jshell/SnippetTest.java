/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @summary test accessors of Snippet
 * @build KullaTesting TestingInputStream
 * @run testng SnippetTest
 */

import jdk.jshell.Snippet;
import jdk.jshell.DeclarationSnippet;
import org.testng.annotations.Test;

import static jdk.jshell.Snippet.Status.VALID;
import static jdk.jshell.Snippet.Status.RECOVERABLE_DEFINED;
import static jdk.jshell.Snippet.Status.OVERWRITTEN;
import static jdk.jshell.Snippet.SubKind.*;

@Test
public class SnippetTest extends KullaTesting {

    public void testImportKey() {
        assertImportKeyMatch("import java.util.List;", "List", SINGLE_TYPE_IMPORT_SUBKIND, added(VALID));
        assertImportKeyMatch("import java.util.*;", "java.util.*", TYPE_IMPORT_ON_DEMAND_SUBKIND, added(VALID));
        assertImportKeyMatch("import static java.lang.String.*;", "java.lang.String.*", STATIC_IMPORT_ON_DEMAND_SUBKIND, added(VALID));
    }

    public void testClassKey() {
        assertDeclarationKeyMatch("class X {}", false, "X", CLASS_SUBKIND, added(VALID));
    }

    public void testInterfaceKey() {
        assertDeclarationKeyMatch("interface I {}", false, "I", INTERFACE_SUBKIND, added(VALID));
    }

    public void testEnumKey() {
        assertDeclarationKeyMatch("enum E {}", false, "E", ENUM_SUBKIND, added(VALID));
    }

    public void testAnnotationKey() {
        assertDeclarationKeyMatch("@interface A {}", false, "A", ANNOTATION_TYPE_SUBKIND, added(VALID));
    }

    public void testMethodKey() {
        assertDeclarationKeyMatch("void m() {}", false, "m", METHOD_SUBKIND, added(VALID));
    }

    public void testVarDeclarationKey() {
        assertVarKeyMatch("int a;", false, "a", VAR_DECLARATION_SUBKIND, "int", added(VALID));
    }

    public void testVarDeclarationWithInitializerKey() {
        assertVarKeyMatch("double b = 9.0;", true, "b", VAR_DECLARATION_WITH_INITIALIZER_SUBKIND, "double", added(VALID));
    }

    public void testTempVarExpressionKey() {
        assertVarKeyMatch("47;", true, "$1", TEMP_VAR_EXPRESSION_SUBKIND, "int", added(VALID));
    }

    public void testVarValueKey() {
        assertEval("double x = 4;", "4.0");
        assertExpressionKeyMatch("x;", "x", VAR_VALUE_SUBKIND, "double");
    }

    public void testAssignmentKey() {
        assertEval("int y;");
        assertExpressionKeyMatch("y = 4;", "y", ASSIGNMENT_SUBKIND, "int");
    }

    public void testStatementKey() {
        assertKeyMatch("if (true) {}", true, STATEMENT_SUBKIND, added(VALID));
        assertKeyMatch("while (true) { break; }", true, STATEMENT_SUBKIND, added(VALID));
        assertKeyMatch("do { } while (false);", true, STATEMENT_SUBKIND, added(VALID));
        assertKeyMatch("for (;;) { break; }", true, STATEMENT_SUBKIND, added(VALID));
    }

    public void noKeys() {
        assertActiveKeys(new DeclarationSnippet[0]);
    }

    public void testKeyId1() {
        Snippet a = classKey(assertEval("class A { }"));
        assertEval("void f() {  }");
        assertEval("int f;");
        assertEval("interface A { }",
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(a, VALID, OVERWRITTEN, false, MAIN_SNIPPET));
        assertKeys(method("()void", "f"), variable("int", "f"), clazz(KullaTesting.ClassType.INTERFACE, "A"));
        assertActiveKeys();
    }

    @Test(enabled = false) // TODO 8081689
    public void testKeyId2() {
        Snippet g = methodKey(assertEval("void g() { f(); }", added(RECOVERABLE_DEFINED)));
        Snippet f = methodKey(assertEval("void f() { }",
                added(VALID),
                ste(g, RECOVERABLE_DEFINED, VALID, false, null)));
        assertEval("int f;");
        assertEval("interface A { }");
        assertKeys(method("()void", "g"), method("()void", "f"), variable("int", "f"),
                clazz(KullaTesting.ClassType.INTERFACE, "A"));
        assertActiveKeys();
        assertEval("double f() { return 0.0; }",
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(f, VALID, OVERWRITTEN, false, MAIN_SNIPPET),
                ste(g, VALID, VALID, false, MAIN_SNIPPET));
        assertKeys(method("()void", "g"), method("()double", "f"), variable("int", "f"),
                clazz(KullaTesting.ClassType.INTERFACE, "A"));
        assertActiveKeys();
    }

    public void testKeyId3() {
        Snippet g = methodKey(assertEval("void g() { f(); }", added(RECOVERABLE_DEFINED)));
        Snippet f = methodKey(assertEval("void f() { }",
                added(VALID),
                ste(g, RECOVERABLE_DEFINED, VALID, false, null)));
        assertDeclareFail("qqqq;", "compiler.err.cant.resolve.location");
        assertEval("interface A { }");
        assertKeys(method("()void", "g"), method("()void", "f"),
                clazz(KullaTesting.ClassType.INTERFACE, "A"));
        assertActiveKeys();
        assertEval("double f() { return 0.0; }",
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(f, VALID, OVERWRITTEN, false, MAIN_SNIPPET),
                ste(g, VALID, VALID, false, MAIN_SNIPPET));
        assertKeys(method("()void", "g"), clazz(KullaTesting.ClassType.INTERFACE, "A"),
                method("()double", "f"));
        assertActiveKeys();
    }
}
