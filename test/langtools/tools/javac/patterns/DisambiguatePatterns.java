/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @enablePreview
 */

import com.sun.source.tree.CaseLabelTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ConstantCaseLabelTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.PatternCaseLabelTree;
import com.sun.source.tree.PatternTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.Tree.Kind;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.parser.JavacParser;
import com.sun.tools.javac.parser.ParserFactory;
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
        test.disambiguationTest("(String s)",
                                 ExpressionType.PATTERN);
        test.disambiguationTest("(@Ann String s)",
                                 ExpressionType.PATTERN);
        test.disambiguationTest("((String s))",
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
        test.disambiguationTest("(a < c.d > b)",
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
        test.forDisambiguationTest("T[] a", ForType.ENHANCED_FOR);
        test.forDisambiguationTest("T[].class.getName()", ForType.TRADITIONAL_FOR);
        test.forDisambiguationTest("T[].class", ForType.TRADITIONAL_FOR, "compiler.err.not.stmt");
        test.forDisambiguationTest("R(T[] a)", ForType.ENHANCED_FOR_WITH_PATTERNS);

        test.forDisambiguationTest("Point(Integer a, Integer b)", ForType.ENHANCED_FOR_WITH_PATTERNS);
        test.forDisambiguationTest("ForEachPatterns.Point(Integer a, Integer b)", ForType.ENHANCED_FOR_WITH_PATTERNS);
        test.forDisambiguationTest("GPoint<Integer>(Integer a, Integer b)", ForType.ENHANCED_FOR_WITH_PATTERNS);
        test.forDisambiguationTest("@Annot(field = \"test\") Point p", ForType.ENHANCED_FOR);
        test.forDisambiguationTest("GPoint<Point>(Point(Integer a, Integer b), Point c)", ForType.ENHANCED_FOR_WITH_PATTERNS);
        test.forDisambiguationTest("GPoint<Point>(Point(var a, Integer b), Point c)", ForType.ENHANCED_FOR_WITH_PATTERNS);
        test.forDisambiguationTest("GPoint<VoidPoint>(VoidPoint(), VoidPoint())", ForType.ENHANCED_FOR_WITH_PATTERNS);
        test.forDisambiguationTest("RecordOfLists(List<Integer> lr)", ForType.ENHANCED_FOR_WITH_PATTERNS);
        test.forDisambiguationTest("RecordOfLists2(List<List<Integer>> lr)", ForType.ENHANCED_FOR_WITH_PATTERNS);
        test.forDisambiguationTest("GPoint<@Annot(field = \"\") ? extends Point>(var x, var y)", ForType.ENHANCED_FOR_WITH_PATTERNS);

        test.forDisambiguationTest("method()", ForType.TRADITIONAL_FOR);
        test.forDisambiguationTest("method(), method()", ForType.TRADITIONAL_FOR);
        test.forDisambiguationTest("method2((Integer a) -> 42)", ForType.TRADITIONAL_FOR);
        test.forDisambiguationTest("m(cond ? b() : i)", ForType.TRADITIONAL_FOR);
        test.forDisambiguationTest("m((GPoint<?>)null, cond ? b() : i)", ForType.TRADITIONAL_FOR);
    }

    private final ParserFactory factory;
    private final List<String> errors = new ArrayList<>();

    public DisambiguatePatterns() throws URISyntaxException {
        Context context = new Context();
        context.put(DiagnosticListener.class, d -> {
            if (d.getKind() == Diagnostic.Kind.ERROR) {
                errors.add(d.getCode());
            }
        });
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

    void forDisambiguationTest(String snippet, ForType forType, String... expectedErrors) {
        errors.clear();

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
            case ENHANCED_FOR, ENHANCED_FOR_WITH_PATTERNS ->
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
        if (!Arrays.asList(expectedErrors).equals(errors)) {
            throw new AssertionError("Expected errors: " + Arrays.asList(expectedErrors) +
                                     ", actual: " + errors +
                                     ", for: " + code);
        }
        ClassTree clazz = (ClassTree) result.getTypeDecls().get(0);
        MethodTree method = (MethodTree) clazz.getMembers().get(0);
        StatementTree st = method.getBody().getStatements().get(0);
        if (forType == ForType.TRADITIONAL_FOR) {
            if (st.getKind() != Kind.FOR_LOOP) {
                throw new AssertionError("Unpected statement: " + st);
            }
        } else {
            EnhancedForLoopTree ef = (EnhancedForLoopTree) st;
            ForType actualType = switch (ef.getVariableOrRecordPattern()) {
                case PatternTree pattern -> ForType.ENHANCED_FOR_WITH_PATTERNS;
                default -> ForType.ENHANCED_FOR;
            };
            if (forType != actualType) {
                throw new AssertionError("Expected: " + forType + ", actual: " + actualType +
                                          ", for: " + code + ", parsed: " + result);
            }
        }
    }

    enum ExpressionType {
        PATTERN,
        EXPRESSION;
    }

    enum ForType {
        TRADITIONAL_FOR,
        ENHANCED_FOR,
        ENHANCED_FOR_WITH_PATTERNS;
    }
}
