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
 * @bug 8141092
 * @summary Test Completion
 * @library /tools/lib
 * @build KullaTesting TestingInputStream ToolBox Compiler
 * @run testng CompletionSuggestionTest
 */

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;

import jdk.jshell.Snippet;
import org.testng.annotations.Test;

import static jdk.jshell.Snippet.Status.VALID;
import static jdk.jshell.Snippet.Status.OVERWRITTEN;

@Test
public class CompletionSuggestionTest extends KullaTesting {

    private final Compiler compiler = new Compiler();
    private final Path outDir = Paths.get("completion_suggestion_test");

    public void testMemberExpr() {
        assertEval("class Test { static void test() { } }");
        assertCompletion("Test.t|", "test()");
        assertEval("Test ccTestInstance = new Test();");
        assertCompletion("ccTestInstance.t|", "toString()");
        assertCompletion(" ccTe|", "ccTestInstance");
        assertCompletion("String value = ccTestInstance.to|", "toString()");
        assertCompletion("java.util.Coll|", "Collection", "Collections");
        assertCompletion("String.cla|", "class");
        assertCompletion("boolean.cla|", "class");
        assertCompletion("byte.cla|", "class");
        assertCompletion("short.cla|", "class");
        assertCompletion("char.cla|", "class");
        assertCompletion("int.cla|", "class");
        assertCompletion("float.cla|", "class");
        assertCompletion("long.cla|", "class");
        assertCompletion("double.cla|", "class");
        assertCompletion("void.cla|", "class");
        assertCompletion("Object[].|", "class");
        assertCompletion("int[].|", "class");
        assertEval("Object[] ao = null;");
        assertCompletion("int i = ao.|", "length");
        assertEval("int[] ai = null;");
        assertCompletion("int i = ai.|", "length");
        assertCompletionIncludesExcludes("\"\".|",
                new HashSet<>(Collections.emptyList()),
                new HashSet<>(Arrays.asList("String(")));
        assertEval("double d = 0;");
        assertEval("void m() {}");
        assertCompletionIncludesExcludes("d.|",
                new HashSet<>(Collections.emptyList()),
                new HashSet<>(Arrays.asList("class")));
        assertCompletionIncludesExcludes("m().|",
                new HashSet<>(Collections.emptyList()),
                new HashSet<>(Arrays.asList("class")));
        assertEval("class C {class D {} static class E {} enum F {} interface H {} void method() {} int number;}");
        assertCompletionIncludesExcludes("C.|",
                new HashSet<>(Arrays.asList("D", "E", "F", "H", "class")),
                new HashSet<>(Arrays.asList("method()", "number")));
        assertCompletionIncludesExcludes("new C().|",
                new HashSet<>(Arrays.asList("method()", "number")),
                new HashSet<>(Arrays.asList("D", "E", "F", "H", "class")));
        assertCompletionIncludesExcludes("new C() {}.|",
                new HashSet<>(Arrays.asList("method()", "number")),
                new HashSet<>(Arrays.asList("D", "E", "F", "H", "class")));
    }

    public void testStartOfExpression() {
        assertEval("int ccTest = 0;");
        assertCompletion("System.err.println(cc|", "ccTest");
        assertCompletion("for (int i = cc|", "ccTest");
    }

    public void testParameter() {
        assertCompletion("class C{void method(int num){num|", "num");
    }

    public void testPrimitive() {
        Set<String> primitives = new HashSet<>(Arrays.asList("boolean", "char", "byte", "short", "int", "long", "float", "double"));
        Set<String> onlyVoid = new HashSet<>(Collections.singletonList("void"));
        Set<String> primitivesOrVoid = new HashSet<>(primitives);
        primitivesOrVoid.addAll(onlyVoid);

        assertCompletionIncludesExcludes("|",
                primitivesOrVoid,
                new HashSet<>(Collections.emptyList()));
        assertCompletionIncludesExcludes("int num = |",
                primitivesOrVoid,
                new HashSet<>(Collections.emptyList()));
        assertCompletionIncludesExcludes("num = |",
                primitivesOrVoid,
                new HashSet<>(Collections.emptyList()));
        assertCompletionIncludesExcludes("class C{void m() {|",
                primitivesOrVoid,
                new HashSet<>(Collections.emptyList()));
        assertCompletionIncludesExcludes("void method(|",
                primitives,
                onlyVoid);
        assertCompletionIncludesExcludes("void method(int num, |",
                primitives,
                onlyVoid);
        assertCompletion("new java.util.ArrayList<doub|");
        assertCompletion("class A extends doubl|");
        assertCompletion("class A implements doubl|");
        assertCompletion("interface A extends doubl|");
        assertCompletion("enum A implements doubl|");
        assertCompletion("class A<T extends doubl|");
    }

    public void testEmpty() {
        assertCompletionIncludesExcludes("|",
                new HashSet<>(Arrays.asList("Object", "Void")),
                new HashSet<>(Arrays.asList("$REPL00DOESNOTMATTER")));
        assertCompletionIncludesExcludes("V|",
                new HashSet<>(Collections.singletonList("Void")),
                new HashSet<>(Collections.singletonList("Object")));
        assertCompletionIncludesExcludes("{ |",
                new HashSet<>(Arrays.asList("Object", "Void")),
                new HashSet<>(Arrays.asList("$REPL00DOESNOTMATTER")));
    }

    public void testSmartCompletion() {
        assertEval("int ccTest1 = 0;");
        assertEval("int ccTest2 = 0;");
        assertEval("String ccTest3 = null;");
        assertEval("void method(int i, String str) { }");
        assertEval("void method(String str, int i) { }");
        assertEval("java.util.List<String> list = null;");
        assertCompletion("int ccTest4 = |", true, "ccTest1", "ccTest2");
        assertCompletion("ccTest2 = |", true, "ccTest1", "ccTest2");
        assertCompletion("int ccTest4 = ccTe|", "ccTest1", "ccTest2", "ccTest3");
        assertCompletion("int ccTest4 = ccTest3.len|", true, "length()");
        assertCompletion("method(|", true, "ccTest1", "ccTest2", "ccTest3");
        assertCompletion("method(0, |", true, "ccTest3");
        assertCompletion("list.add(|", true, "ccTest1", "ccTest2", "ccTest3");
        assertCompletion("list.add(0, |", true, "ccTest3");
        assertCompletion("new String(|", true, "ccTest3");
        assertCompletion("new String(new char[0], |", true, "ccTest1", "ccTest2");
        assertCompletionIncludesExcludes("new jav|", new HashSet<>(Arrays.asList("java", "javax")), Collections.emptySet());
        assertCompletion("Class<String> clazz = String.c|", true, "class");

        Snippet klass = classKey(assertEval("class Klass {void method(int n) {} private void method(String str) {}}"));
        assertCompletion("new Klass().method(|", true, "ccTest1", "ccTest2");
        Snippet klass2 = classKey(assertEval("class Klass {static void method(int n) {} void method(String str) {}}",
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(klass, VALID, OVERWRITTEN, false, MAIN_SNIPPET)));
        assertCompletion("Klass.method(|", true, "ccTest1", "ccTest2");
        assertEval("class Klass {Klass(int n) {} private Klass(String str) {}}",
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(klass2, VALID, OVERWRITTEN, false, MAIN_SNIPPET));
        assertCompletion("new Klass(|", true, "ccTest1", "ccTest2");
    }

    public void testSmartCompletionInOverriddenMethodInvocation() {
        assertEval("int ccTest1 = 0;");
        assertEval("int ccTest2 = 0;");
        assertEval("String ccTest3 = null;");
        assertCompletion("\"\".wait(|", true, "ccTest1", "ccTest2");
        assertEval("class Base {void method(int n) {}}");
        assertEval("class Extend extends Base {}");
        assertCompletion("new Extend().method(|", true, "ccTest1", "ccTest2");
    }

    public void testSmartCompletionForBoxedType() {
        assertEval("int ccTest1 = 0;");
        assertEval("Integer ccTest2 = 0;");
        assertEval("Object ccTest3 = null;");
        assertEval("int method1(int n) {return n;}");
        assertEval("Integer method2(Integer n) {return n;}");
        assertEval("Object method3(Object o) {return o;}");
        assertCompletion("int ccTest4 = |", true, "ccTest1", "ccTest2", "method1(", "method2(");
        assertCompletion("Integer ccTest4 = |", true, "ccTest1", "ccTest2", "method1(", "method2(");
        assertCompletion("Object ccTest4 = |", true, "ccTest1", "ccTest2", "ccTest3", "method1(", "method2(", "method3(");
        assertCompletion("method1(|", true, "ccTest1", "ccTest2", "method1(", "method2(");
        assertCompletion("method2(|", true, "ccTest1", "ccTest2", "method1(", "method2(");
        assertCompletion("method3(|", true, "ccTest1", "ccTest2", "ccTest3", "method1(", "method2(", "method3(");
    }

    public void testNewClass() {
        assertCompletion("String str = new Strin|", "String(", "StringBuffer(", "StringBuilder(", "StringIndexOutOfBoundsException(");
        assertCompletion("String str = new java.lang.Strin|", "String(", "StringBuffer(", "StringBuilder(", "StringIndexOutOfBoundsException(");
        assertCompletion("String str = new |", true, "String(");
        assertCompletion("String str = new java.lang.|", true, "String(");
        assertCompletion("throw new Strin|", true, "StringIndexOutOfBoundsException(");

        assertEval("class A{class B{} class C {C(int n) {}} static class D {} interface I {}}");
        assertEval("A a;");
        assertCompletion("new A().new |", "B()", "C(");
        assertCompletion("a.new |", "B()", "C(");
        assertCompletion("new A.|", "D()");

        assertEval("enum E{; class A {}}");
        assertEval("interface I{; class A {}}");
        assertCompletion("new E.|", "A()");
        assertCompletion("new I.|", "A()");
        assertCompletion("new String(I.A|", "A");
    }

    public void testFullyQualified() {
        assertCompletion("Optional<String> opt = java.u|", "util");
        assertCompletionIncludesExcludes("Optional<Strings> opt = java.util.O|", new HashSet<>(Collections.singletonList("Optional")), Collections.emptySet());

        assertEval("void method(java.util.Optional<String> opt) {}");
        assertCompletion("method(java.u|", "util");

        assertCompletion("Object.notElement.|");
        assertCompletion("Object o = com.su|", "sun");

        Path p1 = outDir.resolve("dir1");
        compiler.compile(p1,
                "package p1.p2;\n" +
                "public class Test {\n" +
                "}",
                "package p1.p3;\n" +
                "public class Test {\n" +
                "}");
        String jarName = "test.jar";
        compiler.jar(p1, jarName, "p1/p2/Test.class", "p1/p3/Test.class");
        addToClasspath(compiler.getPath(p1.resolve(jarName)));

        assertCompletionIncludesExcludes("|", new HashSet<>(Collections.singletonList("p1")), Collections.emptySet());
        assertCompletion("p1.|", "p2", "p3");
        assertCompletion("p1.p2.|", "Test");
        assertCompletion("p1.p3.|", "Test");
    }

    public void testCheckAccessibility() {
        assertCompletion("java.util.regex.Pattern.co|", "compile(");
    }

    public void testCompletePackages() {
        assertCompletion("java.u|", "util");
        assertCompletionIncludesExcludes("jav|", new HashSet<>(Arrays.asList("java", "javax")), Collections.emptySet());
    }

    public void testImports() {
        assertCompletion("import java.u|", "util");
        assertCompletionIncludesExcludes("import jav|", new HashSet<>(Arrays.asList("java", "javax")), Collections.emptySet());
        assertCompletion("import static java.u|", "util");
        assertCompletionIncludesExcludes("import static jav|", new HashSet<>(Arrays.asList("java", "javax")), Collections.emptySet());
        assertCompletion("import static java.lang.Boolean.g|", "getBoolean");
        assertCompletion("import java.util.*|");
        assertCompletionIncludesExcludes("import java.lang.String.|",
                Collections.emptySet(),
                new HashSet<>(Arrays.asList("CASE_INSENSITIVE_ORDER", "copyValueOf", "format", "join", "valueOf", "class", "length")));
        assertCompletionIncludesExcludes("import static java.lang.String.|",
                new HashSet<>(Arrays.asList("CASE_INSENSITIVE_ORDER", "copyValueOf", "format", "join", "valueOf")),
                new HashSet<>(Arrays.asList("class", "length")));
        assertCompletionIncludesExcludes("import java.util.Map.|",
                new HashSet<>(Arrays.asList("Entry")),
                new HashSet<>(Arrays.asList("class")));
    }

    public void testBrokenClassFile() throws Exception {
        Compiler compiler = new Compiler();
        Path testOutDir = Paths.get("CompletionTestBrokenClassFile");
        String input = "package test.inner; public class Test {}";
        compiler.compile(testOutDir, input);
        addToClasspath(compiler.getPath(testOutDir).resolve("test"));
        assertCompletion("import inner.|");
    }

    public void testDocumentation() {
        assertDocumentation("System.getProperty(|",
                "java.lang.System.getProperty(java.lang.String arg0)",
                "java.lang.System.getProperty(java.lang.String arg0, java.lang.String arg1)");
        assertEval("char[] chars = null;");
        assertDocumentation("new String(chars, |",
                "java.lang.String(char[] arg0, int arg1, int arg2)");
        assertDocumentation("String.format(|",
                "java.lang.String.format(java.lang.String arg0, java.lang.Object... arg1)",
                "java.lang.String.format(java.util.Locale arg0, java.lang.String arg1, java.lang.Object... arg2)");
        assertDocumentation("\"\".getBytes(\"\"|", "java.lang.String.getBytes(int arg0, int arg1, byte[] arg2, int arg3)",
                                                    "java.lang.String.getBytes(java.lang.String arg0)",
                                                    "java.lang.String.getBytes(java.nio.charset.Charset arg0)");
        assertDocumentation("\"\".getBytes(\"\" |", "java.lang.String.getBytes(int arg0, int arg1, byte[] arg2, int arg3)",
                                                     "java.lang.String.getBytes(java.lang.String arg0)",
                                                     "java.lang.String.getBytes(java.nio.charset.Charset arg0)");
    }

    public void testMethodsWithNoArguments() {
        assertDocumentation("System.out.println(|",
                "java.io.PrintStream.println()",
                "java.io.PrintStream.println(boolean arg0)",
                "java.io.PrintStream.println(char arg0)",
                "java.io.PrintStream.println(int arg0)",
                "java.io.PrintStream.println(long arg0)",
                "java.io.PrintStream.println(float arg0)",
                "java.io.PrintStream.println(double arg0)",
                "java.io.PrintStream.println(char[] arg0)",
                "java.io.PrintStream.println(java.lang.String arg0)",
                "java.io.PrintStream.println(java.lang.Object arg0)");
    }

    public void testErroneous() {
        assertCompletion("Undefined.|");
    }

    public void testClinit() {
        assertEval("enum E{;}");
        assertEval("class C{static{}}");
        assertCompletionIncludesExcludes("E.|", Collections.emptySet(), new HashSet<>(Collections.singletonList("<clinit>")));
        assertCompletionIncludesExcludes("C.|", Collections.emptySet(), new HashSet<>(Collections.singletonList("<clinit>")));
    }

    public void testMethodHeaderContext() {
        assertCompletion("private void f(Runn|", "Runnable");
        assertCompletion("void f(Runn|", "Runnable");
        assertCompletion("void f(Object o1, Runn|", "Runnable");
        assertCompletion("void f(Object o1) throws Num|", true, "NumberFormatException");
        assertCompletion("void f(Object o1) throws java.lang.Num|", true, "NumberFormatException");
        assertEval("class HogeHoge {static class HogeHogeException extends Exception {}}");
        assertCompletion("void f(Object o1) throws Hoge|", "HogeHoge");
        assertCompletion("void f(Object o1) throws HogeHoge.|", true, "HogeHogeException");
    }

    public void testTypeVariables() {
        assertCompletion("class A<TYPE> { public void test() { TY|", "TYPE");
        assertCompletion("class A<TYPE> { public static void test() { TY|");
        assertCompletion("class A<TYPE> { public <TYPE> void test() { TY|", "TYPE");
        assertCompletion("class A<TYPE> { public static <TYPE> void test() { TY|", "TYPE");
    }

    public void testGeneric() {
        assertEval("import java.util.concurrent.*;");
        assertCompletion("java.util.List<Integ|", "Integer");
        assertCompletion("class A<TYPE extends Call|", "Callable");
        assertCompletion("class A<TYPE extends Callable<TY|", "TYPE");
        assertCompletion("<TYPE> void f(TY|", "TYPE");
        assertCompletion("class A<TYPE extends Callable<? sup|", "super");
        assertCompletion("class A<TYPE extends Callable<? super TY|", "TYPE");
    }

    public void testFields() {
        assertEval("interface Interface { int field = 0; }");
        Snippet clazz = classKey(assertEval("class Clazz {" +
                "static int staticField = 0;" +
                "int field = 0;" +
                " }"));
        assertCompletion("Interface.fiel|", "field");
        assertCompletion("Clazz.staticFiel|", "staticField");
        assertCompletion("new Interface() {}.fiel|");
        assertCompletion("new Clazz().staticFiel|");
        assertCompletion("new Clazz().fiel|", "field");
        assertCompletion("new Clazz() {}.fiel|", "field");
        assertEval("class Clazz implements Interface {}",
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(clazz, VALID, OVERWRITTEN, false, MAIN_SNIPPET));
        assertCompletion("Clazz.fiel|", "field");
        assertCompletion("new Clazz().fiel|");
        assertCompletion("new Clazz() {}.fiel|");
    }

    public void testMethods() {
        assertEval("interface Interface {" +
                "default int defaultMethod() { return 0; }" +
                "static int staticMethod() { return 0; }" +
                "}");
        Snippet clazz = classKey(assertEval("class Clazz {" +
                "static int staticMethod() { return 0; }" +
                "int method() { return 0; }" +
                "}"));
        assertCompletion("Interface.staticMeth|", "staticMethod()");
        assertCompletion("Clazz.staticMeth|", "staticMethod()");
        assertCompletion("new Interface() {}.defaultMe||", "defaultMethod()");
        assertCompletion("new Clazz().staticMeth|");
        assertCompletion("new Clazz().meth|", "method()");
        assertEval("class Clazz implements Interface {}",
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(clazz, VALID, OVERWRITTEN, false, MAIN_SNIPPET));
        assertCompletion("Clazz.staticMeth|");
        assertCompletion("new Clazz() {}.defaultM|", "defaultMethod()");
    }

    @Test(enabled = false) // TODO 8129422
    public void testUncompletedDeclaration() {
        assertCompletion("class Clazz { Claz|", "Clazz");
        assertCompletion("class Clazz { class A extends Claz|", "Clazz");
        assertCompletion("class Clazz { Clazz clazz; Object o = cla|", "clazz");
        assertCompletion("class Clazz { static Clazz clazz; Object o = cla|", "clazz");
        assertCompletion("class Clazz { Clazz clazz; static Object o = cla|", true);
        assertCompletion("class Clazz { void method(Claz|", "Clazz");
        assertCompletion("class A { int method() { return 0; } int a = meth|", "method");
        assertCompletion("class A { int field = 0; int method() { return fiel|", "field");
        assertCompletion("class A { static int method() { return 0; } int a = meth|", "method");
        assertCompletion("class A { static int field = 0; int method() { return fiel|", "field");
        assertCompletion("class A { int method() { return 0; } static int a = meth|", true);
        assertCompletion("class A { int field = 0; static int method() { return fiel|", true);
    }

    @Test(enabled = false) // TODO 8129421
    public void testClassDeclaration() {
        assertEval("interface Interface {}");
        assertCompletion("interface A extends Interf|", "Interface");
        assertCompletion("class A implements Interf|", "Interface");
        assertEval("class Clazz {}");
        assertCompletion("class A extends Claz|", "Clazz");
        assertCompletion("class A extends Clazz implements Interf|", "Interface");
        assertEval("interface Interface1 {}");
        assertCompletion("class A extends Clazz implements Interface, Interf|", "Interface", "Interface1");
        assertCompletion("interface A implements Claz|");
        assertCompletion("interface A implements Inter|");
        assertCompletion("class A implements Claz|", true);
        assertCompletion("class A extends Clazz implements Interface, Interf|", true, "Interface1");
    }

    public void testDocumentationOfUserDefinedMethods() {
        assertEval("void f() {}");
        assertDocumentation("f(|", "f()");
        assertEval("void f(int a) {}");
        assertDocumentation("f(|", "f()", "f(int arg0)");
        assertEval("<T> void f(T... a) {}", DiagCheck.DIAG_WARNING, DiagCheck.DIAG_OK);
        assertDocumentation("f(|", "f()", "f(int arg0)", "f(T... arg0)");
        assertEval("class A {}");
        assertEval("void f(A a) {}");
        assertDocumentation("f(|", "f()", "f(int arg0)", "f(T... arg0)", "f(A arg0)");
    }

    public void testDocumentationOfUserDefinedConstructors() {
        Snippet a = classKey(assertEval("class A {}"));
        assertDocumentation("new A(|", "A()");
        Snippet a2 = classKey(assertEval("class A { A() {} A(int a) {}}",
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(a, VALID, OVERWRITTEN, false, MAIN_SNIPPET)));
        assertDocumentation("new A(|", "A()", "A(int arg0)");
        assertEval("class A<T> { A(T a) {} A(int a) {}}",
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(a2, VALID, OVERWRITTEN, false, MAIN_SNIPPET));
        assertDocumentation("new A(|", "A(T arg0)", "A(int arg0)");
    }

    public void testDocumentationOfOverriddenMethods() {
        assertDocumentation("\"\".wait(|",
            "java.lang.Object.wait(long arg0)",
            "java.lang.Object.wait(long arg0, int arg1)",
            "java.lang.Object.wait()");
        assertEval("class Base {void method() {}}");
        Snippet e = classKey(assertEval("class Extend extends Base {}"));
        assertDocumentation("new Extend().method(|", "Base.method()");
        assertEval("class Extend extends Base {void method() {}}",
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(e, VALID, OVERWRITTEN, false, MAIN_SNIPPET));
        assertDocumentation("new Extend().method(|", "Extend.method()");
    }

    public void testDocumentationOfInvisibleMethods() {
        assertDocumentation("Object.wait(|", "");
        assertDocumentation("\"\".indexOfSupplementary(|", "");
        Snippet a = classKey(assertEval("class A {void method() {}}"));
        assertDocumentation("A.method(|", "");
        assertEval("class A {private void method() {}}",
                ste(MAIN_SNIPPET, VALID, VALID, true, null),
                ste(a, VALID, OVERWRITTEN, false, MAIN_SNIPPET));
        assertDocumentation("new A().method(|", "");
    }

    public void testDocumentationOfInvisibleConstructors() {
        assertDocumentation("new Compiler(|", "");
        assertEval("class A { private A() {} }");
        assertDocumentation("new A(|", "");
    }

    public void testDocumentationWithBoxing() {
        assertEval("int primitive = 0;");
        assertEval("Integer boxed = 0;");
        assertEval("Object object = null;");
        assertEval("void method(int n, Object o) { }");
        assertEval("void method(Object n, int o) { }");
        assertDocumentation("method(primitive,|",
                "method(int arg0, java.lang.Object arg1)",
                "method(java.lang.Object arg0, int arg1)");
        assertDocumentation("method(boxed,|",
                "method(int arg0, java.lang.Object arg1)",
                "method(java.lang.Object arg0, int arg1)");
        assertDocumentation("method(object,|",
                "method(java.lang.Object arg0, int arg1)");
    }

    public void testVarArgs() {
        assertEval("int i = 0;");
        assertEval("class Foo1 { static void m(int... i) { } } ");
        assertCompletion("Foo1.m(|", true, "i");
        assertCompletion("Foo1.m(i, |", true, "i");
        assertCompletion("Foo1.m(i, i, |", true, "i");
        assertEval("class Foo2 { static void m(String s, int... i) { } } ");
        assertCompletion("Foo2.m(|", true);
        assertCompletion("Foo2.m(i, |", true);
        assertCompletion("Foo2.m(\"\", |", true, "i");
        assertCompletion("Foo2.m(\"\", i, |", true, "i");
        assertCompletion("Foo2.m(\"\", i, i, |", true, "i");
        assertEval("class Foo3 { Foo3(String s, int... i) { } } ");
        assertCompletion("new Foo3(|", true);
        assertCompletion("new Foo3(i, |", true);
        assertCompletion("new Foo3(\"\", |", true, "i");
        assertCompletion("new Foo3(\"\", i, |", true, "i");
        assertCompletion("new Foo3(\"\", i, i, |", true, "i");
        assertEval("int[] ia = null;");
        assertCompletion("Foo1.m(ia, |", true);
        assertEval("class Foo4 { static void m(int... i) { } static void m(int[] ia, String str) { } } ");
        assertEval("String str = null;");
        assertCompletion("Foo4.m(ia, |", true, "str");
    }

    public void testConstructorAsMemberOf() {
        assertEval("class Baz<X> { Baz(X x) { } } ");
        assertEval("String str = null;");
        assertEval("Integer i = null;");
        assertCompletion("new Baz(|", true, "i", "str");
        assertCompletion("new Baz<String>(|", true, "str");
        assertCompletion("Baz<String> bz = new Baz<>(|", true, "str");
        assertEval("class Foo { static void m(String str) {} static void m(Baz<String> baz) {} }");
        assertCompletion("Foo.m(new Baz<>(|", true, "str");
    }
}
