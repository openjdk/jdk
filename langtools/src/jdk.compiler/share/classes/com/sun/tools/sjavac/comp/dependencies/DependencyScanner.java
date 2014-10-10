/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.sun.tools.sjavac.comp.dependencies;

import java.util.HashSet;
import java.util.Set;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.PackageSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.TreeScanner;

class DependencyScanner extends TreeScanner {

    public final Set<Dependency> dependencies = new HashSet<>();

    private boolean isValidDependency(Type t) {
        if (t == null || t.isPrimitiveOrVoid() || t.isErroneous())
            return false;
        TypeTag tag = t.getTag();
        return tag != TypeTag.PACKAGE
            && tag != TypeTag.METHOD
            && tag != TypeTag.ARRAY
            && tag != TypeTag.TYPEVAR;
    }

    @Override
    public void visitIdent(JCIdent tree) {
        if (isValidDependency(tree.type))
            dependencies.add(new TypeAndSupertypesDependency(tree.type.tsym));
        super.visitIdent(tree);
    }

    @Override
    public void visitSelect(JCFieldAccess tree) {
        if (tree.getIdentifier().contentEquals("*")) {
            Symbol sym = tree.selected instanceof JCIdent ? ((JCIdent) tree.selected).sym
                                                          : ((JCFieldAccess) tree.selected).sym;
            if (sym instanceof ClassSymbol) {
                ClassSymbol clsSym = (ClassSymbol) sym;
                dependencies.add(new TypeAndSupertypesDependency(clsSym.type.tsym));
            } else {
                dependencies.add(new PackageDependency((PackageSymbol) sym));
            }
        } else if (tree.type != null && tree.type.hasTag(TypeTag.METHOD)) {  // Method call? Depend on the result (even though we never access it elsewhere)
            Type retType = tree.type.getReturnType();
            if (isValidDependency(retType))
                dependencies.add(new TypeAndSupertypesDependency(retType.tsym));
        } else if (isValidDependency(tree.type)) {
            dependencies.add(new TypeAndSupertypesDependency(tree.type.tsym));
        }
        super.visitSelect(tree);
    }

    public Set<Dependency> getResult() {
        return dependencies;
    }
}
