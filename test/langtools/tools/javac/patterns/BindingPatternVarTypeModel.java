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
 * @bug 8332725 8341901
 * @summary Verify the AST model works correctly for binding patterns with var
 */

import com.sun.source.tree.BindingPatternTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

public class BindingPatternVarTypeModel {
    private final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

    public static void main(String... args) throws Exception {
        new BindingPatternVarTypeModel().run();
        new BindingPatternVarTypeModel().runVarParameterized();
    }

    private void run() throws Exception {
        JavaFileObject input =
                SimpleJavaFileObject.forSource(URI.create("mem://Test.java"),
                                               """
                                               public class Test {
                                                   record R(int i) {}
                                                   int test(Object o) {
                                                       return switch (o) {
                                                           case R(var v) -> 0;
                                                           default -> 0;
                                                       };
                                                   }
                                               }
                                               """);
        JavacTask task =
                (JavacTask) compiler.getTask(null, null, null, null, null, List.of(input));
        CompilationUnitTree cut = task.parse().iterator().next();

        task.analyze();

        AtomicBoolean foundBindingPattern = new AtomicBoolean();

        new TreeScanner<Void, Void>() {
            @Override
            public Void visitBindingPattern(BindingPatternTree node, Void p) {
                if (node.getVariable().getType().getKind() != Tree.Kind.PRIMITIVE_TYPE) {
                    throw new AssertionError("Unexpected type for var: " +
                                             node.getVariable().getType().getKind() +
                                             ":" + node.getVariable().getType());
                }
                foundBindingPattern.set(true);
                return super.visitBindingPattern(node, p);
            }
        }.scan(cut, null);

        if (!foundBindingPattern.get()) {
            throw new AssertionError("Didn't find the binding pattern!");
        }
    }

    private void runVarParameterized() throws Exception {
        JavaFileObject input =
                SimpleJavaFileObject.forSource(URI.create("mem:///Test.java"),
                                               """
                                               package test;
                                               public class Test {
                                                   record R(N.I i) {}
                                                   int test(Object o) {
                                                       Test.N.I checkType0 = null;
                                                       var checkType1 = checkType0;
                                                       return switch (o) {
                                                           case R(var checkType2) -> 0;
                                                           default -> 0;
                                                       };
                                                   }
                                                   static class N<T> {
                                                       interface I {}
                                                   }
                                               }
                                               """);
        DiagnosticListener<JavaFileObject> noErrors = d -> {
            if (d.getKind() == Diagnostic.Kind.ERROR) {
                throw new IllegalStateException(d.toString());
            }
        };
        JavacTask task =
                (JavacTask) compiler.getTask(null, null, noErrors, null, null, List.of(input));
        CompilationUnitTree cut = task.parse().iterator().next();
        Trees trees = Trees.instance(task);

        task.analyze();

        new TreePathScanner<Void, Void>() {
            private boolean checkAttributes;
            @Override
            public Void visitVariable(VariableTree node, Void p) {
                boolean prevCheckAttributes = checkAttributes;
                try {
                    checkAttributes |=
                            node.getName().toString().startsWith("checkType");
                    return super.visitVariable(node, p);
                } finally {
                    checkAttributes = prevCheckAttributes;
                }
            }

            @Override
            public Void visitIdentifier(IdentifierTree node, Void p) {
                checkType();
                return super.visitIdentifier(node, p);
            }

            @Override
            public Void visitMemberSelect(MemberSelectTree node, Void p) {
                checkType();
                return super.visitMemberSelect(node, p);
            }

            private void checkType() {
                if (!checkAttributes) {
                    return ;
                }

                TypeMirror type = trees.getTypeMirror(getCurrentPath());

                if (type.getKind() == TypeKind.PACKAGE) {
                    return ; //OK
                }
                if (type.getKind() != TypeKind.DECLARED) {
                    throw new AssertionError("Expected a declared type, but got: " +
                                             type.getKind());
                }

                if (!((DeclaredType) type).getTypeArguments().isEmpty()) {
                    throw new AssertionError("Unexpected type arguments: " +
                                             ((DeclaredType) type).getTypeArguments());
                }
            }
        }.scan(cut, null);
    }
}
