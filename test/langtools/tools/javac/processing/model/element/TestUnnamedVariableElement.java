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
 * @enablePreview
 * @compile TestUnnamedVariableElement.java
 * @compile --enable-preview -source ${jdk.version} -processor TestUnnamedVariableElement -proc:only TestUnnamedVariableElementData.java
 */

import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.util.Elements;
import java.util.*;
import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.io.StringWriter;

public class TestUnnamedVariableElement extends JavacTestingAbstractProcessor implements AutoCloseable {

    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            Trees trees = Trees.instance(processingEnv);

            for(Element rootElement : roundEnv.getRootElements()) {
                TreePath treePath = trees.getPath(rootElement);

                (new UnnamedVariableScanner(processingEnv.getElementUtils(), trees)).
                        scan(treePath.getCompilationUnit(), null);
            }
        }
        return true;
    }

    @Override
    public void close() {}

    class UnnamedVariableScanner extends TreePathScanner<Void, Void> {

        private final Elements elements;
        private Trees trees;

        public UnnamedVariableScanner(Elements elements, Trees trees) {
            super();
            this.elements = elements;
            this.trees = trees;
        }

        @Override
        public Void visitVariable(VariableTree node, Void unused) {
            handleTreeAsLocalVar(getCurrentPath());

            if(!node.getName().isEmpty()) {
                throw new RuntimeException("Expected empty name as the name of the Tree API but got: " + node.getName());
            }

            return super.visitVariable(node, unused);
        }

        private void handleTreeAsLocalVar(TreePath tp) {
            VariableElement element = (VariableElement) trees.getElement(tp);

            System.out.println("Name: " + element.getSimpleName() +
                    "\tKind: " + element.getKind());
            if (element.getKind() != ElementKind.LOCAL_VARIABLE) {
                throw new RuntimeException("Expected a local variable, but got: " +
                        element.getKind());
            }
            StringWriter out = new StringWriter();
            String expected = "int _;";
            elements.printElements(out, element);
            if (!expected.equals(out.toString().trim())) {
                throw new RuntimeException("Expected: " + expected + ", but got: " + out.toString());
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
