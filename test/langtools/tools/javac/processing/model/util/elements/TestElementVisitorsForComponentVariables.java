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
 * @bug 8140442 8324651
 * @enablePreview
 * @summary Test ElementKindVisitorX w.r.t. component local variable
 * @library /tools/javac/lib
 * @build   JavacTestingAbstractProcessor TestElementVisitorsForComponentVariables
 * @compile -J--enable-preview -processor TestElementVisitorsForComponentVariables -proc:only TestElementVisitorsForComponentVariables.java
 */

import com.sun.source.tree.IdentifierTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.util.*;
import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.util.*;

/**
 * Test basic workings ElementKindVisitorX with regards to component
 * local variables.
 */
public class TestElementVisitorsForComponentVariables extends JavacTestingAbstractProcessor {
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            TypeElement outer = eltUtils.getTypeElement("Outer");
            Element componentVariable = findComponentLocalVariable(outer);

            checkExceptionThrown(componentVariable, new ElementKindVisitor6<>() {
                @Override
                public Void visitUnknown(Element e, Void p) {
                    throw new DefaultAction();
                }
            });
            checkExceptionThrown(componentVariable, new ElementKindVisitor7<>() {
                @Override
                public Void visitUnknown(Element e, Void p) {
                    throw new DefaultAction();
                }
            });
            checkExceptionThrown(componentVariable, new ElementKindVisitor8<>() {
                @Override
                public Void visitUnknown(Element e, Void p) {
                    throw new DefaultAction();
                }
            });
            checkExceptionThrown(componentVariable, new ElementKindVisitor9<>() {
                @Override
                public Void visitUnknown(Element e, Void p) {
                    throw new DefaultAction();
                }
            });
            checkExceptionThrown(componentVariable, new ElementKindVisitor14<>() {
                @Override
                public Void visitUnknown(Element e, Void p) {
                    throw new DefaultAction();
                }
            });
            checkExceptionThrown(componentVariable, new ElementKindVisitorPreview<>() {
                @Override
                protected Void defaultAction(Element e, Void p) {
                    throw new DefaultAction();
                }
            });

            boolean[] seenComponentVariable = new boolean[1];

            new ElementKindVisitorPreview<Void, Void>() {
                @Override
                public Void visitVariableAsComponentLocalVariable(VariableElement e, Void p) {
                    seenComponentVariable[0] = true;
                    return null; //OK
                }
                @Override
                protected Void defaultAction(Element e, Void p) {
                    throw new RuntimeException("Should not get here!");
                }
            }.visit(componentVariable, null);
        }
        return true;
    }

    private Element findComponentLocalVariable(Element root) {
        Trees trees = Trees.instance(processingEnv);
        TreePath topLevelPath = trees.getPath(root);
        Element[] componentLocalVariable = new Element[1];
        new TreePathScanner<Void, Void>() {
            @Override
            public Void visitIdentifier(IdentifierTree node, Void p) {
                Element el = trees.getElement(getCurrentPath());

                if (el != null && el.getKind() == ElementKind.COMPONENT_LOCAL_VARIABLE) {
                    componentLocalVariable[0] = el;
                }

                return super.visitIdentifier(node, p);
            }

        }.scan(topLevelPath, null);

        if (componentLocalVariable[0] == null) {
            throw new RuntimeException("Cannot find component variable.");
        }

        return componentLocalVariable[0];
    }

    private void checkExceptionThrown(Element componentLocalVariable,
                                      ElementKindVisitor6<Void, Void> v) {
        try {
            v.visit(componentLocalVariable);
            throw new RuntimeException("Should have thrown an exception.");
        } catch (DefaultAction ex) {
            //OK
        }
    }

    private static class DefaultAction extends RuntimeException {}
}

class Outer {
    private void nested() {
        record R(int i) {}
        R r = null;
        r = r with { i = 0; };
        new Runnable() {
            public void run() {}
        };
    }
}
