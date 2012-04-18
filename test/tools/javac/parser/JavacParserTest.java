/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7073631
 * @summary tests error and diagnostics positions
 * @author  Jan Lahoda
 */

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ErroneousTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.tree.JCTree;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

public class JavacParserTest extends TestCase {
    final JavaCompiler tool;
    public JavacParserTest(String testName) {
        tool = ToolProvider.getSystemJavaCompiler();
        System.out.println("java.home=" + System.getProperty("java.home"));
    }

    static class MyFileObject extends SimpleJavaFileObject {

        private String text;

        public MyFileObject(String text) {
            super(URI.create("myfo:/Test.java"), JavaFileObject.Kind.SOURCE);
            this.text = text;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return text;
        }
    }
    /*
     * converts Windows to Unix style LFs for comparing strings
     */
    private String normalize(String in) {
        return in.replace(System.getProperty("line.separator"), "\n");
    }

    public CompilationUnitTree getCompilationUnitTree(String code) throws IOException {

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, null, null, null,
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();
        return cut;
    }

    public List<String> getErroneousTreeValues(ErroneousTree node) {

        List<String> values = new ArrayList<>();
        if (node.getErrorTrees() != null) {
            for (Tree t : node.getErrorTrees()) {
                values.add(t.toString());
            }
        } else {
            throw new RuntimeException("ERROR: No Erroneous tree "
                    + "has been created.");
        }
        return values;
    }

    public void testPositionForSuperConstructorCalls() throws IOException {
        assert tool != null;

        String code = "package test; public class Test {public Test() {super();}}";

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, null, null, null,
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();
        SourcePositions pos = Trees.instance(ct).getSourcePositions();

        MethodTree method =
                (MethodTree) ((ClassTree) cut.getTypeDecls().get(0)).getMembers().get(0);
        ExpressionStatementTree es =
                (ExpressionStatementTree) method.getBody().getStatements().get(0);

        final int esStartPos = code.indexOf(es.toString());
        final int esEndPos = esStartPos + es.toString().length();
        assertEquals("testPositionForSuperConstructorCalls",
                esStartPos, pos.getStartPosition(cut, es));
        assertEquals("testPositionForSuperConstructorCalls",
                esEndPos, pos.getEndPosition(cut, es));

        MethodInvocationTree mit = (MethodInvocationTree) es.getExpression();

        final int mitStartPos = code.indexOf(mit.toString());
        final int mitEndPos = mitStartPos + mit.toString().length();
        assertEquals("testPositionForSuperConstructorCalls",
                mitStartPos, pos.getStartPosition(cut, mit));
        assertEquals("testPositionForSuperConstructorCalls",
                mitEndPos, pos.getEndPosition(cut, mit));

        final int methodStartPos = mitStartPos;
        final int methodEndPos = methodStartPos + mit.getMethodSelect().toString().length();
        assertEquals("testPositionForSuperConstructorCalls",
                methodStartPos, pos.getStartPosition(cut, mit.getMethodSelect()));
        assertEquals("testPositionForSuperConstructorCalls",
                methodEndPos, pos.getEndPosition(cut, mit.getMethodSelect()));

    }

    public void testPositionForEnumModifiers() throws IOException {

        String code = "package test; public enum Test {A;}";

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, null, null, null,
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();
        SourcePositions pos = Trees.instance(ct).getSourcePositions();

        ClassTree clazz = (ClassTree) cut.getTypeDecls().get(0);
        ModifiersTree mt = clazz.getModifiers();

        assertEquals("testPositionForEnumModifiers",
                38 - 24, pos.getStartPosition(cut, mt));
        assertEquals("testPositionForEnumModifiers",
                44 - 24, pos.getEndPosition(cut, mt));
    }

    public void testNewClassWithEnclosing() throws IOException {


        String code = "package test; class Test { " +
                "class d {} private void method() { " +
                "Object o = Test.this.new d(); } }";

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, null, null, null,
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();
        SourcePositions pos = Trees.instance(ct).getSourcePositions();

        ClassTree clazz = (ClassTree) cut.getTypeDecls().get(0);
        ExpressionTree est =
                ((VariableTree) ((MethodTree) clazz.getMembers().get(1)).getBody().getStatements().get(0)).getInitializer();

        assertEquals("testNewClassWithEnclosing",
                97 - 24, pos.getStartPosition(cut, est));
        assertEquals("testNewClassWithEnclosing",
                114 - 24, pos.getEndPosition(cut, est));
    }

    public void testPreferredPositionForBinaryOp() throws IOException {

        String code = "package test; public class Test {"
                + "private void test() {"
                + "Object o = null; boolean b = o != null && o instanceof String;"
                + "} private Test() {}}";

        CompilationUnitTree cut = getCompilationUnitTree(code);
        ClassTree clazz = (ClassTree) cut.getTypeDecls().get(0);
        MethodTree method = (MethodTree) clazz.getMembers().get(0);
        VariableTree condSt = (VariableTree) method.getBody().getStatements().get(1);
        BinaryTree cond = (BinaryTree) condSt.getInitializer();

        JCTree condJC = (JCTree) cond;
        int condStartPos = code.indexOf("&&");
        assertEquals("testPreferredPositionForBinaryOp",
                condStartPos, condJC.pos);
    }

    public void testPositionBrokenSource126732a() throws IOException {
        String[] commands = new String[]{
            "return Runnable()",
            "do { } while (true)",
            "throw UnsupportedOperationException()",
            "assert true",
            "1 + 1",};

        for (String command : commands) {

            String code = "package test;\n"
                    + "public class Test {\n"
                    + "    public static void test() {\n"
                    + "        " + command + " {\n"
                    + "                new Runnable() {\n"
                    + "        };\n"
                    + "    }\n"
                    + "}";
            JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, null, null,
                    null, null, Arrays.asList(new MyFileObject(code)));
            CompilationUnitTree cut = ct.parse().iterator().next();

            ClassTree clazz = (ClassTree) cut.getTypeDecls().get(0);
            MethodTree method = (MethodTree) clazz.getMembers().get(0);
            List<? extends StatementTree> statements =
                    method.getBody().getStatements();

            StatementTree ret = statements.get(0);
            StatementTree block = statements.get(1);

            Trees t = Trees.instance(ct);
            int len = code.indexOf(command + " {") + (command + " ").length();
            assertEquals(command, len,
                    t.getSourcePositions().getEndPosition(cut, ret));
            assertEquals(command, len,
                    t.getSourcePositions().getStartPosition(cut, block));
        }
    }

    public void testPositionBrokenSource126732b() throws IOException {
        String[] commands = new String[]{
            "break",
            "break A",
            "continue ",
            "continue A",};

        for (String command : commands) {

            String code = "package test;\n"
                    + "public class Test {\n"
                    + "    public static void test() {\n"
                    + "        while (true) {\n"
                    + "            " + command + " {\n"
                    + "                new Runnable() {\n"
                    + "        };\n"
                    + "        }\n"
                    + "    }\n"
                    + "}";

            JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, null, null,
                    null, null, Arrays.asList(new MyFileObject(code)));
            CompilationUnitTree cut = ct.parse().iterator().next();

            ClassTree clazz = (ClassTree) cut.getTypeDecls().get(0);
            MethodTree method = (MethodTree) clazz.getMembers().get(0);
            List<? extends StatementTree> statements =
                    ((BlockTree) ((WhileLoopTree) method.getBody().getStatements().get(0)).getStatement()).getStatements();

            StatementTree ret = statements.get(0);
            StatementTree block = statements.get(1);

            Trees t = Trees.instance(ct);
            int len = code.indexOf(command + " {") + (command + " ").length();
            assertEquals(command, len,
                    t.getSourcePositions().getEndPosition(cut, ret));
            assertEquals(command, len,
                    t.getSourcePositions().getStartPosition(cut, block));
        }
    }

    public void testErrorRecoveryForEnhancedForLoop142381() throws IOException {

        String code = "package test; class Test { " +
                "private void method() { " +
                "java.util.Set<String> s = null; for (a : s) {} } }";

        final List<Diagnostic<? extends JavaFileObject>> errors =
                new LinkedList<Diagnostic<? extends JavaFileObject>>();

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, null,
                new DiagnosticListener<JavaFileObject>() {
            public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
                errors.add(diagnostic);
            }
        }, null, null, Arrays.asList(new MyFileObject(code)));

        CompilationUnitTree cut = ct.parse().iterator().next();

        ClassTree clazz = (ClassTree) cut.getTypeDecls().get(0);
        StatementTree forStatement =
                ((MethodTree) clazz.getMembers().get(0)).getBody().getStatements().get(1);

        assertEquals("testErrorRecoveryForEnhancedForLoop142381",
                Kind.ENHANCED_FOR_LOOP, forStatement.getKind());
        assertFalse("testErrorRecoveryForEnhancedForLoop142381", errors.isEmpty());
    }

    public void testPositionAnnotationNoPackage187551() throws IOException {

        String code = "\n@interface Test {}";

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, null, null, null,
                null, Arrays.asList(new MyFileObject(code)));

        CompilationUnitTree cut = ct.parse().iterator().next();
        ClassTree clazz = (ClassTree) cut.getTypeDecls().get(0);
        Trees t = Trees.instance(ct);

        assertEquals("testPositionAnnotationNoPackage187551",
                1, t.getSourcePositions().getStartPosition(cut, clazz));
    }

    public void testPositionsSane() throws IOException {
        performPositionsSanityTest("package test; class Test { " +
                "private void method() { " +
                "java.util.List<? extends java.util.List<? extends String>> l; " +
                "} }");
        performPositionsSanityTest("package test; class Test { " +
                "private void method() { " +
                "java.util.List<? super java.util.List<? super String>> l; " +
                "} }");
        performPositionsSanityTest("package test; class Test { " +
                "private void method() { " +
                "java.util.List<? super java.util.List<?>> l; } }");
    }

    private void performPositionsSanityTest(String code) throws IOException {

        final List<Diagnostic<? extends JavaFileObject>> errors =
                new LinkedList<Diagnostic<? extends JavaFileObject>>();

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, null,
                new DiagnosticListener<JavaFileObject>() {

            public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
                errors.add(diagnostic);
            }
        }, null, null, Arrays.asList(new MyFileObject(code)));

        final CompilationUnitTree cut = ct.parse().iterator().next();
        final Trees trees = Trees.instance(ct);

        new TreeScanner<Void, Void>() {

            private long parentStart = 0;
            private long parentEnd = Integer.MAX_VALUE;

            @Override
            public Void scan(Tree node, Void p) {
                if (node == null) {
                    return null;
                }

                long start = trees.getSourcePositions().getStartPosition(cut, node);

                if (start == (-1)) {
                    return null; //synthetic tree
                }
                assertTrue(node.toString() + ":" + start + "/" + parentStart,
                        parentStart <= start);

                long prevParentStart = parentStart;

                parentStart = start;

                long end = trees.getSourcePositions().getEndPosition(cut, node);

                assertTrue(node.toString() + ":" + end + "/" + parentEnd,
                        end <= parentEnd);

                long prevParentEnd = parentEnd;

                parentEnd = end;

                super.scan(node, p);

                parentStart = prevParentStart;
                parentEnd = prevParentEnd;

                return null;
            }

            private void assertTrue(String message, boolean b) {
                if (!b) fail(message);
            }
        }.scan(cut, null);
    }

    public void testCorrectWilcardPositions() throws IOException {
        performWildcardPositionsTest("package test; import java.util.List; " +
                "class Test { private void method() { List<? extends List<? extends String>> l; } }",

                Arrays.asList("List<? extends List<? extends String>> l;",
                "List<? extends List<? extends String>>",
                "List",
                "? extends List<? extends String>",
                "List<? extends String>",
                "List",
                "? extends String",
                "String"));
        performWildcardPositionsTest("package test; import java.util.List; " +
                "class Test { private void method() { List<? super List<? super String>> l; } }",

                Arrays.asList("List<? super List<? super String>> l;",
                "List<? super List<? super String>>",
                "List",
                "? super List<? super String>",
                "List<? super String>",
                "List",
                "? super String",
                "String"));
        performWildcardPositionsTest("package test; import java.util.List; " +
                "class Test { private void method() { List<? super List<?>> l; } }",

                Arrays.asList("List<? super List<?>> l;",
                "List<? super List<?>>",
                "List",
                "? super List<?>",
                "List<?>",
                "List",
                "?"));
        performWildcardPositionsTest("package test; import java.util.List; " +
                "class Test { private void method() { " +
                "List<? extends List<? extends List<? extends String>>> l; } }",

                Arrays.asList("List<? extends List<? extends List<? extends String>>> l;",
                "List<? extends List<? extends List<? extends String>>>",
                "List",
                "? extends List<? extends List<? extends String>>",
                "List<? extends List<? extends String>>",
                "List",
                "? extends List<? extends String>",
                "List<? extends String>",
                "List",
                "? extends String",
                "String"));
        performWildcardPositionsTest("package test; import java.util.List; " +
                "class Test { private void method() { " +
                "List<? extends List<? extends List<? extends String   >>> l; } }",
                Arrays.asList("List<? extends List<? extends List<? extends String   >>> l;",
                "List<? extends List<? extends List<? extends String   >>>",
                "List",
                "? extends List<? extends List<? extends String   >>",
                "List<? extends List<? extends String   >>",
                "List",
                "? extends List<? extends String   >",
                "List<? extends String   >",
                "List",
                "? extends String",
                "String"));
    }

    public void performWildcardPositionsTest(final String code,
            List<String> golden) throws IOException {

        final List<Diagnostic<? extends JavaFileObject>> errors =
                new LinkedList<Diagnostic<? extends JavaFileObject>>();

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, null,
                new DiagnosticListener<JavaFileObject>() {
                    public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
                        errors.add(diagnostic);
                    }
                }, null, null, Arrays.asList(new MyFileObject(code)));

        final CompilationUnitTree cut = ct.parse().iterator().next();
        final List<String> content = new LinkedList<String>();
        final Trees trees = Trees.instance(ct);

        new TreeScanner<Void, Void>() {
            @Override
            public Void scan(Tree node, Void p) {
                if (node == null) {
                    return null;
                }
                long start = trees.getSourcePositions().getStartPosition(cut, node);

                if (start == (-1)) {
                    return null; //synthetic tree
                }
                long end = trees.getSourcePositions().getEndPosition(cut, node);
                String s = code.substring((int) start, (int) end);
                content.add(s);

                return super.scan(node, p);
            }
        }.scan(((MethodTree) ((ClassTree) cut.getTypeDecls().get(0)).getMembers().get(0)).getBody().getStatements().get(0), null);

        assertEquals("performWildcardPositionsTest",golden.toString(),
                content.toString());
    }

    public void testStartPositionForMethodWithoutModifiers() throws IOException {

        String code = "package t; class Test { <T> void t() {} }";

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, null, null, null,
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();
        ClassTree clazz = (ClassTree) cut.getTypeDecls().get(0);
        MethodTree mt = (MethodTree) clazz.getMembers().get(0);
        Trees t = Trees.instance(ct);
        int start = (int) t.getSourcePositions().getStartPosition(cut, mt);
        int end = (int) t.getSourcePositions().getEndPosition(cut, mt);

        assertEquals("testStartPositionForMethodWithoutModifiers",
                "<T> void t() {}", code.substring(start, end));
    }

    public void testStartPositionEnumConstantInit() throws IOException {

        String code = "package t; enum Test { AAA; }";

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, null, null, null,
                null, Arrays.asList(new MyFileObject(code)));
        CompilationUnitTree cut = ct.parse().iterator().next();
        ClassTree clazz = (ClassTree) cut.getTypeDecls().get(0);
        VariableTree enumAAA = (VariableTree) clazz.getMembers().get(0);
        Trees t = Trees.instance(ct);
        int start = (int) t.getSourcePositions().getStartPosition(cut,
                enumAAA.getInitializer());

        assertEquals("testStartPositionEnumConstantInit", -1, start);
    }

    public void testVariableInIfThen1() throws IOException {

        String code = "package t; class Test { " +
                "private static void t(String name) { " +
                "if (name != null) String nn = name.trim(); } }";

        DiagnosticCollector<JavaFileObject> coll =
                new DiagnosticCollector<JavaFileObject>();

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, null, coll, null,
                null, Arrays.asList(new MyFileObject(code)));

        ct.parse();

        List<String> codes = new LinkedList<String>();

        for (Diagnostic<? extends JavaFileObject> d : coll.getDiagnostics()) {
            codes.add(d.getCode());
        }

        assertEquals("testVariableInIfThen1",
                Arrays.<String>asList("compiler.err.variable.not.allowed"),
                codes);
    }

    public void testVariableInIfThen2() throws IOException {

        String code = "package t; class Test { " +
                "private static void t(String name) { " +
                "if (name != null) class X {} } }";
        DiagnosticCollector<JavaFileObject> coll =
                new DiagnosticCollector<JavaFileObject>();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, null, coll, null,
                null, Arrays.asList(new MyFileObject(code)));

        ct.parse();

        List<String> codes = new LinkedList<String>();

        for (Diagnostic<? extends JavaFileObject> d : coll.getDiagnostics()) {
            codes.add(d.getCode());
        }

        assertEquals("testVariableInIfThen2",
                Arrays.<String>asList("compiler.err.class.not.allowed"), codes);
    }

    public void testVariableInIfThen3() throws IOException {

        String code = "package t; class Test { "+
                "private static void t() { " +
                "if (true) abstract class F {} }}";
        DiagnosticCollector<JavaFileObject> coll =
                new DiagnosticCollector<JavaFileObject>();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, null, coll, null,
                null, Arrays.asList(new MyFileObject(code)));

        ct.parse();

        List<String> codes = new LinkedList<String>();

        for (Diagnostic<? extends JavaFileObject> d : coll.getDiagnostics()) {
            codes.add(d.getCode());
        }

        assertEquals("testVariableInIfThen3",
                Arrays.<String>asList("compiler.err.class.not.allowed"), codes);
    }

    public void testVariableInIfThen4() throws IOException {

        String code = "package t; class Test { "+
                "private static void t(String name) { " +
                "if (name != null) interface X {} } }";
        DiagnosticCollector<JavaFileObject> coll =
                new DiagnosticCollector<JavaFileObject>();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, null, coll, null,
                null, Arrays.asList(new MyFileObject(code)));

        ct.parse();

        List<String> codes = new LinkedList<String>();

        for (Diagnostic<? extends JavaFileObject> d : coll.getDiagnostics()) {
            codes.add(d.getCode());
        }

        assertEquals("testVariableInIfThen4",
                Arrays.<String>asList("compiler.err.class.not.allowed"), codes);
    }

    public void testVariableInIfThen5() throws IOException {

        String code = "package t; class Test { "+
                "private static void t() { " +
                "if (true) } }";
        DiagnosticCollector<JavaFileObject> coll =
                new DiagnosticCollector<JavaFileObject>();
        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, null, coll, null,
                null, Arrays.asList(new MyFileObject(code)));

        ct.parse();

        List<String> codes = new LinkedList<String>();

        for (Diagnostic<? extends JavaFileObject> d : coll.getDiagnostics()) {
            codes.add(d.getCode());
        }

        assertEquals("testVariableInIfThen5",
                Arrays.<String>asList("compiler.err.illegal.start.of.stmt"),
                codes);
    }

    //see javac bug #6882235, NB bug #98234:
    public void testMissingExponent() throws IOException {

        String code = "\nclass Test { { System.err.println(0e); } }";

        JavacTaskImpl ct = (JavacTaskImpl) tool.getTask(null, null, null, null,
                null, Arrays.asList(new MyFileObject(code)));

        assertNotNull(ct.parse().iterator().next());
    }

    public void testTryResourcePos() throws IOException {

        final String code = "package t; class Test { " +
                "{ try (java.io.InputStream in = null) { } } }";

        CompilationUnitTree cut = getCompilationUnitTree(code);

        new TreeScanner<Void, Void>() {
            @Override
            public Void visitVariable(VariableTree node, Void p) {
                if ("in".contentEquals(node.getName())) {
                    JCTree.JCVariableDecl var = (JCTree.JCVariableDecl) node;
                    System.out.println(node.getName() + "," + var.pos);
                    assertEquals("testTryResourcePos", "in = null) { } } }",
                            code.substring(var.pos));
                }
                return super.visitVariable(node, p);
            }
        }.scan(cut, null);
    }

    public void testVarPos() throws IOException {

        final String code = "package t; class Test { " +
                "{ java.io.InputStream in = null; } }";

        CompilationUnitTree cut = getCompilationUnitTree(code);

        new TreeScanner<Void, Void>() {

            @Override
            public Void visitVariable(VariableTree node, Void p) {
                if ("in".contentEquals(node.getName())) {
                    JCTree.JCVariableDecl var = (JCTree.JCVariableDecl) node;
                    assertEquals("testVarPos","in = null; } }",
                            code.substring(var.pos));
                }
                return super.visitVariable(node, p);
            }
        }.scan(cut, null);
    }

    // expected erroneous tree: int x = y;(ERROR);
    public void testOperatorMissingError() throws IOException {

        String code = "package test; public class ErrorTest { "
                + "void method() { int x = y  z } }";
        CompilationUnitTree cut = getCompilationUnitTree(code);
        final List<String> values = new ArrayList<>();
        final List<String> expectedValues =
                new ArrayList<>(Arrays.asList("[z]"));

        new TreeScanner<Void, Void>() {

            @Override
            public Void visitErroneous(ErroneousTree node, Void p) {

                values.add(getErroneousTreeValues(node).toString());
                return null;

            }
        }.scan(cut, null);

        assertEquals("testSwitchError: The Erroneous tree "
                + "error values: " + values
                + " do not match expected error values: "
                + expectedValues, values, expectedValues);
    }

    //expected erroneous tree:  String s = (ERROR);
    public void testMissingParenthesisError() throws IOException {

        String code = "package test; public class ErrorTest { "
                + "void f() {String s = new String; } }";
        CompilationUnitTree cut = getCompilationUnitTree(code);
        final List<String> values = new ArrayList<>();
        final List<String> expectedValues =
                new ArrayList<>(Arrays.asList("[new String()]"));

        new TreeScanner<Void, Void>() {

            @Override
            public Void visitErroneous(ErroneousTree node, Void p) {

                values.add(getErroneousTreeValues(node).toString());
                return null;
            }
        }.scan(cut, null);

        assertEquals("testSwitchError: The Erroneous tree "
                + "error values: " + values
                + " do not match expected error values: "
                + expectedValues, values, expectedValues);
    }

    //expected erroneous tree: package test; (ERROR)(ERROR)
    public void testMissingClassError() throws IOException {

        String code = "package Test; clas ErrorTest {  "
                + "void f() {String s = new String(); } }";
        CompilationUnitTree cut = getCompilationUnitTree(code);
        final List<String> values = new ArrayList<>();
        final List<String> expectedValues =
                new ArrayList<>(Arrays.asList("[, clas]", "[]"));

        new TreeScanner<Void, Void>() {

            @Override
            public Void visitErroneous(ErroneousTree node, Void p) {

                values.add(getErroneousTreeValues(node).toString());
                return null;
            }
        }.scan(cut, null);

        assertEquals("testSwitchError: The Erroneous tree "
                + "error values: " + values
                + " do not match expected error values: "
                + expectedValues, values, expectedValues);
    }

    //expected erroneous tree: void m1(int i) {(ERROR);{(ERROR);}
    public void testSwitchError() throws IOException {

        String code = "package test; public class ErrorTest { "
                + "int numDays; void m1(int i) { switchh {i} { case 1: "
                + "numDays = 31; break; } } }";
        CompilationUnitTree cut = getCompilationUnitTree(code);
        final List<String> values = new ArrayList<>();
        final List<String> expectedValues =
                new ArrayList<>(Arrays.asList("[switchh]", "[i]"));

        new TreeScanner<Void, Void>() {

            @Override
            public Void visitErroneous(ErroneousTree node, Void p) {

                values.add(getErroneousTreeValues(node).toString());
                return null;
            }
        }.scan(cut, null);

        assertEquals("testSwitchError: The Erroneous tree "
                + "error values: " + values
                + " do not match expected error values: "
                + expectedValues, values, expectedValues);
    }

    //expected erroneous tree: class ErrorTest {(ERROR)
    public void testMethodError() throws IOException {

        String code = "package Test; class ErrorTest {  "
                + "static final void f) {String s = new String(); } }";
        CompilationUnitTree cut = getCompilationUnitTree(code);
        final List<String> values = new ArrayList<>();
        final List<String> expectedValues =
                new ArrayList<>(Arrays.asList("[\nstatic final void f();]"));

        new TreeScanner<Void, Void>() {

            @Override
            public Void visitErroneous(ErroneousTree node, Void p) {

                values.add(normalize(getErroneousTreeValues(node).toString()));
                return null;
            }
        }.scan(cut, null);

        assertEquals("testMethodError: The Erroneous tree "
                + "error value: " + values
                + " does not match expected error values: "
                + expectedValues, values, expectedValues);
    }

    void testsNotWorking() throws IOException {

        // Fails with nb-javac, needs further investigation
        testPositionBrokenSource126732a();
        testPositionBrokenSource126732b();

        // Fails, these tests yet to be addressed
        testPositionForEnumModifiers();
        testStartPositionEnumConstantInit();
    }
    void testPositions() throws IOException {
        testPositionsSane();
        testCorrectWilcardPositions();
        testPositionAnnotationNoPackage187551();
        testPositionForSuperConstructorCalls();
        testPreferredPositionForBinaryOp();
        testStartPositionForMethodWithoutModifiers();
        testVarPos();
        testVariableInIfThen1();
        testVariableInIfThen2();
        testVariableInIfThen3();
        testVariableInIfThen4();
        testVariableInIfThen5();
        testMissingExponent();
        testTryResourcePos();
        testOperatorMissingError();
        testMissingParenthesisError();
        testMissingClassError();
        testSwitchError();
        testMethodError();
    }

    public static void main(String... args) throws IOException {
        JavacParserTest jpt = new JavacParserTest("JavacParserTest");
        jpt.testPositions();
        System.out.println("PASS");
    }
}

abstract class TestCase {

    void assertEquals(String message, int i, int pos) {
        if (i != pos) {
            fail(message);
        }
    }

    void assertFalse(String message, boolean empty) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    void assertEquals(String message, int i, long l) {
        if (i != l) {
            fail(message + ":" + i + ":" + l);
        }
    }

    void assertEquals(String message, Object o1, Object o2) {
        System.out.println(o1);
        System.out.println(o2);
        if (o1 != null && o2 != null && !o1.equals(o2)) {
            fail(message);
        }
        if (o1 == null && o2 != null) {
            fail(message);
        }
    }

    void assertNotNull(Object o) {
        if (o == null) {
            fail();
        }
    }

    void fail() {
        fail("test failed");
    }

    void fail(String message) {
        throw new RuntimeException(message);
    }
}
