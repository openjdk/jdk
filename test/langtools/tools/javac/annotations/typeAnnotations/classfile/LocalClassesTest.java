/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8371817
 * @summary Check for type annotating types that refer to local classes read
 *          from classfiles
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run junit LocalClassesTest
 */

import com.sun.source.tree.ClassTree;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import toolbox.JavacTask;
import toolbox.ToolBox;

public class LocalClassesTest {

    ToolBox tb = new ToolBox();
    Path base;

    @Test
    void test() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);

        Map<String, String> local2enclosing = new HashMap<>();
        new JavacTask(tb)
                .options("-d", classes.toString())
                .sources("""
                         import java.lang.annotation.ElementType;
                         import java.lang.annotation.Target;

                         public class Test {
                            public static void m1() {
                               class Local1 {
                                  @Nullable Local1 l;
                               }
                            }
                            public void m2() {
                               class Local2 {
                                  @Nullable Local2 l;
                               }
                            }
                         }

                         @Target({ElementType.TYPE_USE})
                         @interface Nullable {}
                         """)
                .callback(task -> {
                    task.addTaskListener(new TaskListener() {
                        @Override
                        public void finished(TaskEvent e) {
                            if (e.getKind() == TaskEvent.Kind.ANALYZE) {
                                Trees trees = Trees.instance(task);
                                new TreePathScanner<>() {
                                    @Override
                                    public Object visitClass(ClassTree node, Object p) {
                                        if (node.getSimpleName().toString().startsWith("Local")) {
                                            Element el = trees.getElement(getCurrentPath());
                                            TypeMirror type = trees.getTypeMirror(getCurrentPath());
                                            local2enclosing.put(el.getSimpleName().toString(), ((DeclaredType) type).getEnclosingType().toString());
                                        }
                                        return super.visitClass(node, p);
                                    }
                                }.scan(e.getCompilationUnit(), null);
                            }
                        }
                    });
                })
                .run()
                .writeAll();

        Path classes2 = base.resolve("classes2");
        Files.createDirectories(classes2);

        ProcessorImpl p = new ProcessorImpl();
        new JavacTask(tb)
                .options("-cp", classes.toString(), "-d", classes2.toString())
                .processors(p)
                .classes("Test$1Local1", "Test$1Local2")
                .run()
                .writeAll();

        Assertions.assertEquals(local2enclosing.get("Local1"), p.local2enclosing.get("Local1"));
        Assertions.assertEquals(local2enclosing.get("Local2"), p.local2enclosing.get("Local2"));
    }

    @SupportedAnnotationTypes("*")
    private static class ProcessorImpl extends AbstractProcessor {
        private Map<String, String> local2enclosing = new HashMap<>();

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            for (TypeElement te : ElementFilter.typesIn(roundEnv.getRootElements())) {
                if (te.getSimpleName().toString().startsWith("Local")) {
                    local2enclosing.put(te.getSimpleName().toString(), ((DeclaredType) te.asType()).getEnclosingType().toString());
                }
            }
            return false;
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported();
        }
    }

    @BeforeEach
    public void setup(TestInfo info) {
        base = Paths.get(".")
                    .resolve(info.getTestMethod()
                                 .orElseThrow()
                                 .getName());
    }
}
