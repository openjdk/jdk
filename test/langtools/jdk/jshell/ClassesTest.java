/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8145239 8129559 8080354 8189248 8010319 8246353 8247456 8282160 8292755 8319532
 * @summary Tests for EvaluationState.classes
 * @build KullaTesting TestingInputStream ExpectedDiagnostic
 * @run junit/timeout=480 ClassesTest
 */

import java.util.ArrayList;
import java.util.List;

import javax.tools.Diagnostic;

import jdk.jshell.Snippet;
import jdk.jshell.TypeDeclSnippet;
import jdk.jshell.VarSnippet;

import jdk.jshell.Diag;
import jdk.jshell.Snippet.Status;
import static java.util.stream.Collectors.toList;
import static jdk.jshell.Snippet.Status.VALID;
import static jdk.jshell.Snippet.Status.RECOVERABLE_NOT_DEFINED;
import static jdk.jshell.Snippet.Status.RECOVERABLE_DEFINED;
import static jdk.jshell.Snippet.Status.DROPPED;
import static jdk.jshell.Snippet.Status.REJECTED;
import static jdk.jshell.Snippet.Status.OVERWRITTEN;
import static jdk.jshell.Snippet.Status.NONEXISTENT;
import static jdk.jshell.Snippet.SubKind.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ClassesTest extends KullaTesting {

    @Test
    public void noClasses() {
        assertNumberOfActiveClasses(0);
    }

    @Test
    public void testSignature1() {
        TypeDeclSnippet c1 = classKey(assertEval("class A extends B {}", added(RECOVERABLE_NOT_DEFINED)));
        assertTypeDeclSnippet(c1, "A", RECOVERABLE_NOT_DEFINED, CLASS_SUBKIND, 1, 0);
        TypeDeclSnippet c2 = classKey(assertEval("@interface A { Class<B> f() default B.class; }",
                ste(MAIN_SNIPPET, RECOVERABLE_NOT_DEFINED, RECOVERABLE_NOT_DEFINED, false, null),
                ste(c1, RECOVERABLE_NOT_DEFINED, OVERWRITTEN, false, MAIN_SNIPPET)));
        assertTypeDeclSnippet(c2, "A", RECOVERABLE_NOT_DEFINED, ANNOTATION_TYPE_SUBKIND, 1, 0);
        TypeDeclSnippet c3 = classKey(assertEval("enum A {; private A(B b) {} }",
                ste(MAIN_SNIPPET, RECOVERABLE_NOT_DEFINED, RECOVERABLE_NOT_DEFINED, false, null),
                ste(c2, RECOVERABLE_NOT_DEFINED, OVERWRITTEN, false, MAIN_SNIPPET)));
        assertTypeDeclSnippet(c3, "A", RECOVERABLE_NOT_DEFINED, ENUM_SUBKIND, 1, 0);
        TypeDeclSnippet c4 = classKey(assertEval("interface A extends B {}",
                ste(MAIN_SNIPPET, RECOVERABLE_NOT_DEFINED, RECOVERABLE_NOT_DEFINED, false, null),
                ste(c3, RECOVERABLE_NOT_DEFINED, OVERWRITTEN, false, MAIN_SNIPPET)));
        assertTypeDeclSnippet(c4, "A", RECOVERABLE_NOT_DEFINED, INTERFACE_SUBKIND, 1, 0);
        TypeDeclSnippet c5 = classKey(assertEval("class A { void f(B b) {} }",
                ste(MAIN_SNIPPET, RECOVERABLE_NOT_DEFINED, RECOVERABLE_NOT_DEFINED, false, null),
                ste(c4, RECOVERABLE_NOT_DEFINED, OVERWRITTEN, false, MAIN_SNIPPET)));
        assertTypeDeclSnippet(c5, "A", RECOVERABLE_NOT_DEFINED, CLASS_SUBKIND, 1, 0);
    }

    @Test
    public void testSignature2() {
        TypeDeclSnippet c1 = (TypeDeclSnippet) assertDeclareFail("class A { void f() { return g(); } }", "compiler.err.prob.found.req");
        assertTypeDeclSnippet(c1, "A", REJECTED, CLASS_SUBKIND, 0, 2);
        TypeDeclSnippet c2 = classKey(assertEval("class A { int f() { return g(); } }",
                ste(c1, NONEXISTENT, RECOVERABLE_DEFINED, true, null)));
        assertTypeDeclSnippet(c2, "A", RECOVERABLE_DEFINED, CLASS_SUBKIND, 1, 0);
        assertDrop(c2,
                ste(c2, RECOVERABLE_DEFINED, DROPPED, true, null));
    }

    @Test
    public void classDeclaration() {
        assertEval("class A { }");
        assertClasses(clazz(KullaTesting.ClassType.CLASS, "A"));
    }


    @Test
    public void interfaceDeclaration() {
        assertEval("interface A { }");
        assertClasses(clazz(KullaTesting.ClassType.INTERFACE, "A"));
    }

    @Test
    public void annotationDeclaration() {
        assertEval("@interface A { }");
        assertClasses(clazz(KullaTesting.ClassType.ANNOTATION, "A"));
    }

    @Test
    public void enumDeclaration() {
        assertEval("enum A { }");
        assertClasses(clazz(KullaTesting.ClassType.ENUM, "A"));
    }

    @Test
    public void classesDeclaration() {
        assertEval("interface A { }");
        assertEval("class B implements A { }");
        assertEval("interface C extends A { }");
        assertEval("enum D implements C { }");
        assertEval("@interface E { }");
        assertClasses(
                clazz(KullaTesting.ClassType.INTERFACE, "A"),
                clazz(KullaTesting.ClassType.CLASS, "B"),
                clazz(KullaTesting.ClassType.INTERFACE, "C"),
                clazz(KullaTesting.ClassType.ENUM, "D"),
                clazz(KullaTesting.ClassType.ANNOTATION, "E"));
        assertActiveKeys();
    }

    @Test
    public void classesRedeclaration1() {
        Snippet a = classKey(assertEval("class A { }"));
        Snippet b = classKey(assertEval("interface B { }"));
        assertClasses(clazz(KullaTesting.ClassType.CLASS, "A"), clazz(KullaTesting.ClassType.INTERFACE, "B"));
        assertActiveKeys();

        assertEval("interface A { }",
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(a, VALID, OVERWRITTEN, false, MAIN_SNIPPET));
        assertClasses(clazz(KullaTesting.ClassType.INTERFACE, "A"),
                clazz(KullaTesting.ClassType.INTERFACE, "B"));
        assertActiveKeys();

        assertEval("interface B { } //again",
                ste(MAIN_SNIPPET, VALID, VALID, false, null),
                ste(b, VALID, OVERWRITTEN, false, MAIN_SNIPPET));
        assertClasses(clazz(KullaTesting.ClassType.INTERFACE, "A"),
                clazz(KullaTesting.ClassType.INTERFACE, "B"));
        assertActiveKeys();
    }

    @Test
    public void classesRedeclaration2() {
        assertEval("class A { }");
        assertClasses(clazz(KullaTesting.ClassType.CLASS, "A"));
        assertActiveKeys();

        Snippet b = classKey(assertEval("class B extends A { }"));
        assertClasses(clazz(KullaTesting.ClassType.CLASS, "A"),
                clazz(KullaTesting.ClassType.CLASS, "B"));
        assertActiveKeys();

        Snippet c = classKey(assertEval("class C extends B { }"));
        assertClasses(clazz(KullaTesting.ClassType.CLASS, "A"),
                clazz(KullaTesting.ClassType.CLASS, "B"), clazz(KullaTesting.ClassType.CLASS, "C"));
        assertActiveKeys();

        assertEval("interface B { }",
                DiagCheck.DIAG_OK,
                DiagCheck.DIAG_ERROR,
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(b, VALID, OVERWRITTEN, false, MAIN_SNIPPET),
                ste(c, VALID, RECOVERABLE_NOT_DEFINED, true, MAIN_SNIPPET));
        assertClasses(clazz(KullaTesting.ClassType.CLASS, "A"),
                clazz(KullaTesting.ClassType.INTERFACE, "B"), clazz(KullaTesting.ClassType.CLASS, "C"));
        assertEval("new C();",
                DiagCheck.DIAG_ERROR,
                DiagCheck.DIAG_ERROR,
                added(REJECTED));
        assertActiveKeys();
    }

    //8154496: test3 update: sig change should false
    @Test
    public void classesRedeclaration3() {
        Snippet a = classKey(assertEval("class A { }"));
        assertClasses(clazz(KullaTesting.ClassType.CLASS, "A"));
        assertActiveKeys();

        Snippet test1 = methodKey(assertEval("A test() { return null; }"));
        Snippet test2 = methodKey(assertEval("void test(A a) { }"));
        Snippet test3 = methodKey(assertEval("void test(int n) {A a;}"));
        assertActiveKeys();

        assertEval("interface A { }",
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(test1, VALID, VALID, true, MAIN_SNIPPET),
                ste(test2, VALID, VALID, true, MAIN_SNIPPET),
                ste(test3, VALID, VALID, true, MAIN_SNIPPET),
                ste(a, VALID, OVERWRITTEN, false, MAIN_SNIPPET));
        assertClasses(clazz(KullaTesting.ClassType.INTERFACE, "A"));
        assertMethods(method("()A", "test"), method("(A)void", "test"), method("(int)void", "test"));
        assertActiveKeys();
    }

    @Test
    public void classesCyclic1() {
        Snippet b = classKey(assertEval("class B extends A { }",
                added(RECOVERABLE_NOT_DEFINED)));
        Snippet a = classKey(assertEval("class A extends B { }", DiagCheck.DIAG_IGNORE, DiagCheck.DIAG_IGNORE,
                added(REJECTED)));
        /***
        assertDeclareFail("class A extends B { }", "****",
                added(REJECTED),
                ste(b, RECOVERABLE_NOT_DEFINED, RECOVERABLE_NOT_DEFINED, false, MAIN_SNIPPET));
        ***/
        // It is random which one it shows up in, but cyclic error should be there
        List<Diag> diagsA = getState().diagnostics(a).collect(toList());
        List<Diag> diagsB = getState().diagnostics(b).collect(toList());
        List<Diag> diags;
        if (diagsA.isEmpty()) {
            diags = diagsB;
        } else {
            diags = diagsA;
            assertTrue(diagsB.isEmpty());
        }
        assertEquals(1, diags.size(), "Expected one error");
        assertEquals("compiler.err.cyclic.inheritance", diags.get(0).getCode(), "Expected cyclic inheritance error");
        assertActiveKeys();
    }

    @Test
    public void classesCyclic2() {
        Snippet d = classKey(assertEval("class D extends E { }", added(RECOVERABLE_NOT_DEFINED)));
        assertEval("class E { D d; }",
                added(VALID),
                ste(d, RECOVERABLE_NOT_DEFINED, VALID, true, MAIN_SNIPPET));
        assertActiveKeys();
    }

    @Test
    public void classesCyclic3() {
        Snippet outer = classKey(assertEval("class Outer { class Inner extends Foo { } }",
                added(RECOVERABLE_NOT_DEFINED)));
        Snippet foo = classKey(assertEval("class Foo { } ",
                added(VALID),
                ste(outer, RECOVERABLE_NOT_DEFINED, VALID, true, MAIN_SNIPPET)));
        assertEval(" class Foo extends Outer { }",
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(foo, VALID, OVERWRITTEN, false, MAIN_SNIPPET),
                ste(outer, VALID, VALID, true, MAIN_SNIPPET));
        assertActiveKeys();
    }

    @Test
    public void classesIgnoredModifiers() {
        assertEval("public interface A { }");
        assertEval("static class B implements A { }");
        assertEval("static interface C extends A { }");
        assertActiveKeys();
    }

    @Test
    public void classesIgnoredModifiersAnnotation() {
        assertEval("public @interface X { }");
        assertEval("@X public interface A { }");
        assertEval("@X static class B implements A { }");
        assertEval("@X static interface C extends A { }");
        assertActiveKeys();
    }

    @Test
    public void classesIgnoredModifiersOtherModifiers() {
        assertEval("strictfp public interface A { }");
        assertEval("strictfp static class B implements A { }");
        assertEval("strictfp static interface C extends A { }");
        assertActiveKeys();
    }

    @Test
    public void ignoreModifierSpaceIssue() {
        assertEval("interface I { void f(); } ");
        // there should not be a space between 'I' and '{' to reproduce the failure
        assertEval("class C implements I{ public void f() {}}");
        assertClasses(clazz(KullaTesting.ClassType.CLASS, "C"), clazz(KullaTesting.ClassType.INTERFACE, "I"));
        assertActiveKeys();
    }

    public Object[][] innerClasses() {
        List<Object[]> list = new ArrayList<>();
        for (ClassType outerClassType : ClassType.values()) {
            for (ClassType innerClassType : ClassType.values()) {
                list.add(new Object[]{outerClassType, innerClassType});
            }
        }
        return list.toArray(new Object[list.size()][]);
    }

    @ParameterizedTest
    @MethodSource("innerClasses")
    public void innerClasses(ClassType outerClassType, ClassType innerClassType) {
        String source =
                outerClassType + " A {" + (outerClassType == ClassType.ENUM ? ";" : "") +
                innerClassType + " B { }" +
                "}";
        assertEval(source);
        assertNumberOfActiveClasses(1);
        assertActiveKeys();
    }

    @Test
    public void testInnerClassesCrash() {
        Snippet a = classKey(assertEval("class A { class B extends A {} }"));
        Snippet a2 = classKey(assertEval("class A { interface I1 extends I2 {} interface I2 {} }",
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(a, VALID, OVERWRITTEN, false, MAIN_SNIPPET)));
        assertEval("class A { A a = new A() {}; }",
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(a2, VALID, OVERWRITTEN, false, MAIN_SNIPPET));
    }

    @Test
    public void testInnerClassesCrash1() {
        assertEval("class A { class B extends A {} B getB() { return new B();} }");
        assertEquals("a", varKey(assertEval("A a = new A();")).name());
        VarSnippet variableKey = varKey(assertEval("a.getB();"));
        assertEquals("A.B", variableKey.typeName());
    }

    @Test
    public void testInnerClassesCrash2() {
        assertEval("class A { interface I1 extends I2 {} interface I2 {} I1 x; }");
        assertEquals("a", varKey(assertEval("A a = new A();")).name());
        VarSnippet variableKey = varKey(assertEval("a.x;"));
        assertEquals("A.I1", variableKey.typeName());
    }

    @Test
    public void testCircular() {
        assertEval("import java.util.function.Supplier;");
        TypeDeclSnippet aClass =
                classKey(assertEval("public class A<T> {\n" +
                                    "  private class SomeClass {}\n" +
                                    "  public Supplier<T> m() {\n" +
                                    "    return new B<>(this);\n" +
                                    "  }\n" +
                                    "}",
                                   added(RECOVERABLE_DEFINED)));
        assertEval("public class B<T> implements Supplier<T> {\n" +
                   "  public B(A<T> a) {}\n" +
                   "  public T get() {return null;}\n" +
                   "}",
                   added(VALID),
                   ste(aClass, Status.RECOVERABLE_DEFINED, Status.VALID, false, null));
        assertEval("new A()");
    }

    @Test
    public void testCircular8282160() {
        TypeDeclSnippet classKey = classKey(assertEval("""
                                                       class B {
                                                           C c;
                                                           public void run() {}
                                                       }
                                                       """,
                                                       added(RECOVERABLE_NOT_DEFINED)));
        assertEval("""
                   class C extends B {
                       @Override
                       public void run() {}
                   }
                   """,
                   added(VALID),
                   ste(classKey, Status.RECOVERABLE_NOT_DEFINED, Status.VALID, true, null));
    }

    @Test
    public void testDefaultMethodInInterface() {
        assertEvalFail("""
                       interface C {
                           public void run() {
                               try {
                                   throw IllegalStateException();
                               } catch (Throwable t) {
                                   throw new RuntimeException(t);
                               }
                           }
                       }
                       """);
    }

    @Test
    public void testNonSealed() {
        assertAnalyze("non-sealed class C extends B {}int i;",
                      "non-sealed class C extends B {}",
                      "int i;",
                      true);
    }

}
