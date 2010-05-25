/*
 * Copyright (c) 2001, 2008, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;
import javax.tools.JavaFileObject;

/**
 *  Javadoc's own enter phase does a few things above and beyond that
 *  done by javac.
 *  @author Neal Gafter
 */
public class JavadocEnter extends Enter {
    public static JavadocEnter instance0(Context context) {
        Enter instance = context.get(enterKey);
        if (instance == null)
            instance = new JavadocEnter(context);
        return (JavadocEnter)instance;
    }

    public static void preRegister(final Context context) {
        context.put(enterKey, new Context.Factory<Enter>() {
               public Enter make() {
                   return new JavadocEnter(context);
               }
        });
    }

    protected JavadocEnter(Context context) {
        super(context);
        messager = Messager.instance0(context);
        docenv = DocEnv.instance(context);
    }

    final Messager messager;
    final DocEnv docenv;

    public void main(List<JCCompilationUnit> trees) {
        // count all Enter errors as warnings.
        int nerrors = messager.nerrors;
        super.main(trees);
        messager.nwarnings += (messager.nerrors - nerrors);
        messager.nerrors = nerrors;
    }

    public void visitTopLevel(JCCompilationUnit tree) {
        super.visitTopLevel(tree);
        if (tree.sourcefile.isNameCompatible("package-info", JavaFileObject.Kind.SOURCE)) {
            String comment = tree.docComments.get(tree);
            docenv.makePackageDoc(tree.packge, comment, tree);
        }
    }

    public void visitClassDef(JCClassDecl tree) {
        super.visitClassDef(tree);
        if (tree.sym != null && tree.sym.kind == Kinds.TYP) {
            if (tree.sym == null) return;
            String comment = env.toplevel.docComments.get(tree);
            ClassSymbol c = tree.sym;
            docenv.makeClassDoc(c, comment, tree, env.toplevel.lineMap);
        }
    }

    /** Don't complain about a duplicate class. */
    protected void duplicateClass(DiagnosticPosition pos, ClassSymbol c) {}

}
