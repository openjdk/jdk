/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.javac.api;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.Iterator;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

import com.sun.source.tree.Tree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.MemberEnter;
import com.sun.tools.javac.comp.Resolve;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeCopier;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Log;

import static com.sun.source.tree.Tree.Kind.*;


/**
 * Provides an implementation of Scope.
 *
 * <p><b>This is NOT part of any API supported by Sun Microsystems.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 *
 * @author Jonathan Gibbons;
 */
public class JavacScope implements com.sun.source.tree.Scope {
    protected final Env<AttrContext> env;

    /** Creates a new instance of JavacScope */
    JavacScope(Env<AttrContext> env) {
        env.getClass(); // null-check
        this.env = env;
    }

    public JavacScope getEnclosingScope() {
        if (env.outer != null && env.outer != env)
            return  new JavacScope(env.outer);
        else {
            // synthesize an outermost "star-import" scope
            return new JavacScope(env) {
                public boolean isStarImportScope() {
                    return true;
                }
                public JavacScope getEnclosingScope() {
                    return null;
                }
                public Iterable<? extends Element> getLocalElements() {
                    return env.toplevel.starImportScope.getElements();
                }
            };
        }
    }

    public TypeElement getEnclosingClass() {
        // hide the dummy class that javac uses to enclose the top level declarations
        return (env.outer == null || env.outer == env ? null : env.enclClass.sym);
    }

    public ExecutableElement getEnclosingMethod() {
        return (env.enclMethod == null ? null : env.enclMethod.sym);
    }

    public Iterable<? extends Element> getLocalElements() {
        return env.info.getLocalElements();
    }

    public Env<AttrContext> getEnv() {
        return env;
    }

    public boolean isStarImportScope() {
        return false;
    }

    public boolean equals(Object other) {
        if (other instanceof JavacScope) {
            JavacScope s = (JavacScope) other;
            return (env.equals(s.env)
                && isStarImportScope() == s.isStarImportScope());
        } else
            return false;
    }

    public int hashCode() {
        return env.hashCode() + (isStarImportScope() ? 1 : 0);
    }

    public String toString() {
        return "JavacScope[env=" + env + ",starImport=" + isStarImportScope() + "]";
    }
}
