/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8369489
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

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;

import org.junit.jupiter.api.Test;

import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class TypeAnnosOnMemberReferenceTest {
    private ToolBox tb = new ToolBox();

    @Test
    public void testAnnoOnMemberRef() throws Exception {
        Path base = Paths.get(".");
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

        Path classDir = getClassDir();
        new JavacTask(tb)
                .classpath(classDir)
                .outdir(classes)
                .options("-processor", VerifyAnnotations.class.getName())
                .files(tb.findJavaFiles(src))
                .outdir(classes)
                .run(Task.Expect.SUCCESS);
    }

    public Path getClassDir() {
        String classes = ToolBox.testClasses;
        if (classes == null) {
            return Paths.get("build");
        } else {
            return Paths.get(classes);
        }
    }

    @SupportedAnnotationTypes("*")
    public static final class VerifyAnnotations extends AbstractProcessor {
        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            TypeElement testElement = processingEnv.getElementUtils().getTypeElement("Test");
            VariableElement iElement = ElementFilter.fieldsIn(testElement.getEnclosedElements()).getFirst();
            Trees trees = Trees.instance(processingEnv);
            TreePath iPath = trees.getPath(iElement);
            StringBuilder text = new StringBuilder();
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
            String expected =
                    """

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
                    )""";

            String actual = text.toString();

            if (!expected.equals(actual)) {
                throw new AssertionError("Expected: " + expected + "," +
                                         "got: " + actual);
            }

            return false;
        }
    }
}
