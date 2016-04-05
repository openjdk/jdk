/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @test 8080069 8152925
 * @summary Test of Snippet redefinition and replacement.
 * @build KullaTesting TestingInputStream
 * @run testng ReplaceTest
 */

import java.util.Collection;

import java.util.List;
import jdk.jshell.Snippet;
import jdk.jshell.MethodSnippet;
import jdk.jshell.PersistentSnippet;
import jdk.jshell.TypeDeclSnippet;
import jdk.jshell.VarSnippet;
import jdk.jshell.DeclarationSnippet;
import org.testng.annotations.Test;

import jdk.jshell.SnippetEvent;
import jdk.jshell.UnresolvedReferenceException;
import static org.testng.Assert.assertEquals;
import static jdk.jshell.Snippet.Status.*;
import static jdk.jshell.Snippet.SubKind.*;
import static org.testng.Assert.assertTrue;

@Test
public class ReplaceTest extends KullaTesting {

    public void testRedefine() {
        Snippet vx = varKey(assertEval("int x;"));
        Snippet mu = methodKey(assertEval("int mu() { return x * 4; }"));
        Snippet c = classKey(assertEval("class C { String v() { return \"#\" + mu(); } }"));
        assertEval("C c0  = new C();");
        assertEval("c0.v();", "\"#0\"");
        assertEval("int x = 10;", "10",
                ste(MAIN_SNIPPET, VALID, VALID, false, null),
                ste(vx, VALID, OVERWRITTEN, false, MAIN_SNIPPET));
        assertEval("c0.v();", "\"#40\"");
        assertEval("C c = new C();");
        assertEval("c.v();", "\"#40\"");
        assertEval("int mu() { return x * 3; }",
                ste(MAIN_SNIPPET, VALID, VALID, false, null),
                ste(mu, VALID, OVERWRITTEN, false, MAIN_SNIPPET));
        assertEval("c.v();", "\"#30\"");
        assertEval("class C { String v() { return \"@\" + mu(); } }",
                ste(MAIN_SNIPPET, VALID, VALID, false, null),
                ste(c, VALID, OVERWRITTEN, false, MAIN_SNIPPET));
        assertEval("c0.v();", "\"@30\"");
        assertEval("c = new C();");
        assertEval("c.v();", "\"@30\"");
        assertActiveKeys();
    }

    public void testReplaceClassToVar() {
        Snippet oldA = classKey(assertEval("class A { public String toString() { return \"old\"; } }"));
        Snippet v = varKey(assertEval("A a = new A();", "old"));
        assertEval("a;", "old");
        Snippet midA = classKey(assertEval("class A { public String toString() { return \"middle\"; } }",
                ste(MAIN_SNIPPET, VALID, VALID, false, null),
                ste(oldA, VALID, OVERWRITTEN, false, MAIN_SNIPPET)));
        assertEval("a;", "middle");
        assertEval("class A { int x; public String toString() { return \"new\"; } }",
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(midA, VALID, OVERWRITTEN, false, MAIN_SNIPPET),
                ste(v, VALID, VALID, true, MAIN_SNIPPET));
        assertEval("a;", "null");
        assertActiveKeys();
    }

    public void testReplaceVarToMethod() {
        Snippet x = varKey(assertEval("int x;"));
        Snippet musn = methodKey(assertEval("double mu() { return x * 4; }"));
        assertEval("x == 0;", "true");
        assertEval("mu() == 0.0;", "true");
        assertEval("double x = 2.5;",
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(x, VALID, OVERWRITTEN, false, MAIN_SNIPPET),
                ste(musn, VALID, VALID, false, MAIN_SNIPPET));
        Collection<MethodSnippet> meths = getState().methods();
        assertEquals(meths.size(), 1);
        assertTrue(musn == meths.iterator().next(), "Identity must not change");
        assertEval("x == 2.5;", "true");
        assertEval("mu() == 10.0;", "true");  // Auto redefine
        assertActiveKeys();
    }

    public void testReplaceMethodToMethod() {
        Snippet a = methodKey(assertEval("double a() { return 2; }"));
        Snippet b = methodKey(assertEval("double b() { return a() * 10; }"));
        assertEval("double c() { return b() * 3; }");
        assertEval("double d() { return c() + 1000; }");
        assertEval("d();", "1060.0");
        assertEval("int a() { return 5; }",
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(a, VALID, OVERWRITTEN, false, MAIN_SNIPPET),
                ste(b, VALID, VALID, false, MAIN_SNIPPET));
        assertEval("d();", "1150.0");
        assertActiveKeys();
    }

    public void testReplaceClassToMethod() {
        Snippet c = classKey(assertEval("class C { int f() { return 7; } }"));
        Snippet m = methodKey(assertEval("int m() { return new C().f(); }"));
        assertEval("m();", "7");
        assertEval("class C { int x = 99; int f() { return x; } }",
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(c, VALID, OVERWRITTEN, false, MAIN_SNIPPET),
                ste(m, VALID, VALID, false, MAIN_SNIPPET));
        assertEval("m();", "99");
        assertActiveKeys();
    }

    public void testReplaceVarToClass() {
        Snippet x = varKey(assertEval("int x;"));
        Snippet c = classKey(assertEval("class A { double a = 4 * x; }"));
        assertEval("x == 0;", "true");
        assertEval("new A().a == 0.0;", "true");
        assertEval("double x = 2.5;",
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(x, VALID, OVERWRITTEN, false, MAIN_SNIPPET),
                ste(c, VALID, VALID, false, MAIN_SNIPPET));
        Collection<TypeDeclSnippet> classes = getState().types();
        assertEquals(classes.size(), 1);
        assertTrue(c == classes.iterator().next(), "Identity must not change");
        assertEval("x == 2.5;", "true");
        assertEval("new A().a == 10.0;", "true");
        assertActiveKeys();
    }

    public void testReplaceMethodToClass() {
        Snippet x = methodKey(assertEval("int x() { return 0; }"));
        Snippet c = classKey(assertEval("class A { double a = 4 * x(); }"));
        assertEval("x() == 0;", "true");
        assertEval("new A().a == 0.0;", "true");
        assertEval("double x() { return 2.5; }",
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(x, VALID, OVERWRITTEN, false, MAIN_SNIPPET),
                ste(c, VALID, VALID, false, MAIN_SNIPPET));
        assertEval("x();", "2.5");
        Collection<TypeDeclSnippet> classes = getState().types();
        assertEquals(classes.size(), 1);
        assertTrue(c == classes.iterator().next(), "Identity must not change");
        assertEval("x() == 2.5;", "true");
        assertEval("new A().a == 10.0;", "true");
        assertActiveKeys();
    }

    public void testReplaceClassToClass() {
        TypeDeclSnippet a = classKey(assertEval("class A {}"));
        assertTypeDeclSnippet(a, "A", VALID, CLASS_SUBKIND, 0, 0);
        TypeDeclSnippet b = classKey(assertEval("class B extends A {}"));
        TypeDeclSnippet c = classKey(assertEval("class C extends B {}"));
        TypeDeclSnippet d = classKey(assertEval("class D extends C {}"));
        assertEval("class A { int x; public String toString() { return \"NEW\"; } }",
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(a, VALID, OVERWRITTEN, false, MAIN_SNIPPET),
                ste(b, VALID, VALID, true, MAIN_SNIPPET),
                ste(c, VALID, VALID, true, b),
                ste(d, VALID, VALID, true, c));
        assertTypeDeclSnippet(b, "B", VALID, CLASS_SUBKIND, 0, 0);
        assertTypeDeclSnippet(c, "C", VALID, CLASS_SUBKIND, 0, 0);
        assertTypeDeclSnippet(d, "D", VALID, CLASS_SUBKIND, 0, 0);
        assertEval("new D();", "NEW");
        assertActiveKeys();
    }

    public void testOverwriteReplaceMethod() {
        MethodSnippet k1 = methodKey(assertEval("String m(Integer i) { return i.toString(); }"));
        MethodSnippet k2 = methodKey(assertEval("String m(java.lang.Integer i) { return \"java.lang.\" + i.toString(); }",
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(k1, VALID, OVERWRITTEN, false, MAIN_SNIPPET)));
        assertMethodDeclSnippet(k1, "m", "(Integer)String", OVERWRITTEN, 0, 0);
        assertEval("m(6);", "\"java.lang.6\"");
        assertEval("String m(Integer i) { return i.toString(); }",
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(k2, VALID, OVERWRITTEN, false, MAIN_SNIPPET));
        assertMethodDeclSnippet(k2, "m", "(java.lang.Integer)String", OVERWRITTEN, 0, 0);
        assertEval("m(6);", "\"6\"");
        assertActiveKeys();
    }

    public void testOverwriteMethodForwardReferenceClass() {
        Snippet k1 = methodKey(assertEval("int q(Boo b) { return b.x; }",
                added(RECOVERABLE_NOT_DEFINED)));
        assertUnresolvedDependencies1((MethodSnippet) k1, RECOVERABLE_NOT_DEFINED, "class Boo");
        assertEval("class Boo { int x = 55; }",
                added(VALID),
                ste(k1, RECOVERABLE_NOT_DEFINED, VALID, true, null));
        assertMethodDeclSnippet((MethodSnippet) k1, "q", "(Boo)int", VALID, 0, 0);
        assertEval("q(new Boo());", "55");
        assertActiveKeys();
    }

    public void testOverwriteMethodForwardReferenceClassImport() {
        MethodSnippet k1 = methodKey(assertEval("int ff(List lis) { return lis.size(); }",
                added(RECOVERABLE_NOT_DEFINED)));
        assertUnresolvedDependencies1(k1, RECOVERABLE_NOT_DEFINED, "class List");
        assertEval("import java.util.*;",
                added(VALID),
                ste(k1, RECOVERABLE_NOT_DEFINED, VALID, true, null));
        assertMethodDeclSnippet(k1, "ff", "(List)int", VALID, 0, 0);
        assertEval("ff(new ArrayList());", "0");
        assertActiveKeys();
    }

    public void testForwardVarToMethod() {
        DeclarationSnippet t = methodKey(assertEval("int t() { return x; }", added(RECOVERABLE_DEFINED)));
        assertUnresolvedDependencies1(t, RECOVERABLE_DEFINED, "variable x");
        assertEvalUnresolvedException("t();", "t", 1, 0);
        Snippet x = varKey(assertEval("int x = 33;", "33",
                added(VALID),
                ste(t, RECOVERABLE_DEFINED, VALID, false, null)));
        assertEval("t();", "33");
        assertEval("double x = 0.88;",
                "0.88", null,
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(x, VALID, OVERWRITTEN, false, MAIN_SNIPPET),
                ste(t, VALID, RECOVERABLE_DEFINED, false, MAIN_SNIPPET));
        assertEvalUnresolvedException("t();", "t", 0, 1);
        assertActiveKeys();
    }

    public void testForwardMethodToMethod() {
        Snippet t = methodKey(assertEval("int t() { return f(); }", added(RECOVERABLE_DEFINED)));
        Snippet f = methodKey(assertEval("int f() { return g(); }",
                added(RECOVERABLE_DEFINED),
                ste(t, RECOVERABLE_DEFINED, VALID, false, null)));
        assertUnresolvedDependencies1((DeclarationSnippet) f, RECOVERABLE_DEFINED, "method g()");
        assertEvalUnresolvedException("t();", "f", 1, 0);
        Snippet g = methodKey(assertEval("int g() { return 55; }",
                added(VALID),
                ste(f, RECOVERABLE_DEFINED, VALID, false, null)));
        assertEval("t();", "55");
        assertEval("double g() { return 3.14159; }",
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(g, VALID, OVERWRITTEN, false, MAIN_SNIPPET),
                ste(f, VALID, RECOVERABLE_DEFINED, false, MAIN_SNIPPET));
        DeclarationSnippet exsn = assertEvalUnresolvedException("t();", "f", 0, 1);
        assertTrue(exsn == f, "Identity must not change");
        assertActiveKeys();
    }

    public void testForwardClassToMethod() {
        DeclarationSnippet t = methodKey(assertEval("int t() { return new A().f(); }", added(RECOVERABLE_DEFINED)));
        assertUnresolvedDependencies1(t, RECOVERABLE_DEFINED, "class A");
        assertEvalUnresolvedException("t();", "t", 1, 0);
        Snippet a = classKey(assertEval(
                "class A {\n" +
                        "   int f() { return 10; }\n" +
                "}",
                added(VALID),
                ste(t, RECOVERABLE_DEFINED, VALID, false, null)));
        assertEval("t();", "10");
        assertEval(
                "class A {\n" +
                "   double f() { return 88.0; }\n" +
                "}",
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(a, VALID, OVERWRITTEN, false, MAIN_SNIPPET),
                ste(t, VALID, RECOVERABLE_DEFINED, false, MAIN_SNIPPET));
        assertEvalUnresolvedException("t();", "t", 0, 1);
        assertActiveKeys();
    }

    public void testForwardVarToClass() {
        DeclarationSnippet a = classKey(assertEval("class A { int f() { return g; } }", added(RECOVERABLE_DEFINED)));
        assertUnresolvedDependencies1(a, RECOVERABLE_DEFINED, "variable g");
        Snippet g = varKey(assertEval("int g = 10;", "10",
                added(VALID),
                ste(a, RECOVERABLE_DEFINED, VALID, false, null)));
        assertEval("new A().f();", "10");
        assertEval("double g = 10;", "10.0", null,
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(g, VALID, OVERWRITTEN, false, MAIN_SNIPPET),
                ste(a, VALID, RECOVERABLE_DEFINED, false, MAIN_SNIPPET));
        assertUnresolvedDependencies(a, 0);
        assertActiveKeys();
    }

    public void testForwardVarToClassGeneric() {
        DeclarationSnippet a = classKey(assertEval("class A<T> { final T x; A(T v) { this.x = v; } ; T get() { return x; } int core() { return g; } }", added(RECOVERABLE_DEFINED)));
        assertUnresolvedDependencies1(a, RECOVERABLE_DEFINED, "variable g");

        List<SnippetEvent> events = assertEval("A<String> as = new A<>(\"hi\");", null,
                UnresolvedReferenceException.class, DiagCheck.DIAG_OK, DiagCheck.DIAG_OK, null);
        SnippetEvent ste = events.get(0);
        Snippet assn = ste.snippet();
        DeclarationSnippet unsn = ((UnresolvedReferenceException) ste.exception()).getSnippet();
        assertEquals(unsn.name(), "A", "Wrong with unresolved");
        assertEquals(getState().unresolvedDependencies(unsn).size(), 1, "Wrong size unresolved");
        assertEquals(getState().diagnostics(unsn).size(), 0, "Expected no diagnostics");

        Snippet g = varKey(assertEval("int g = 10;", "10",
                added(VALID),
                ste(a, RECOVERABLE_DEFINED, VALID, false, MAIN_SNIPPET)));
        assertEval("A<String> as = new A<>(\"low\");",
                ste(MAIN_SNIPPET, VALID, VALID, false, null),
                ste(assn, VALID, OVERWRITTEN, false, MAIN_SNIPPET));
        assertEval("as.get();", "\"low\"");
        assertUnresolvedDependencies(a, 0);
        assertActiveKeys();
    }

   public void testForwardVarToClassExtendsImplements() {
        DeclarationSnippet ik = classKey(assertEval("interface I { default int ii() { return 1; } }", added(VALID)));
        DeclarationSnippet jk = classKey(assertEval("interface J { default int jj() { return 2; } }", added(VALID)));
        DeclarationSnippet ck = classKey(assertEval("class C { int cc() { return 3; } }", added(VALID)));
        DeclarationSnippet dk = classKey(assertEval("class D extends C implements I,J { int dd() { return g; } }", added(RECOVERABLE_DEFINED)));
        DeclarationSnippet ek = classKey(assertEval("class E extends D { int ee() { return 5; } }", added(VALID)));
        assertUnresolvedDependencies1(dk, RECOVERABLE_DEFINED, "variable g");
        assertEvalUnresolvedException("new D();", "D", 1, 0);
        assertEvalUnresolvedException("new E();", "D", 1, 0);
        VarSnippet g = varKey(assertEval("int g = 10;", "10",
                added(VALID),
                ste(dk, RECOVERABLE_DEFINED, VALID, false, MAIN_SNIPPET)));
        assertEval("E e = new E();");
        assertDrop(g,
                ste(g, VALID, DROPPED, true, null),
                ste(dk, VALID, RECOVERABLE_DEFINED, false, g));
        assertEvalUnresolvedException("new D();", "D", 1, 0);
        assertEvalUnresolvedException("new E();", "D", 1, 0);
        assertEval("e.ee();", "5");
        assertEvalUnresolvedException("e.dd();", "D", 1, 0);
        assertEval("e.cc();", "3");
        assertEval("e.jj();", "2");
        assertEval("e.ii();", "1");
        assertActiveKeys();
    }

    public void testForwardVarToInterface() {
        DeclarationSnippet i = classKey(assertEval("interface I { default int f() { return x; } }", added(RECOVERABLE_DEFINED)));
        assertUnresolvedDependencies1(i, RECOVERABLE_DEFINED, "variable x");
        DeclarationSnippet c = classKey(assertEval("class C implements I { int z() { return 2; } }", added(VALID)));
        assertEval("C c = new C();");
        assertEval("c.z();", "2");
        assertEvalUnresolvedException("c.f()", "I", 1, 0);
        Snippet g = varKey(assertEval("int x = 55;", "55",
                added(VALID),
                ste(i, RECOVERABLE_DEFINED, VALID, false, null)));
        assertEval("c.f();", "55");
        assertUnresolvedDependencies(i, 0);
        assertActiveKeys();
    }

    public void testForwardVarToEnum() {
        DeclarationSnippet a = classKey(assertEval("enum E { Q, W, E; float ff() { return fff; } }", added(RECOVERABLE_DEFINED)));
        assertUnresolvedDependencies1(a, RECOVERABLE_DEFINED, "variable fff");
        Snippet g = varKey(assertEval("float fff = 4.5f;", "4.5",
                added(VALID),
                ste(a, RECOVERABLE_DEFINED, VALID, false, null)));
        assertEval("E.Q.ff();", "4.5");
        assertEval("double fff = 3.3;", "3.3", null,
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(g, VALID, OVERWRITTEN, false, MAIN_SNIPPET),
                ste(a, VALID, RECOVERABLE_DEFINED, false, MAIN_SNIPPET));
        assertUnresolvedDependencies(a, 0);
        assertActiveKeys();
    }

    public void testForwardMethodToClass() {
        DeclarationSnippet a = classKey(assertEval("class A { int f() { return g(); } }", added(RECOVERABLE_DEFINED)));
        assertUnresolvedDependencies1(a, RECOVERABLE_DEFINED, "method g()");
        assertEval("A foo() { return null; }");
        assertEvalUnresolvedException("new A();", "A", 1, 0);
        Snippet g = methodKey(assertEval("int g() { return 10; }",
                added(VALID),
                ste(a, RECOVERABLE_DEFINED, VALID, false, null)));
        assertEval("new A().f();", "10");
        assertEval("double g() { return 10; }",
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(g, VALID, OVERWRITTEN, false, MAIN_SNIPPET),
                ste(a, VALID, RECOVERABLE_DEFINED, false, MAIN_SNIPPET));
        assertUnresolvedDependencies(a, 0);
        assertActiveKeys();
    }

    public void testForwardClassToClass1() {
        Snippet a = classKey(assertEval("class A { B b = new B(); }", added(RECOVERABLE_NOT_DEFINED)));
        assertDeclareFail("new A().b;", "compiler.err.cant.resolve.location");

        Snippet b = classKey(assertEval("class B { public String toString() { return \"B\"; } }",
                added(VALID),
                ste(a, RECOVERABLE_NOT_DEFINED, VALID, true, null)));
        assertEval("new A().b;", "B");
        assertEval("interface B { }",
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(b, VALID, OVERWRITTEN, false, MAIN_SNIPPET),
                ste(a, VALID, RECOVERABLE_DEFINED, true, MAIN_SNIPPET));
        assertEvalUnresolvedException("new A().b;", "A", 0, 1);
        assertActiveKeys();
    }

    public void testForwardClassToClass2() {
        Snippet a = classKey(assertEval("class A extends B { }", added(RECOVERABLE_NOT_DEFINED)));
        assertDeclareFail("new A();", "compiler.err.cant.resolve.location");

        Snippet b = classKey(assertEval("class B { public String toString() { return \"B\"; } }",
                added(VALID),
                ste(a, RECOVERABLE_NOT_DEFINED, VALID, true, null)));
        assertEval("new A();", "B");
        assertEval("interface B { }",
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(b, VALID, OVERWRITTEN, false, MAIN_SNIPPET),
                ste(a, VALID, RECOVERABLE_NOT_DEFINED, true, MAIN_SNIPPET));
        assertDeclareFail("new A();", "compiler.err.cant.resolve.location");
        assertActiveKeys();
    }

    public void testForwardClassToClass3() {
        Snippet a = classKey(assertEval("interface A extends B { static int f() { return 10; } }", added(RECOVERABLE_NOT_DEFINED)));
        assertDeclareFail("A.f();", "compiler.err.cant.resolve.location");

        Snippet b = classKey(assertEval("interface B { }",
                added(VALID),
                ste(a, RECOVERABLE_NOT_DEFINED, VALID, true, null)));
        assertEval("A.f();", "10");
        assertEval("class B { }",
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(b, VALID, OVERWRITTEN, false, MAIN_SNIPPET),
                ste(a, VALID, RECOVERABLE_NOT_DEFINED, true, MAIN_SNIPPET));
        assertDeclareFail("A.f();", "compiler.err.cant.resolve.location");
        assertActiveKeys();
    }

    public void testImportDeclare() {
        Snippet singleImport = importKey(assertEval("import java.util.List;", added(VALID)));
        Snippet importOnDemand = importKey(assertEval("import java.util.*;", added(VALID)));
        Snippet singleStaticImport = importKey(assertEval("import static java.lang.Math.abs;", added(VALID)));
        Snippet staticImportOnDemand = importKey(assertEval("import static java.lang.Math.*;", added(VALID)));
        assertEval("import java.util.List; //again",
                ste(MAIN_SNIPPET, VALID, VALID, false, null),
                ste(singleImport, VALID, OVERWRITTEN, false, MAIN_SNIPPET));
        assertEval("import java.util.*; //again",
                ste(MAIN_SNIPPET, VALID, VALID, false, null),
                ste(importOnDemand, VALID, OVERWRITTEN, false, MAIN_SNIPPET));
        assertEval("import static java.lang.Math.abs; //again",
                ste(MAIN_SNIPPET, VALID, VALID, false, null),
                ste(singleStaticImport, VALID, OVERWRITTEN, false, MAIN_SNIPPET));
        assertEval("import static java.lang.Math.*; //again",
                ste(MAIN_SNIPPET, VALID, VALID, false, null),
                ste(staticImportOnDemand, VALID, OVERWRITTEN, false, MAIN_SNIPPET));
        assertActiveKeys();
    }

    public void testForwardVariable() {
        assertEval("int f() { return x; }", added(RECOVERABLE_DEFINED));
        assertEvalUnresolvedException("f();", "f", 1, 0);
        assertActiveKeys();
    }

    public void testLocalClassInUnresolved() {
        Snippet f = methodKey(assertEval("void f() { class A {} g(); }", added(RECOVERABLE_DEFINED)));
        assertEval("void g() {}",
                added(VALID),
                ste(f, RECOVERABLE_DEFINED, VALID, false, null));
        assertEval("f();", "");
    }

    @Test(enabled = false) // TODO 8129420
    public void testLocalClassEvolve() {
        Snippet j = methodKey(assertEval("Object j() { return null; }", added(VALID)));
        assertEval("Object j() { class B {}; return null; }",
                ste(MAIN_SNIPPET, VALID, VALID, false, null));
        assertEval("Object j() { class B {}; return new B(); }",
                ste(MAIN_SNIPPET, VALID, VALID, false, null));
        assertEval("j().getClass().getSimpleName();", "\"B\"");
        assertEval("Object j() { class B { int p; public String toString() { return \"Yep\";} }; return new B(); }",
                ste(MAIN_SNIPPET, VALID, VALID, false, null));
        assertEval("j().getClass().getSimpleName();", "\"B\"");
        assertEval("j();", "Yep");
    }

    public void testForwardSingleImportMethodToMethod() {
        DeclarationSnippet string = methodKey(assertEval("String string() { return format(\"string\"); }",
                added(RECOVERABLE_DEFINED)));
        assertUnresolvedDependencies1(string, RECOVERABLE_DEFINED, "method format(java.lang.String)");
        assertEvalUnresolvedException("string();", "string", 1, 0);
        assertEval("import static java.lang.String.format;",
                added(VALID),
                ste(string, RECOVERABLE_DEFINED, VALID, false, null));
        assertEval("string();", "\"string\"");

        assertEval("double format(String s) { return 0; }",
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                added(VALID),
                ste(string, VALID, RECOVERABLE_DEFINED, false, null));
        assertEvalUnresolvedException("string();", "string", 0, 1);
        assertActiveKeys();
    }

    public void testForwardImportMethodOnDemandToMethod() {
        DeclarationSnippet string = methodKey(assertEval("String string() { return format(\"string\"); }",
                added(RECOVERABLE_DEFINED)));
        assertUnresolvedDependencies1(string, RECOVERABLE_DEFINED, "method format(java.lang.String)");
        assertEvalUnresolvedException("string();", "string", 1, 0);
        assertEval("import static java.lang.String.*;",
                added(VALID),
                ste(string, RECOVERABLE_DEFINED, VALID, false, null));
        assertEval("string();", "\"string\"");

        assertEval("double format(String s) { return 0; }",
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                added(VALID),
                ste(string, VALID, RECOVERABLE_DEFINED, false, null));
        assertEvalUnresolvedException("string();", "string", 0, 1);
        assertActiveKeys();
    }

    public void testForwardSingleImportFieldToMethod() {
        DeclarationSnippet pi = methodKey(assertEval("double pi() { return PI; }",
                added(RECOVERABLE_DEFINED)));
        assertUnresolvedDependencies1(pi, RECOVERABLE_DEFINED, "variable PI");
        assertEvalUnresolvedException("pi();", "pi", 1, 0);
        assertEval("import static java.lang.Math.PI;",
                added(VALID),
                ste(pi, RECOVERABLE_DEFINED, VALID, false, null));
        assertEval("Math.abs(pi() - 3.1415) < 0.001;", "true");

        assertEval("String PI;",
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                added(VALID),
                ste(pi, VALID, RECOVERABLE_DEFINED, false, null));
        assertEvalUnresolvedException("pi();", "pi", 0, 1);
        assertActiveKeys();
    }

    public void testForwardImportFieldOnDemandToMethod() {
        DeclarationSnippet pi = methodKey(assertEval("double pi() { return PI; }",
                added(RECOVERABLE_DEFINED)));
        assertUnresolvedDependencies1(pi, RECOVERABLE_DEFINED, "variable PI");
        assertEvalUnresolvedException("pi();", "pi", 1, 0);
        assertEval("import static java.lang.Math.*;",
                added(VALID),
                ste(pi, RECOVERABLE_DEFINED, VALID, false, MAIN_SNIPPET));
        assertEval("Math.abs(pi() - 3.1415) < 0.001;", "true");

        assertEval("String PI;",
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                added(VALID),
                ste(pi, VALID, RECOVERABLE_DEFINED, false, MAIN_SNIPPET));
        assertEvalUnresolvedException("pi();", "pi", 0, 1);
        assertActiveKeys();
    }

    public void testForwardSingleImportMethodToClass1() {
        PersistentSnippet a = classKey(assertEval("class A { String s = format(\"%d\", 10); }",
                added(RECOVERABLE_DEFINED)));
        assertEvalUnresolvedException("new A();", "A", 1, 0);
        assertEval("import static java.lang.String.format;",
                added(VALID),
                ste(a, RECOVERABLE_DEFINED, VALID, false, null));
        assertEval("new A().s;", "\"10\"");
        PersistentSnippet format = methodKey(assertEval("void format(String s, int d) { }",
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                added(VALID),
                ste(a, VALID, RECOVERABLE_DEFINED, false, MAIN_SNIPPET)));
        assertEvalUnresolvedException("new A();", "A", 0, 1);
        assertActiveKeys();
        assertDrop(format,
                ste(format, VALID, DROPPED, true, null),
                ste(a, RECOVERABLE_DEFINED, VALID, false, format));
    }

    public void testForwardSingleImportMethodToClass2() {
        PersistentSnippet a = classKey(assertEval("class A { String s() { return format(\"%d\", 10); } }",
                added(RECOVERABLE_DEFINED)));
        assertEvalUnresolvedException("new A();", "A", 1, 0);
        assertEval("import static java.lang.String.format;",
                added(VALID),
                ste(a, RECOVERABLE_DEFINED, VALID, false, null));
        assertEval("new A().s();", "\"10\"");
        PersistentSnippet format = methodKey(assertEval("void format(String s, int d) { }",
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                added(VALID),
                ste(a, VALID, RECOVERABLE_DEFINED, false, null)));
        assertEvalUnresolvedException("new A();", "A", 0, 1);
        assertActiveKeys();
        assertDrop(format,
                ste(format, VALID, DROPPED, true, null),
                ste(a, RECOVERABLE_DEFINED, VALID, false, format));
    }

    public void testForwardSingleImportClassToClass1() {
        PersistentSnippet a = classKey(assertEval("class A { static List<Integer> list; }",
                added(RECOVERABLE_NOT_DEFINED)));
        assertDeclareFail("new A();", "compiler.err.cant.resolve.location");
        assertEval("import java.util.List;",
                added(VALID),
                ste(a, RECOVERABLE_NOT_DEFINED, VALID, true, null));
        assertEval("import java.util.Arrays;", added(VALID));
        assertEval("A.list = Arrays.asList(1, 2, 3);", "[1, 2, 3]");

        PersistentSnippet list = classKey(assertEval("class List {}",
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                added(VALID),
                ste(a, VALID, RECOVERABLE_NOT_DEFINED, true, null)));
        assertDeclareFail("A.list = Arrays.asList(1, 2, 3);", "compiler.err.already.defined.static.single.import");
        assertActiveKeys();
        assertDrop(list,
                ste(list, VALID, DROPPED, true, null),
                ste(a, RECOVERABLE_NOT_DEFINED, VALID, true, list));
    }

    public void testForwardSingleImportClassToClass2() {
        PersistentSnippet clsA = classKey(assertEval("class A extends ArrayList<Integer> { }",
                added(RECOVERABLE_NOT_DEFINED)));
        assertDeclareFail("new A();", "compiler.err.cant.resolve.location");
        assertEval("import java.util.ArrayList;",
                added(VALID),
                ste(clsA, RECOVERABLE_NOT_DEFINED, VALID, true, MAIN_SNIPPET));
        Snippet vara = varKey(assertEval("A a = new A();", "[]"));

        PersistentSnippet arraylist = classKey(assertEval("class ArrayList {}",
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                added(VALID),
                ste(clsA, VALID, RECOVERABLE_NOT_DEFINED, true, MAIN_SNIPPET),
                ste(vara, VALID, RECOVERABLE_NOT_DEFINED, true, clsA)));
        assertDeclareFail("A a = new A();", "compiler.err.cant.resolve.location",
                ste(MAIN_SNIPPET, RECOVERABLE_NOT_DEFINED, REJECTED, false, null),
                ste(vara, RECOVERABLE_NOT_DEFINED, OVERWRITTEN, false, MAIN_SNIPPET));
        assertActiveKeys();
        assertDrop(arraylist,
                ste(arraylist, VALID, DROPPED, true, null),
                ste(clsA, RECOVERABLE_NOT_DEFINED, VALID, true, arraylist));
    }

    public void testForwardImportOnDemandMethodToClass1() {
        PersistentSnippet a = classKey(assertEval("class A { String s = format(\"%d\", 10); }",
                added(RECOVERABLE_DEFINED)));
        assertEvalUnresolvedException("new A();", "A", 1, 0);
        assertEval("import static java.lang.String.*;",
                added(VALID),
                ste(a, RECOVERABLE_DEFINED, VALID, false, null));
        assertEval("A x = new A();");
        assertEval("x.s;", "\"10\"");
        PersistentSnippet format = methodKey(assertEval("void format(String s, int d) { }",
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                added(VALID),
                ste(a, VALID, RECOVERABLE_DEFINED, false, null)));
        assertEvalUnresolvedException("new A();", "A", 0, 1);
        assertActiveKeys();
        assertDrop(format,
                ste(format, VALID, DROPPED, true, null),
                ste(a, RECOVERABLE_DEFINED, VALID, false, format));
        assertEval("x.s;", "\"10\"");
    }

    public void testForwardImportOnDemandMethodToClass2() {
        PersistentSnippet a = classKey(assertEval("class A { String s() { return format(\"%d\", 10); } }",
                added(RECOVERABLE_DEFINED)));
        assertEvalUnresolvedException("new A();", "A", 1, 0);
        assertEval("import static java.lang.String.*;",
                added(VALID),
                ste(a, RECOVERABLE_DEFINED, VALID, false, null));
        assertEval("new A().s();", "\"10\"");
        PersistentSnippet format = methodKey(assertEval("void format(String s, int d) { }",
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                added(VALID),
                ste(a, VALID, RECOVERABLE_DEFINED, false, null)));
        assertEvalUnresolvedException("new A();", "A", 0, 1);
        assertActiveKeys();
        assertDrop(format,
                ste(format, VALID, DROPPED, true, null),
                ste(a, RECOVERABLE_DEFINED, VALID, false, format));
    }

    public void testForwardImportOnDemandClassToClass1() {
        PersistentSnippet a = classKey(assertEval("class A { static List<Integer> list; }",
                added(RECOVERABLE_NOT_DEFINED)));
        assertDeclareFail("new A();", "compiler.err.cant.resolve.location");
        assertEval("import java.util.*;",
                added(VALID),
                ste(a, RECOVERABLE_NOT_DEFINED, VALID, true, null));
        assertEval("A.list = Arrays.asList(1, 2, 3);", "[1, 2, 3]");

        PersistentSnippet list = classKey(assertEval("class List {}",
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                added(VALID),
                ste(a, VALID, RECOVERABLE_NOT_DEFINED, true, null)));
        assertDeclareFail("A.list = Arrays.asList(1, 2, 3);", "compiler.err.cant.resolve.location");
        assertActiveKeys();
        assertDrop(list,
                ste(list, VALID, DROPPED, true, null),
                ste(a, RECOVERABLE_NOT_DEFINED, VALID, true, list));
    }

    public void testForwardImportOnDemandClassToClass2() {
        PersistentSnippet clsA = classKey(assertEval("class A extends ArrayList<Integer> { }",
                added(RECOVERABLE_NOT_DEFINED)));
        assertDeclareFail("new A();", "compiler.err.cant.resolve.location");
        assertEval("import java.util.*;",
                added(VALID),
                ste(clsA, RECOVERABLE_NOT_DEFINED, VALID, true, MAIN_SNIPPET));
        Snippet vara = varKey(assertEval("A a = new A();", "[]"));

        PersistentSnippet arraylist = classKey(assertEval("class ArrayList {}",
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                added(VALID),
                ste(clsA, VALID, RECOVERABLE_NOT_DEFINED, true, MAIN_SNIPPET),
                ste(vara, VALID, RECOVERABLE_NOT_DEFINED, true, clsA)));
        assertDeclareFail("new A();", "compiler.err.cant.resolve.location");
        assertActiveKeys();
        assertDrop(arraylist,
                ste(arraylist, VALID, DROPPED, true, null),
                ste(clsA, RECOVERABLE_NOT_DEFINED, VALID, true, arraylist),
                ste(vara, RECOVERABLE_NOT_DEFINED, VALID, true, clsA));
    }

    public void testForwardSingleImportFieldToClass1() {
        PersistentSnippet a = classKey(assertEval("class A { static double pi() { return PI; } }",
                added(RECOVERABLE_DEFINED)));
        assertEvalUnresolvedException("new A();", "A", 1, 0);
        assertEval("import static java.lang.Math.PI;",
                added(VALID),
                ste(a, RECOVERABLE_DEFINED, VALID, false, null));
        assertEval("Math.abs(A.pi() - 3.1415) < 0.001;", "true");

        PersistentSnippet list = varKey(assertEval("String PI;",
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                added(VALID),
                ste(a, VALID, RECOVERABLE_DEFINED, false, null)));
        assertEvalUnresolvedException("new A();", "A", 0, 1);
        assertActiveKeys();
        assertDrop(list,
                ste(list, VALID, DROPPED, true, null),
                ste(a, RECOVERABLE_DEFINED, VALID, false, list));
    }

    public void testForwardSingleImportFieldToClass2() {
        PersistentSnippet a = classKey(assertEval("class A { static double pi = PI; }",
                added(RECOVERABLE_DEFINED)));
        assertEvalUnresolvedException("new A();", "A", 1, 0);
        assertEval("import static java.lang.Math.PI;",
                added(VALID),
                ste(a, RECOVERABLE_DEFINED, VALID, true, null));
        assertEval("Math.abs(A.pi - 3.1415) < 0.001;", "true");

        PersistentSnippet list = varKey(assertEval("String PI;",
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                added(VALID),
                ste(a, VALID, RECOVERABLE_DEFINED, true, null)));
        assertEvalUnresolvedException("new A();", "A", 0, 1);
        assertActiveKeys();
        assertDrop(list,
                ste(list, VALID, DROPPED, true, null),
                ste(a, RECOVERABLE_DEFINED, VALID, true, list));
    }

    public void testForwardImportOnDemandFieldToClass1() {
        PersistentSnippet a = classKey(assertEval("class A { static double pi() { return PI; } }",
                added(RECOVERABLE_DEFINED)));
        assertEvalUnresolvedException("new A();", "A", 1, 0);
        assertEval("import static java.lang.Math.*;",
                added(VALID),
                ste(a, RECOVERABLE_DEFINED, VALID, false, null));
        assertEval("Math.abs(A.pi() - 3.1415) < 0.001;", "true");

        PersistentSnippet list = varKey(assertEval("String PI;",
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                added(VALID),
                ste(a, VALID, RECOVERABLE_DEFINED, false, null)));
        assertEvalUnresolvedException("new A();", "A", 0, 1);
        assertActiveKeys();
        assertDrop(list,
                ste(list, VALID, DROPPED, true, null),
                ste(a, RECOVERABLE_DEFINED, VALID, false, list));
    }

    public void testForwardImportOnDemandFieldToClass2() {
        PersistentSnippet a = classKey(assertEval("class A { static double pi = PI; }",
                added(RECOVERABLE_DEFINED)));
        assertEvalUnresolvedException("new A();", "A", 1, 0);
        assertEval("import static java.lang.Math.*;",
                added(VALID),
                ste(a, RECOVERABLE_DEFINED, VALID, true, null));
        assertEval("Math.abs(A.pi - 3.1415) < 0.001;", "true");

        PersistentSnippet list = varKey(assertEval("String PI;",
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                added(VALID),
                ste(a, VALID, RECOVERABLE_DEFINED, true, null)));
        assertEvalUnresolvedException("new A();", "A", 0, 1);
        assertActiveKeys();
        assertDrop(list,
                ste(list, VALID, DROPPED, true, null),
                ste(a, RECOVERABLE_DEFINED, VALID, true, list));
        assertEval("Math.abs(A.pi - 3.1415) < 0.001;", "true");
    }

    public void testReplaceCausesMethodReferenceError() {
        Snippet l = classKey(assertEval("interface Logger { public void log(String message); }", added(VALID)));
        Snippet v = varKey(assertEval("Logger l = System.out::println;", added(VALID)));
        assertEval("interface Logger { public boolean accept(String message);  }",
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(l, VALID, OVERWRITTEN, false, MAIN_SNIPPET),
                ste(v, VALID, RECOVERABLE_NOT_DEFINED, true, MAIN_SNIPPET));
    }

    public void testReplaceCausesClassCompilationError() {
        Snippet l = classKey(assertEval("interface L { }", added(VALID)));
        Snippet c = classKey(assertEval("class C implements L { }", added(VALID)));
        assertEval("interface L { void m(); }",
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(l, VALID, OVERWRITTEN, false, MAIN_SNIPPET),
                ste(c, VALID, RECOVERABLE_NOT_DEFINED, true, MAIN_SNIPPET));
    }

    public void testOverwriteNoUpdate() {
        String xsi = "int x = 5;";
        String xsd = "double x = 3.14159;";
        VarSnippet xi = varKey(assertEval(xsi, added(VALID)));
        String ms1 = "double m(Integer i) { return i + x; }";
        String ms2 = "double m(java.lang.Integer i) { return i + x; }";
        MethodSnippet k1 = methodKey(assertEval(ms1, added(VALID)));
        VarSnippet xd = varKey(assertEval(xsd,
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(xi, VALID, OVERWRITTEN, false, MAIN_SNIPPET),
                ste(k1, VALID, VALID, false, MAIN_SNIPPET)));
        MethodSnippet k2 = methodKey(assertEval(ms2,
                ste(MAIN_SNIPPET, VALID, VALID, true, null), //TODO: technically, should be false
                ste(k1, VALID, OVERWRITTEN, false, MAIN_SNIPPET)));
        VarSnippet xi2 = varKey(assertEval(xsi,
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(xd, VALID, OVERWRITTEN, false, MAIN_SNIPPET),
                ste(k2, VALID, VALID, false, MAIN_SNIPPET)));
        varKey(assertEval(xsd,
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(xi2, VALID, OVERWRITTEN, false, MAIN_SNIPPET),
                ste(k2, VALID, VALID, false, MAIN_SNIPPET)));
    }
}
