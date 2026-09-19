/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8369489 8391567
 * @summary Verify annotations on member references work reasonably.
 * @library /tools/lib /tools/javac/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run junit TypeAnnosOnMemberReferenceTest
 */

import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class TypeAnnosOnMemberReferenceTest {
    private ToolBox tb = new ToolBox();
    private Path base;

    @Test //JDK-8369489
    public void testAnnoOnMemberRef() throws Exception {
        Path src = base.resolve("src");
        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        tb.writeJavaFiles(src,
                """
                import java.lang.annotation.Target;
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;

                public class Test {
                    interface I {
                        void foo(int i);
                    }

                    @Target(ElementType.TYPE_USE)
                    @interface Ann1 {}
                    @Target(ElementType.TYPE_USE)
                    @interface Ann2 {}
                    I i = @Ann1 Test @Ann2 []::new;
                }
                """);

        String expected =
                """
                i:

                (MEMBER_REFERENCE
                    (ANNOTATED_TYPE
                        (TYPE_ANNOTATION
                            (IDENTIFIER Ann2
                            )
                        )
                        (ARRAY_TYPE
                            (ANNOTATED_TYPE
                                (TYPE_ANNOTATION
                                    (IDENTIFIER Ann1
                                    )
                                )
                                (IDENTIFIER Test
                                )
                            )
                        )
                    )
                )
                """;

        Path classDir = getClassDir();
        new JavacTask(tb)
                .classpath(classDir)
                .outdir(classes)
                .options("-processor", VerifyAnnotations.class.getName(),
                         "-Aexpected=" + expected)
                .files(tb.findJavaFiles(src))
                .outdir(classes)
                .run(Task.Expect.SUCCESS);
    }

    @Test //JDK-8391567
    public void testTreeInfoIsTypeSelectorDelegatesToSelectValid() throws Exception {
        //annotating "@Ann1 Test.I", where I is an innerclass is valid:
        Path src = base.resolve("src");
        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        tb.writeJavaFiles(src,
                """
                import java.lang.annotation.*;
                import java.util.function.IntFunction;
                import java.util.function.Supplier;
                import java.util.*;

                class Test {
                    @Target(ElementType.TYPE_USE)
                    @interface Ann1 {}
                    @Target(ElementType.TYPE_USE)
                    @interface Ann2 {}
                    @Target(ElementType.TYPE_USE)
                    @interface Ann3 {}
                    class I {}
                    Supplier<I> f1 = @Ann1 Test.I::new;
                    IntFunction<I[]> f2 = @Ann1 Test.I @Ann2 []::new;
                    IntFunction<I[][]> f3 = @Ann1 Test.I @Ann2 [] @Ann3 []::new;
                }
                """);

        String expected =
                """
                f1:

                (MEMBER_REFERENCE
                    (MEMBER_SELECT
                        (ANNOTATED_TYPE
                            (TYPE_ANNOTATION
                                (IDENTIFIER Ann1
                                )
                            )
                            (IDENTIFIER Test
                            )
                        )
                    )
                )
                f2:

                (MEMBER_REFERENCE
                    (ANNOTATED_TYPE
                        (TYPE_ANNOTATION
                            (IDENTIFIER Ann2
                            )
                        )
                        (ARRAY_TYPE
                            (MEMBER_SELECT
                                (ANNOTATED_TYPE
                                    (TYPE_ANNOTATION
                                        (IDENTIFIER Ann1
                                        )
                                    )
                                    (IDENTIFIER Test
                                    )
                                )
                            )
                        )
                    )
                )
                f3:

                (MEMBER_REFERENCE
                    (ANNOTATED_TYPE
                        (TYPE_ANNOTATION
                            (IDENTIFIER Ann2
                            )
                        )
                        (ARRAY_TYPE
                            (ANNOTATED_TYPE
                                (TYPE_ANNOTATION
                                    (IDENTIFIER Ann3
                                    )
                                )
                                (ARRAY_TYPE
                                    (MEMBER_SELECT
                                        (ANNOTATED_TYPE
                                            (TYPE_ANNOTATION
                                                (IDENTIFIER Ann1
                                                )
                                            )
                                            (IDENTIFIER Test
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
                """;

        Path classDir = getClassDir();
        new JavacTask(tb)
                .classpath(classDir)
                .outdir(classes)
                .options("-processor", VerifyAnnotations.class.getName(),
                         "-Aexpected=" + expected)
                .files(tb.findJavaFiles(src))
                .outdir(classes)
                .run(Task.Expect.SUCCESS);
    }

    @Test //JDK-8391567
    public void testTreeInfoIsTypeSelectorDelegatesToSelectInvalid() throws Exception {
        //annotating "@Ann1 T.N" where N is a static nested class
        //or "@Ann p.T.N" or "@Ann p.T.I", where I is an inner class
        //and p is a package is not valid:
        Path src = base.resolve("src");
        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        tb.writeJavaFiles(src,
                """
                package p;

                import java.lang.annotation.*;
                import java.util.function.Supplier;

                class Test {
                    @Target(ElementType.TYPE_USE)
                    @interface Ann1 {}
                    static class N {}
                           class I {}
                    Supplier<N> f1 = @Ann1 Test.N::new;
                    Supplier<N> f2 = @Ann1 p.Test.N::new;
                    Supplier<I> f3 = @Ann1 p.Test.I::new;
                }
                """);

        List<String> expected = List.of(
            "Test.java:11:28: compiler.err.type.annotation.inadmissible: (compiler.misc.type.annotation.1: @p.Test.Ann1), p.Test, @p.Test.Ann1 p.Test.N",
            "Test.java:12:28: compiler.err.type.annotation.inadmissible: (compiler.misc.type.annotation.1: @p.Test.Ann1), p.Test, @p.Test.Ann1 p.Test.N",
            "Test.java:13:28: compiler.err.type.annotation.inadmissible: (compiler.misc.type.annotation.1: @p.Test.Ann1), p.Test, p.Test.@p.Test.Ann1 I",
            "3 errors"
        );

        List<String> log =
            new JavacTask(tb)
                .outdir(classes)
                .options("-XDrawDiagnostics")
                .files(tb.findJavaFiles(src))
                .outdir(classes)
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        tb.checkEqual(expected, log);
    }

    public Path getClassDir() {
        String classes = ToolBox.testClasses;
        if (classes == null) {
            return Paths.get("build");
        } else {
            return Paths.get(classes);
        }
    }

    @BeforeEach
    void setBase(TestInfo testInfo) {
        base = Path.of(testInfo.getTestMethod().orElseThrow().getName());
    }

    @SupportedAnnotationTypes("*")
    @SupportedOptions("expected")
    public static final class VerifyAnnotations extends AbstractProcessor {
        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            TypeElement testElement = processingEnv.getElementUtils().getTypeElement("Test");
            Trees trees = Trees.instance(processingEnv);
            StringBuilder text = new StringBuilder();
            for (VariableElement iElement : ElementFilter.fieldsIn(testElement.getEnclosedElements())) {
                text.append(iElement.getSimpleName()).append(":\n");

                TreePath iPath = trees.getPath(iElement);
                new TreeScanner<>() {
                    int ident = 0;
                    @Override
                    public Object scan(Tree tree, Object p) {
                        if (tree != null) {
                            String indent =
                                    Stream.generate(() -> " ")
                                          .limit(ident)
                                          .collect(Collectors.joining());

                            text.append("\n")
                                .append(indent)
                                .append("(")
                                .append(tree.getKind());
                            ident += 4;
                            super.scan(tree, p);
                            ident -= 4;
                            text.append("\n")
                                .append(indent)
                                .append(")");
                        }
                        return null;
                    }

                    @Override
                    public Object visitIdentifier(IdentifierTree node, Object p) {
                        text.append(" ").append(node.getName());
                        return super.visitIdentifier(node, p);
                    }
                }.scan(((VariableTree) iPath.getLeaf()).getInitializer(), null);
                text.append("\n");
            }

            String actual = text.toString();
            String expected = processingEnv.getOptions().get("expected");

            if (!Objects.equals(expected, actual)) {
                throw new AssertionError("Expected: " + expected + "," +
                                         "got: " + actual);
            }

            return false;
        }
    }
}
