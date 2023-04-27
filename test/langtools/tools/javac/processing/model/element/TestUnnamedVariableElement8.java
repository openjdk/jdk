/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8302344 8307007
 * @summary Compiler Implementation for Unnamed patterns and variables
 * @library /tools/javac/lib
 * @modules jdk.compiler
 * @build   JavacTestingAbstractProcessor
 * @compile TestUnnamedVariableElement8.java
 * @compile -source 8 -processor TestUnnamedVariableElement8 -proc:only TestUnnamedVariableElementData.java
 */

import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.Set;

public class TestUnnamedVariableElement8 extends JavacTestingAbstractProcessor implements AutoCloseable {

    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            Trees trees = Trees.instance(processingEnv);

            for(Element rootElement : roundEnv.getRootElements()) {
                TreePath treePath = trees.getPath(rootElement);

                (new UnnamedVariableScanner(trees)).
                        scan(treePath.getCompilationUnit(), null);
            }
        }
        return true;
    }

    @Override
    public void close() {}

    class UnnamedVariableScanner extends TreePathScanner<Void, Void> {
        private Trees trees;

        public UnnamedVariableScanner(Trees trees) {
            super();
            this.trees = trees;
        }

        @Override
        public Void visitVariable(VariableTree node, Void unused) {
            handleTreeAsLocalVar(getCurrentPath());
            return super.visitVariable(node, unused);
        }

        private void handleTreeAsLocalVar(TreePath tp) {
            Element element = trees.getElement(tp);

            System.out.println("Name: " + element.getSimpleName() +
                    "\tKind: " + element.getKind());
            if (element.getKind() != ElementKind.LOCAL_VARIABLE) {
                throw new RuntimeException("Expected a local variable, but got: " +
                        element.getKind());
            }
            if (!element.getSimpleName().toString().equals("_")) {
                throw new RuntimeException("Expected _ for simple name of an unnamed variable, but got: " +
                        element.getSimpleName());
            }
            testUnnamedVariable(element);
        }
    }

    /**
     * Verify that a local variable modeled as an element behaves
     * as expected under 6 and latest specific visitors.
     */
    private static void testUnnamedVariable(Element element) {
        ElementKindVisitor visitorLatest =
                new ElementKindVisitor<Object, Void>() {
                    @Override
                    public Object visitVariableAsLocalVariable(VariableElement e,
                                                               Void p) {
                        return e;
                    }
                };

        if (visitorLatest.visit(element) == null) {
            throw new RuntimeException("Null result of a resource variable visitation.");
        }
    }
}
