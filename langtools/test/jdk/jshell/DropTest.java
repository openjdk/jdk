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
 * @bug 8081431
 * @summary Test of JShell#drop().
 * @build KullaTesting TestingInputStream
 * @run testng DropTest
 */

import jdk.jshell.DeclarationSnippet;
import jdk.jshell.PersistentSnippet;
import org.testng.annotations.Test;

import static jdk.jshell.Snippet.Status.*;

@Test
public class DropTest extends KullaTesting {

    public void testDrop() {
        PersistentSnippet var = varKey(assertEval("int x;"));
        PersistentSnippet method = methodKey(assertEval("int mu() { return x * 4; }"));
        PersistentSnippet clazz = classKey(assertEval("class C { String v() { return \"#\" + mu(); } }"));
        assertDrop(var,
                ste(var, VALID, DROPPED, true, null),
                ste(method, VALID, RECOVERABLE_DEFINED, false, var));
        //assertDrop(method,
        //        ste(method, RECOVERABLE_DEFINED, DROPPED, false, null),
        //        ste(clazz, RECOVERABLE_NOT_DEFINED, RECOVERABLE_NOT_DEFINED, false, method));
        //assertDeclareFail("new C();", "compiler.err.cant.resolve.location");
        assertVariables();
        assertMethods();
        assertClasses();
        assertActiveKeys();

        assertEval("int x = 10;", "10",
                ste(var, DROPPED, VALID, true, null),
                ste(method, RECOVERABLE_DEFINED, VALID, false, MAIN_SNIPPET));
        PersistentSnippet c0 = varKey(assertEval("C c0 = new C();"));
        assertEval("c0.v();", "\"#40\"");
        assertEval("C c = new C();");
        assertEval("c.v();", "\"#40\"");
        assertEval("int mu() { return x * 3; }",
                ste(MAIN_SNIPPET, VALID, VALID, false, null),
                ste(method, VALID, OVERWRITTEN, false, MAIN_SNIPPET));
        assertEval("c.v();", "\"#30\"");
        assertEval("class C { String v() { return \"@\" + mu(); } }",
                ste(MAIN_SNIPPET, VALID, VALID, false, null),
                ste(clazz, VALID, OVERWRITTEN, false, MAIN_SNIPPET));
        assertEval("c0.v();", "\"@30\"");
        assertDrop(c0,
                ste(c0, VALID, DROPPED, true, null));
        assertEval("c = new C();");
        assertEval("c.v();", "\"@30\"");

        assertVariables();
        assertMethods();
        assertClasses();
        assertActiveKeys();
    }

    public void testDropImport() {
        PersistentSnippet imp = importKey(assertEval("import java.util.*;"));
        PersistentSnippet decl = varKey(
                assertEval("List<Integer> list = Arrays.asList(1, 2, 3);", "[1, 2, 3]"));
        assertEval("list;", "[1, 2, 3]");
        assertDrop(imp,
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                ste(imp, VALID, DROPPED, true, null),
                ste(decl, VALID, RECOVERABLE_NOT_DEFINED, true, imp));
        assertDeclareFail("list;", "compiler.err.cant.resolve.location");
    }

    public void testDropVarToMethod() {
        PersistentSnippet x = varKey(assertEval("int x;"));
        DeclarationSnippet method = methodKey(assertEval("double mu() { return x * 4; }"));
        assertEval("x == 0;", "true");
        assertEval("mu() == 0.0;", "true");

        assertDrop(x,
                ste(x, VALID, DROPPED, true, null),
                ste(method, VALID, RECOVERABLE_DEFINED, false, x));
        assertUnresolvedDependencies1(method, RECOVERABLE_DEFINED, "variable x");
        assertEvalUnresolvedException("mu();", "mu", 1, 0);

        assertVariables();
        assertMethods();
        assertActiveKeys();
    }

    public void testDropMethodToMethod() {
        PersistentSnippet a = methodKey(assertEval("double a() { return 2; }"));
        DeclarationSnippet b = methodKey(assertEval("double b() { return a() * 10; }"));
        assertEval("double c() { return b() * 3; }");
        DeclarationSnippet d = methodKey(assertEval("double d() { return c() + 1000; }"));
        assertEval("d();", "1060.0");
        assertDrop(a,
                ste(a, VALID, DROPPED, true, null),
                ste(b, VALID, RECOVERABLE_DEFINED, false, a));
        assertUnresolvedDependencies1(b, RECOVERABLE_DEFINED, "method a()");
        assertUnresolvedDependencies(d, 0);
        assertEvalUnresolvedException("d();", "b", 1, 0);
        assertMethods();
        assertActiveKeys();
    }

    public void testDropClassToMethod() {
        PersistentSnippet c = classKey(assertEval("class C { int f() { return 7; } }"));
        DeclarationSnippet m = methodKey(assertEval("int m() { return new C().f(); }"));
        assertDrop(c,
                ste(c, VALID, DROPPED, true, null),
                ste(m, VALID, RECOVERABLE_DEFINED, false, c));
        assertUnresolvedDependencies1(m, RECOVERABLE_DEFINED, "class C");
        assertEvalUnresolvedException("m();", "m", 1, 0);
        assertActiveKeys();
    }

    public void testDropVarToClass() {
        PersistentSnippet x = varKey(assertEval("int x;"));
        DeclarationSnippet a = classKey(assertEval("class A { double a = 4 * x; }"));
        assertDrop(x,
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                ste(x, VALID, DROPPED, true, null),
                ste(a, VALID, RECOVERABLE_NOT_DEFINED, true, x));
        assertUnresolvedDependencies1(a, RECOVERABLE_NOT_DEFINED, "variable x");
        assertDeclareFail("new A().a;", "compiler.err.cant.resolve.location");
        assertVariables();
        assertActiveKeys();
    }

    public void testDropMethodToClass() {
        PersistentSnippet x = methodKey(assertEval("int x() { return 0; }"));
        DeclarationSnippet a = classKey(assertEval("class A { double a = 4 * x(); }"));
        assertDrop(x,
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                ste(x, VALID, DROPPED, true, null),
                ste(a, VALID, RECOVERABLE_NOT_DEFINED, true, x));
        assertUnresolvedDependencies1(a, RECOVERABLE_NOT_DEFINED, "method x()");
        assertDeclareFail("new A().a;", "compiler.err.cant.resolve.location");
        assertMethods();
        assertActiveKeys();
    }

    public void testDropClassToClass() {
        PersistentSnippet a = classKey(assertEval("class A {}"));
        PersistentSnippet b = classKey(assertEval("class B extends A {}"));
        PersistentSnippet c = classKey(assertEval("class C extends B {}"));
        PersistentSnippet d = classKey(assertEval("class D extends C {}"));
        assertDrop(a,
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                ste(a, VALID, DROPPED, true, null),
                ste(b, VALID, RECOVERABLE_NOT_DEFINED, true, a),
                ste(c, VALID, RECOVERABLE_NOT_DEFINED, true, b),
                ste(d, VALID, RECOVERABLE_NOT_DEFINED, true, c));
        assertUnresolvedDependencies1((DeclarationSnippet) b, RECOVERABLE_NOT_DEFINED, "class A");
        assertDrop(c,
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                ste(c, RECOVERABLE_NOT_DEFINED, DROPPED, false, null),
                ste(d, RECOVERABLE_NOT_DEFINED, RECOVERABLE_NOT_DEFINED, false, c));
        assertEval("interface A {}", null, null,
                DiagCheck.DIAG_OK, DiagCheck.DIAG_ERROR,
                ste(a, DROPPED, VALID, true, null),
                ste(b, RECOVERABLE_NOT_DEFINED, RECOVERABLE_NOT_DEFINED, false, MAIN_SNIPPET));
        assertClasses();
        assertActiveKeys();
    }

    public void testDropNoUpdate() {
        String as1 = "class A {}";
        String as2 = "class A extends java.util.ArrayList<Boolean> {}";
        PersistentSnippet a = classKey(assertEval(as1, added(VALID)));
        PersistentSnippet b = classKey(assertEval("class B extends A {}", added(VALID)));
        PersistentSnippet ax = classKey(assertEval(as2,
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(a, VALID, OVERWRITTEN, false, MAIN_SNIPPET),
                ste(b, VALID, VALID, true, MAIN_SNIPPET)));
        ax = classKey(assertEval(as1,
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(ax, VALID, OVERWRITTEN, false, MAIN_SNIPPET),
                ste(b, VALID, VALID, true, MAIN_SNIPPET)));
        assertDrop(b,
                ste(b, VALID, DROPPED, true, null));
        ax = classKey(assertEval(as2,
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(ax, VALID, OVERWRITTEN, false, MAIN_SNIPPET)));
        assertEval(as1,
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(ax, VALID, OVERWRITTEN, false, MAIN_SNIPPET));
    }
}
