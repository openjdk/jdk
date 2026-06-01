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

import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import com.sun.source.util.Trees;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCExpressionStatement;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.ListBuffer;

/**
 * This annotation processor is used to remove calls to no-arg super() in a constructor,
 * so that Attr can generate warnings about early field references.
 */
public class SuperCallRemover extends JavacTestingAbstractProcessor {
    private Trees trees;

    @Override
    public void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        trees = Trees.instance(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }

        ConstructorPatcher remover = new ConstructorPatcher();
        for (Element root : roundEnv.getRootElements()) {
            JCTree tree = (JCTree)trees.getTree(root);
            if (tree != null) {
                remover.translate(tree);
            }
        }
        return false;
    }

    static class ConstructorPatcher extends TreeTranslator {
        @Override
        public void visitMethodDef(JCMethodDecl tree) {
            if (TreeInfo.isConstructor(tree) && TreeInfo.hasAnyConstructorCall(tree)) {
                ListBuffer<JCStatement> newStats = new ListBuffer<>();
                for (JCStatement statement : tree.body.stats) {
                    if (statement instanceof JCExpressionStatement expressionStatement &&
                            expressionStatement.expr instanceof JCMethodInvocation methodInvocation &&
                            TreeInfo.isSuperCall(expressionStatement) &&
                            methodInvocation.args.isEmpty()) {
                        continue;
                    }
                    newStats.add(statement);
                }
                tree.body.stats = newStats.toList();
            }
            super.visitMethodDef(tree);
        }
    }
}
