/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @modules jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.parser
 *          jdk.compiler/com.sun.tools.javac.tree
 *          jdk.compiler/com.sun.tools.javac.util
 */

import com.sun.source.tree.CaseLabelTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ConstantCaseLabelTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.PatternCaseLabelTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.Tree.Kind;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.parser.JavacParser;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.tree.JCTree.JCEnhancedVariableDeclaration;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.main.Option;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Options;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;

public class DisambiguatePatterns {

    public static void main(String... args) throws Throwable {
        DisambiguatePatterns test = new DisambiguatePatterns();
        test.disambiguationTest("String s",
                                 ExpressionType.PATTERN);
        test.disambiguationTest("String s when s.isEmpty()",
                                 ExpressionType.PATTERN);
        test.disambiguationTest("String s",
                                 ExpressionType.PATTERN);
        test.disambiguationTest("@Ann String s",
                                 ExpressionType.PATTERN);
        test.disambiguationTest("String s",
                                 ExpressionType.PATTERN);
        test.disambiguationTest("(String) s",
                                 ExpressionType.EXPRESSION);
        test.disambiguationTest("((String) s)",
                                 ExpressionType.EXPRESSION);
        test.disambiguationTest("((0x1))",
                                 ExpressionType.EXPRESSION);
        test.disambiguationTest("(a > b)",
                                 ExpressionType.EXPRESSION);
        test.disambiguationTest("(a >> b)",
                                 ExpressionType.EXPRESSION);
        test.disambiguationTest("(a >>> b)",
                                 ExpressionType.EXPRESSION);
        test.disambiguationTest("(a < b | a > b)",
                                 ExpressionType.EXPRESSION);
        test.disambiguationTest("(a << b | a >> b)",
                                 ExpressionType.EXPRESSION);
        test.disambiguationTest("(a << b || a < b | a >>> b)",
                                 ExpressionType.EXPRESSION);
        test.disambiguationTest("a < c.d > b",
                                 ExpressionType.PATTERN);
        test.disambiguationTest("a<? extends c.d> b",
                                 ExpressionType.PATTERN);
        test.disambiguationTest("@Ann a<? extends c.d> b",
                                 ExpressionType.PATTERN);
        test.disambiguationTest("a<? extends @Ann c.d> b",
                                 ExpressionType.PATTERN);
        test.disambiguationTest("a<? super c.d> b",
                                 ExpressionType.PATTERN);
        test.disambiguationTest("a<? super @Ann c.d> b",
                                 ExpressionType.PATTERN);
        test.disambiguationTest("a<b<c.d>> b",
                                 ExpressionType.PATTERN);
        test.disambiguationTest("a<b<@Ann c.d>> b",
                                 ExpressionType.PATTERN);
        test.disambiguationTest("a<b<c<d>>> b",
                                 ExpressionType.PATTERN);
        test.disambiguationTest("a[] b",
                                 ExpressionType.PATTERN);
        test.disambiguationTest("a[][] b",
                                 ExpressionType.PATTERN);
        test.disambiguationTest("int i",
                                 ExpressionType.PATTERN);
        test.disambiguationTest("int[] i",
                                 ExpressionType.PATTERN);
        test.disambiguationTest("a[a]",
                                 ExpressionType.EXPRESSION);
        test.disambiguationTest("a[b][c]",
                                 ExpressionType.EXPRESSION);
        test.disambiguationTest("a & b",
                                 ExpressionType.EXPRESSION);
        test.disambiguationTest("R r when (x > 0)",
                                 ExpressionType.PATTERN);
        test.disambiguationTest("R(int x) when (x > 0)",
                                 ExpressionType.PATTERN);
        test.disambiguationTest("java.util.List<?>[] p",
                                 ExpressionType.PATTERN);
        test.disambiguationTest("java.util.List<?>[][] p",
                                 ExpressionType.PATTERN);
        test.disambiguationTest("a<b<c>>[] d",
                                 ExpressionType.PATTERN);
        test.disambiguationTest("java.util.List<?>[] p when true",
                                 ExpressionType.PATTERN);
        test.disambiguationTest("java.util.List<?> @Ann [] p",
                                 ExpressionType.PATTERN);
        test.disambiguationTest("a<b<c>> @Ann [] d",
                                 ExpressionType.PATTERN);
        test.disambiguationTest("(java.util.List<?>[]) o",
                                 ExpressionType.EXPRESSION);
        test.disambiguationTest("String[].class",
                                 ExpressionType.EXPRESSION);
        test.disambiguationTest("new int[1][]",
                                ExpressionType.EXPRESSION);
        test.disambiguationTest("new java.util.List<?>[1][]",
                                ExpressionType.EXPRESSION);

        // Local Variable Declaration or Enhanced Local Variable Declaration in the header of the enhanced-for?
        test.forDisambiguationTest("T[] a",
                                 ForType.ENHANCED_FOR_WITH_LVDS);
        test.forDisambiguationTest("@Annot(field = \"test\") Point p",
                                 ForType.ENHANCED_FOR_WITH_LVDS);
        test.forDisambiguationTest("R(T[] a)",
                                 ForType.ENHANCED_FOR_WITH_PATTERNS_WITH_ELVDS);
        test.forDisambiguationTest("Point(Integer a, Integer b)",
                                 ForType.ENHANCED_FOR_WITH_PATTERNS_WITH_ELVDS);
        test.forDisambiguationTest("ForEachPatterns.Point(Integer a, Integer b)",
                                 ForType.ENHANCED_FOR_WITH_PATTERNS_WITH_ELVDS);
        test.forDisambiguationTest("GPoint<Integer>(Integer a, Integer b)",
                                 ForType.ENHANCED_FOR_WITH_PATTERNS_WITH_ELVDS);
        test.forDisambiguationTest("GPoint<Point>(Point(Integer a, Integer b), Point c)",
                                 ForType.ENHANCED_FOR_WITH_PATTERNS_WITH_ELVDS);
        test.forDisambiguationTest("GPoint<Point>(Point(var a, Integer b), Point c)",
                                 ForType.ENHANCED_FOR_WITH_PATTERNS_WITH_ELVDS);
        test.forDisambiguationTest("GPoint<VoidPoint>(VoidPoint(), VoidPoint())",
                                 ForType.ENHANCED_FOR_WITH_PATTERNS_WITH_ELVDS);
        test.forDisambiguationTest("RecordOfLists(List<Integer> lr)",
                                 ForType.ENHANCED_FOR_WITH_PATTERNS_WITH_ELVDS);
        test.forDisambiguationTest("RecordOfLists2(List<List<Integer>> lr)",
                                 ForType.ENHANCED_FOR_WITH_PATTERNS_WITH_ELVDS);
        test.forDisambiguationTest("T[].class.getName()",
                                 ForType.TRADITIONAL_FOR);
        test.forDisambiguationTest("method()",
                                 ForType.TRADITIONAL_FOR);
        test.forDisambiguationTest("method(), method()",
                                 ForType.TRADITIONAL_FOR);
        test.forDisambiguationTest("method2((Integer a) -> 42)",
                                 ForType.TRADITIONAL_FOR);
        test.forDisambiguationTest("m(cond ? b() : i)",
                                 ForType.TRADITIONAL_FOR);
        test.forDisambiguationTest("m((GPoint<?>)null, cond ? b() : i)",
                                 ForType.TRADITIONAL_FOR);

        // Local Variable Declaration or Enhanced Local Variable Declaration?
        test.variableDeclDisambiguationTest("Point(Integer a, Integer b) = p",
                                 LocalVariableDeclType.ENHANCED_LOCAL_VARIABLE_DECLARATION);
        test.variableDeclDisambiguationTest("Point(var a, var b) = p",
                                 LocalVariableDeclType.ENHANCED_LOCAL_VARIABLE_DECLARATION);
        test.variableDeclDisambiguationTest("R(T[] a) = r",
                                 LocalVariableDeclType.ENHANCED_LOCAL_VARIABLE_DECLARATION);
        test.variableDeclDisambiguationTest("ForEachPatterns.Point(Integer a, Integer b) = fp",
                                 LocalVariableDeclType.ENHANCED_LOCAL_VARIABLE_DECLARATION);
        test.variableDeclDisambiguationTest("GPoint<Point>(Point(var a, Integer b), Point c) = gp",
                                 LocalVariableDeclType.ENHANCED_LOCAL_VARIABLE_DECLARATION);
        test.variableDeclDisambiguationTest("GPoint<Point>(Point(@Ann Integer a, @Ann Integer b), @Ann Point c) = gp",
                                 LocalVariableDeclType.ENHANCED_LOCAL_VARIABLE_DECLARATION);
        test.variableDeclDisambiguationTest("RecordOfLists2(List<List<Integer>> lr) = rol2",
                                 LocalVariableDeclType.ENHANCED_LOCAL_VARIABLE_DECLARATION);
        test.variableDeclDisambiguationTest("Point p = p0",
                                 LocalVariableDeclType.LOCAL_VARIABLE_DECLARATION);
        test.variableDeclDisambiguationTest("@Ann Point p = p0",
                                 LocalVariableDeclType.LOCAL_VARIABLE_DECLARATION);
        test.variableDeclDisambiguationTest("GPoint<Integer> gp = g",
                                 LocalVariableDeclType.LOCAL_VARIABLE_DECLARATION);
        test.variableDeclDisambiguationTest("T[] a = arr",
                                 LocalVariableDeclType.LOCAL_VARIABLE_DECLARATION);
        test.variableDeclDisambiguationTest("int i = 0",
                                 LocalVariableDeclType.LOCAL_VARIABLE_DECLARATION);
        test.variableDeclDisambiguationTest("var v = method()",
                                 LocalVariableDeclType.LOCAL_VARIABLE_DECLARATION);
    }

    private final ParserFactory factory;

    public DisambiguatePatterns() throws URISyntaxException {
        Context context = new Context();
        JavacFileManager jfm = new JavacFileManager(context, true, Charset.defaultCharset());
        Options.instance(context).put(Option.PREVIEW, "");
        SimpleJavaFileObject source =
                new SimpleJavaFileObject(new URI("mem://Test.java"), JavaFileObject.Kind.SOURCE) {};
        Log.instance(context).useSource(source);
        factory = ParserFactory.instance(context);
    }

    void disambiguationTest(String snippet, ExpressionType expectedType) {
        String code = """
                      public class Test {
                          private void test() {
                              switch (null) {
                                  case SNIPPET -> {}
                              }
                          }
                      }
                      """.replace("SNIPPET", snippet);
        JavacParser parser = factory.newParser(code, false, false, false);
        CompilationUnitTree result = parser.parseCompilationUnit();
        ClassTree clazz = (ClassTree) result.getTypeDecls().get(0);
        MethodTree method = (MethodTree) clazz.getMembers().get(0);
        SwitchTree st = (SwitchTree) method.getBody().getStatements().get(0);
        CaseLabelTree label = st.getCases().get(0).getLabels().get(0);
        ExpressionType actualType = switch (label) {
            case ConstantCaseLabelTree et -> ExpressionType.EXPRESSION;
            case PatternCaseLabelTree pt -> ExpressionType.PATTERN;
            default -> throw new AssertionError("Unexpected result: " + result);
        };
        if (expectedType != actualType) {
            throw new AssertionError("Expected: " + expectedType + ", actual: " + actualType +
                                      ", for: " + code + ", parsed: " + result);
        }
    }

    void forDisambiguationTest(String snippet, ForType forType) {
        String codeTemplate = switch (forType) {
            case TRADITIONAL_FOR ->
                    """
                    public class Test {
                        private void test() {
                            for (SNIPPET; ;) {
                            }
                        }
                    }
                    """;
            case ENHANCED_FOR_WITH_LVDS, ENHANCED_FOR_WITH_PATTERNS_WITH_ELVDS ->
                    """
                    public class Test {
                        private void test() {
                            for (SNIPPET : collection) {
                            }
                        }
                    }
                    """;
        };

        String code = codeTemplate.replace("SNIPPET", snippet);
        JavacParser parser = factory.newParser(code, false, false, false);
        CompilationUnitTree result = parser.parseCompilationUnit();
        ClassTree clazz = (ClassTree) result.getTypeDecls().get(0);
        MethodTree method = (MethodTree) clazz.getMembers().get(0);
        StatementTree st = method.getBody().getStatements().get(0);
        if (forType == ForType.TRADITIONAL_FOR) {
            if (st.getKind() != Kind.FOR_LOOP) {
                throw new AssertionError("Unpected statement: " + st);
            }
        } else {
            EnhancedForLoopTree ef = (EnhancedForLoopTree) st;
            ForType actualType = ef.getRecordPattern() != null
                    ? ForType.ENHANCED_FOR_WITH_PATTERNS_WITH_ELVDS
                    : ForType.ENHANCED_FOR_WITH_LVDS;
            if (forType != actualType) {
                throw new AssertionError("Expected: " + forType + ", actual: " + actualType +
                        ", for: " + code + ", parsed: " + result);
            }
        }
    }

    void variableDeclDisambiguationTest(String snippet, LocalVariableDeclType varDeclType, String... expectedErrors) {
        String code = """
                      public class Test {
                          private void test() {
                              SNIPPET;
                          }
                      }
                      """.replace("SNIPPET", snippet);
        JavacParser parser = factory.newParser(code, false, false, false);
        CompilationUnitTree result = parser.parseCompilationUnit();
        ClassTree clazz = (ClassTree) result.getTypeDecls().get(0);
        MethodTree method = (MethodTree) clazz.getMembers().get(0);
        StatementTree st = method.getBody().getStatements().get(0);
        switch (varDeclType) {
            case ENHANCED_LOCAL_VARIABLE_DECLARATION -> {
                if (!(st instanceof JCEnhancedVariableDeclaration)) {
                    throw new AssertionError("Expected JCEnhancedVariableDecl, got: " + st.getClass() +
                            ", for: " + code + ", parsed: " + result);
                }
                if (st.getKind() != Kind.ENHANCED_VARIABLE_DECLARATION) {
                    throw new AssertionError("Expected kind ENHANCED_VARIABLE_DECL, got: " + st.getKind() +
                            ", for: " + code + ", parsed: " + result);
                }
            }
            case LOCAL_VARIABLE_DECLARATION -> {
                if (!(st instanceof JCVariableDecl)) {
                    throw new AssertionError("Expected JCVariableDecl, got: " + st.getClass() +
                            ", for: " + code + ", parsed: " + result);
                }
                if (st.getKind() != Kind.VARIABLE) {
                    throw new AssertionError("Expected kind VARIABLE, got: " + st.getKind() +
                            ", for: " + code + ", parsed: " + result);
                }
            }
        }
    }

    enum ForType {
        TRADITIONAL_FOR,
        ENHANCED_FOR_WITH_LVDS,
        ENHANCED_FOR_WITH_PATTERNS_WITH_ELVDS;
    }

    enum ExpressionType {
        PATTERN,
        EXPRESSION;
    }

    enum LocalVariableDeclType {
        LOCAL_VARIABLE_DECLARATION,
        ENHANCED_LOCAL_VARIABLE_DECLARATION;
    }
}
