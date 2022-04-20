/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import java.util.Set;

@SupportedAnnotationTypes("*")
public class TypeAnnotationPositionProcessor extends AbstractProcessor {
    private Trees trees;
    private boolean processed = false;

    @Override
    public void init(ProcessingEnvironment pe) {
        super.init(pe);
        trees = Trees.instance(pe);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (processed) {
            return false;
        } else {
            processed = true;
        }
        Set<? extends Element> elements = roundEnv.getRootElements();
        TypeElement typeElement = null;
        for (TypeElement te : ElementFilter.typesIn(elements)) {
            if ("TypeAnnotationPositionTest".equals(te.getSimpleName().toString())) {
                typeElement = te;
                break;
            }
        }
        for (ExecutableElement m : ElementFilter.methodsIn(typeElement.getEnclosedElements())) {
            if ("test".equals(m.getSimpleName().toString())) {
                MethodTree methodTree = trees.getTree(m);
                new PositionVisitor().scan(methodTree, ((JCMethodDecl) methodTree).pos);
            }
        }
        return false;
    }

    private static class PositionVisitor extends TreeScanner<Void, Integer> {
        @Override
        public Void scan(Tree tree, Integer p) {
            if (tree != null) ((JCTree) tree).pos = p;
            return super.scan(tree, p);
        }
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }
}
