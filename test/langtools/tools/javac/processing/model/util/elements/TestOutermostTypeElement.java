/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8140442
 * @enablePreview
 * @summary Test Elements.getOutermostTypeElement
 * @library /tools/javac/lib
 * @build   JavacTestingAbstractProcessor TestOutermostTypeElement
 * @compile -J--enable-preview -processor TestOutermostTypeElement -proc:only TestOutermostTypeElement.java
 */

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.DerivedInstanceTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.util.*;
import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.util.*;

/**
 * Test basic workings of Elements.getOutermostTypeElement
 */
public class TestOutermostTypeElement extends JavacTestingAbstractProcessor {
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            Elements vacuousElts = new VacuousElements();
            Trees trees = Trees.instance(processingEnv);

            ModuleElement javaBaseMod = eltUtils.getModuleElement("java.base");
            checkOuter(javaBaseMod, null, vacuousElts);
            checkOuter(javaBaseMod, null, eltUtils);

            PackageElement javaLangPkg = eltUtils.getPackageElement("java.lang");
            checkOuter(javaLangPkg, null, vacuousElts);
            checkOuter(javaLangPkg, null, eltUtils);

            // Starting from the root elements, traverse over all
            // enclosed elements and type parameters. The outermost
            // enclosing type element should equal the root
            // element. This traversal does *not* hit elements
            // corresponding to structures inside of a method.
            for (TypeElement e : ElementFilter.typesIn(roundEnv.getRootElements()) ) {
                var outerScaner = new OuterScanner(e);
                outerScaner.scan(e, vacuousElts);
                outerScaner.scan(e, eltUtils);

                var treeBasedScanner = new OuterTreeBaseScanner();
                treeBasedScanner.scan(e, vacuousElts);
                treeBasedScanner.scan(e, eltUtils);
             }
        }
        return true;
    }

    private class OuterScanner extends ElementScanner<Void, Elements> {
        private TypeElement expectedOuter;
        public OuterScanner(TypeElement expectedOuter) {
            this.expectedOuter = expectedOuter;
        }

        @Override
        public Void scan(Element e, Elements elts) {
            checkOuter(e, expectedOuter, elts);
            super.scan(e, elts);
            return null;
        }
    }

    private void checkOuter(Element e, TypeElement expectedOuter, Elements elts) {
        var actualOuter = elts.getOutermostTypeElement(e);
        if (!Objects.equals(actualOuter, expectedOuter)) {
            throw new RuntimeException(String.format("Unexpected outermost ``%s''' for %s, expected ``%s.''%n",
                                                     actualOuter,
                                                     e,
                                                     expectedOuter));
        }
    }

    private class OuterTreeBaseScanner extends TreePathScanner<Void, Elements> {
        private final Trees trees;
        private TypeElement topLevel;

        public OuterTreeBaseScanner() {
            this.trees = Trees.instance(processingEnv);
        }

        public void scan(TypeElement el, Elements elts) {
            TreePath topLevelPath = trees.getPath(el);

            topLevel = el;
            scan(topLevelPath, elts);
        }

        @Override
        public Void visitClass(ClassTree node, Elements p) {
            handleDeclaration(p);
            return super.visitClass(node, p);
        }

        @Override
        public Void visitVariable(VariableTree node, Elements p) {
            handleDeclaration(p);
            return super.visitVariable(node, p);
        }

        @Override
        public Void visitMethod(MethodTree node, Elements p) {
            handleDeclaration(p);
            return super.visitMethod(node, p);
        }

        @Override
        public Void visitDerivedInstance(DerivedInstanceTree node, Elements p) {
            for (StatementTree st : node.getBlock().getStatements()) {
                if (st.getKind() == Kind.EXPRESSION_STATEMENT) {
                    ExpressionStatementTree est = (ExpressionStatementTree) st;

                    if (est.getExpression().getKind() == Kind.ASSIGNMENT) {
                        AssignmentTree at = (AssignmentTree) est.getExpression();
                        TreePath left = TreePath.getPath(getCurrentPath(), at.getVariable());
                        Element componentVariable = trees.getElement(left);

                        assertNotNull(componentVariable);

                        if (componentVariable.getKind() != ElementKind.COMPONENT_LOCAL_VARIABLE) {
                            throw new RuntimeException("Unexpected variable kind: " + componentVariable.getKind());
                        }

                        checkOuter(componentVariable, topLevel, p);
                    }
                }
            }

            return super.visitDerivedInstance(node, p);
        }

        private void handleDeclaration(Elements els) {
            Element el = trees.getElement(getCurrentPath());

            assertNotNull(el);
            checkOuter(el, topLevel, els);
        }

        private static void assertNotNull(Object o) {
            if (o == null) {
                throw new RuntimeException("Unexpected null value.");
            }
        }
    }
}

/**
 * Outer class to host a variety of kinds of inner elements with Outer
 * as their outermost class.
 */
class Outer {
    private Outer() {}

    public enum InnerEnum {
        VALUE1,
        VALUE2;

        private int field;
    }

    public static class InnerClass {
        private static int field;
        static {
            field = 5;
        }

        public <C> InnerClass(C c) {}

        void foo() {return;}
        static void bar() {return;}
        static <R> R baz(Class<? extends R> clazz) {return null;}

        private class InnerInnerClass {
            public InnerInnerClass() {}
        }

        private void nested() {
            int i = 0;
            try (AutoCloseable a = null) {
                boolean b = a instanceof Runnable r;
            } catch (Exception ex) {
            }
            record R(int i) {}
            R r = null;
            r = r with { i = 0; };
            new Runnable() {
                public void run() {}
            };
        }
    }

    public interface InnerInterface {
        final int field = 42;
        void foo();
    }

    public @interface InnerAnnotation {
        int value() default 1;
    }

    public record InnerRecord(double rpm, double diameter) {
        void foo() {return;}
    }
}
