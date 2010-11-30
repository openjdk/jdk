/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6993963
 * @summary Project Coin: Use precise exception analysis for effectively final catch parameters
 * @library ../../lib
 * @build JavacTestingAbstractProcessor ModelChecker
 * @compile -processor ModelChecker Model01.java
 */

import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import com.sun.source.util.TreePath;

import java.util.Set;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;

@SupportedAnnotationTypes("Check")
public class ModelChecker extends JavacTestingAbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver())
            return true;

        Trees trees = Trees.instance(processingEnv);

        TypeElement testAnno = elements.getTypeElement("Check");
        for (Element elem: roundEnv.getElementsAnnotatedWith(testAnno)) {
            TreePath p = trees.getPath(elem);
            new MulticatchParamTester(trees).scan(p, null);
        }
        return true;
    }

    class MulticatchParamTester extends TreePathScanner<Void, Void> {
        Trees trees;

        public MulticatchParamTester(Trees trees) {
            super();
            this.trees = trees;
        }

        @Override
        public Void visitVariable(VariableTree node, Void p) {
            Element ex = trees.getElement(getCurrentPath());
            if (ex.getSimpleName().contentEquals("ex")) {
                assertTrue(ex.getKind() == ElementKind.EXCEPTION_PARAMETER, "Expected EXCEPTION_PARAMETER - found " + ex.getKind());
                for (Element e : types.asElement(ex.asType()).getEnclosedElements()) {
                    Member m = e.getAnnotation(Member.class);
                    if (m != null) {
                        assertTrue(e.getKind() == m.value(), "Expected " + m.value() + " - found " + e.getKind());
                    }
                }
                assertTrue(assertionCount == 3, "Expected 3 assertions - found " + assertionCount);
            }
            return super.visitVariable(node, p);
        }
    }

    private static void assertTrue(boolean cond, String msg) {
        assertionCount++;
        if (!cond)
            throw new AssertionError(msg);
    }

    static int assertionCount = 0;
}
