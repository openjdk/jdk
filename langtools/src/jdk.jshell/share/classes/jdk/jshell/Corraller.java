/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jshell;

import java.util.List;
import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import jdk.jshell.Wrap.Range;
import static java.util.stream.Collectors.toList;

/**
 * Produce a corralled version of the Wrap for a snippet.
 *
 * @author Robert Field
 */
class Corraller {

    private final int index;
    private final String compileSource;
    private final TreeDissector dis;

    Corraller(int index, String compileSource, TreeDissector dis) {
        this.index = index;
        this.compileSource = compileSource;
        this.dis = dis;
    }

    Wrap corralTree(Tree tree, String enclosingType, int indent) {
        switch (tree.getKind()) {
            case VARIABLE:
                return corralVariable((VariableTree) tree, indent);
            case CLASS:
            case ENUM:
            case ANNOTATION_TYPE:
            case INTERFACE:
                return corralType((ClassTree) tree, indent);
            case METHOD:
                return corralMethod((MethodTree) tree, enclosingType, indent);
            default:
                return null;
        }
    }

    Wrap corralMethod(MethodTree mt) {
        return corralMethod(mt, null, 1);
    }

    Wrap corralMethod(MethodTree mt, String enclosingType, int indent) {
        Range modRange = dis.treeToRange(mt.getModifiers());
        Range tpRange = dis.treeListToRange(mt.getTypeParameters());
        Range typeRange = dis.treeToRange(mt.getReturnType());
        String name = mt.getName().toString();
        if ("<init>".equals(name)) {
            name = enclosingType;
        }
        Range paramRange = dis.treeListToRange(mt.getParameters());
        Range throwsRange = dis.treeListToRange(mt.getThrows());
        return Wrap.corralledMethod(compileSource,
                modRange, tpRange, typeRange, name, paramRange, throwsRange, index, indent);
    }

    Wrap corralVariable(VariableTree vt, int indent) {
        String name = vt.getName().toString();
        Range modRange = dis.treeToRange(vt.getModifiers());
        Tree baseType = vt.getType();
        StringBuilder sbBrackets = new StringBuilder();
        while (baseType instanceof ArrayTypeTree) {
            //TODO handle annotations too
            baseType = ((ArrayTypeTree) baseType).getType();
            sbBrackets.append("[]");
        }
        Range rtype = dis.treeToRange(baseType);
        Range runit = dis.treeToRange(vt);
        runit = new Range(runit.begin, runit.end - 1);
        ExpressionTree it = vt.getInitializer();
        int nameMax;
        if (it != null) {
            Range rinit = dis.treeToRange(it);
            nameMax = rinit.begin - 1;
        } else {
            nameMax = runit.end - 1;
        }
        int nameStart = compileSource.lastIndexOf(name, nameMax);
        if (nameStart < 0) {
            throw new AssertionError("Name '" + name + "' not found");
        }
        int nameEnd = nameStart + name.length();
        Range rname = new Range(nameStart, nameEnd);
        return Wrap.corralledVar(compileSource, modRange, rtype, sbBrackets.toString(), rname, indent);
    }

    Wrap corralType(ClassTree ct, int indent) {
        boolean isClass;
        switch (ct.getKind()) {
            case CLASS:
                isClass = true;
                break;
            case INTERFACE:
                isClass = false;
                break;
            default:
                return null;
        }
        Range modRange = dis.treeToRange(ct.getModifiers());
        String name = ct.getSimpleName().toString();
        Range tpRange = dis.treeListToRange(ct.getTypeParameters());
        Range extendsRange = dis.treeToRange(ct.getExtendsClause());
        List<Range> implementsRanges = ct.getImplementsClause().stream()
                .map(ic -> dis.treeToRange(ic))
                .collect(toList());
        List<Wrap> members = ct.getMembers().stream()
                .map(t -> corralTree(t, name, indent + 1))
                .filter(w -> w != null)
                .collect(toList());
        boolean hasConstructor = ct.getMembers().stream()
                .anyMatch(t -> t.getKind() == Tree.Kind.METHOD && ((MethodTree) t).getName().toString().equals("<init>"));
        Wrap wrap = Wrap.corralledType(compileSource, modRange, ct.getKind(), name, tpRange,
                extendsRange, implementsRanges, members, isClass && !hasConstructor, index, indent);
        return wrap;
    }
}
