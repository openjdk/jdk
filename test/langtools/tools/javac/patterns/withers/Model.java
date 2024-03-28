/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8256266 8281238
 * @summary Verify annotations work correctly on binding variables
 * @library /tools/javac/lib
 * @enablePreview
 * @modules java.compiler
 *          jdk.compiler
 *          java.base/jdk.internal.classfile.impl
 * @build JavacTestingAbstractProcessor
 * @compile Model.java
 * @compile/ref=Model.out -J--enable-preview -processor Model -XDshould-stop.at=FLOW -XDrawDiagnostics Model.java
 */

import com.sun.source.tree.AssignmentTree;
import com.sun.source.util.TreePath;
import java.util.Set;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.DerivedInstanceTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import javax.lang.model.element.ElementKind;

public class Model extends JavacTestingAbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            JavacTask.instance(processingEnv).addTaskListener(new TaskListener() {
                @Override
                public void finished(TaskEvent e) {
                    if (e.getKind() == TaskEvent.Kind.ANALYZE) {
                        performCheck(e.getCompilationUnit());
                    }
                }
            });
        }
        return false;
    }

    private void performCheck(CompilationUnitTree cut) {
        Trees trees = Trees.instance(processingEnv);
        SourcePositions sp = trees.getSourcePositions();

        new TreePathScanner<Void, Void>() {
            @Override
            public Void visitDerivedInstance(DerivedInstanceTree node, Void p) {
                System.err.println("visitDerivedInstance start");
                try {
                    int start = (int) sp.getStartPosition(cut, node);
                    int end = (int) sp.getEndPosition(cut, node);
                    System.err.println(node.toString());
                    System.err.println(cut.getSourceFile()
                                          .getCharContent(true)
                                          .subSequence(start, end));
                    return super.visitDerivedInstance(node, p);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                } finally {
                    System.err.println("visitDerivedInstance end");
                }
            }

            @Override
            public Void visitAssignment(AssignmentTree node, Void p) {
                Element varEl = trees.getElement(new TreePath(new TreePath(getCurrentPath(), node), node.getVariable()));
                if (varEl.getKind() == ElementKind.COMPONENT_LOCAL_VARIABLE) {
                    System.err.println(varEl.getSimpleName());
                }
                return super.visitAssignment(node, p);
            }
        }.scan(cut, null);
    }

    private static void test() {
        record R(String val1, Integer val2) {}
        R r = new R("", 0);
        r = r with {
            val1 = "a";
            val2 = -1;
        };
    }
}

