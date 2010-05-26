/*
 * Copyright (c) 2003, 2008, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javadoc;

import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Position;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.comp.MemberEnter;
import com.sun.tools.javac.tree.JCTree.*;

/**
 *  Javadoc's own memberEnter phase does a few things above and beyond that
 *  done by javac.
 *  @author Neal Gafter
 */
class JavadocMemberEnter extends MemberEnter {
    public static JavadocMemberEnter instance0(Context context) {
        MemberEnter instance = context.get(memberEnterKey);
        if (instance == null)
            instance = new JavadocMemberEnter(context);
        return (JavadocMemberEnter)instance;
    }

    public static void preRegister(final Context context) {
        context.put(memberEnterKey, new Context.Factory<MemberEnter>() {
               public MemberEnter make() {
                   return new JavadocMemberEnter(context);
               }
        });
    }

    final DocEnv docenv;

    protected JavadocMemberEnter(Context context) {
        super(context);
        docenv = DocEnv.instance(context);
    }

    public void visitMethodDef(JCMethodDecl tree) {
        super.visitMethodDef(tree);
        MethodSymbol meth = tree.sym;
        if (meth == null || meth.kind != Kinds.MTH) return;
        String docComment = env.toplevel.docComments.get(tree);
        Position.LineMap lineMap = env.toplevel.lineMap;
        if (meth.isConstructor())
            docenv.makeConstructorDoc(meth, docComment, tree, lineMap);
        else if (isAnnotationTypeElement(meth))
            docenv.makeAnnotationTypeElementDoc(meth, docComment, tree, lineMap);
        else
            docenv.makeMethodDoc(meth, docComment, tree, lineMap);
    }

    public void visitVarDef(JCVariableDecl tree) {
        super.visitVarDef(tree);
        if (tree.sym != null &&
                tree.sym.kind == Kinds.VAR &&
                !isParameter(tree.sym)) {
            String docComment = env.toplevel.docComments.get(tree);
            Position.LineMap lineMap = env.toplevel.lineMap;
            docenv.makeFieldDoc(tree.sym, docComment, tree, lineMap);
        }
    }

    private static boolean isAnnotationTypeElement(MethodSymbol meth) {
        return ClassDocImpl.isAnnotationType(meth.enclClass());
    }

    private static boolean isParameter(VarSymbol var) {
        return (var.flags() & Flags.PARAMETER) != 0;
    }
}
