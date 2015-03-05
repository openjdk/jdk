/*
 * Copyright (c) 2006, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.api;


import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.util.DefinedBy;
import com.sun.tools.javac.util.DefinedBy.Api;
import com.sun.tools.javac.util.Assert;

/**
 * Provides an implementation of Scope.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 *
 * @author Jonathan Gibbons;
 */
public class JavacScope implements com.sun.source.tree.Scope {

    static JavacScope create(Env<AttrContext> env) {
        if (env.outer == null || env.outer == env) {
            //the "top-level" scope needs to return both imported and defined elements
            //see test CheckLocalElements
            return new JavacScope(env) {
                @Override @DefinedBy(Api.COMPILER_TREE)
                public Iterable<? extends Element> getLocalElements() {
                    return env.toplevel.namedImportScope.getSymbols();
                }
            };
        } else {
            return new JavacScope(env);
        }
    }

    protected final Env<AttrContext> env;

    private JavacScope(Env<AttrContext> env) {
        this.env = Assert.checkNonNull(env);
    }

    @DefinedBy(Api.COMPILER_TREE)
    public JavacScope getEnclosingScope() {
        if (env.outer != null && env.outer != env) {
            return create(env.outer);
        } else {
            // synthesize an outermost "star-import" scope
            return new JavacScope(env) {
                public boolean isStarImportScope() {
                    return true;
                }
                @DefinedBy(Api.COMPILER_TREE)
                public JavacScope getEnclosingScope() {
                    return null;
                }
                @DefinedBy(Api.COMPILER_TREE)
                public Iterable<? extends Element> getLocalElements() {
                    return env.toplevel.starImportScope.getSymbols();
                }
            };
        }
    }

    @DefinedBy(Api.COMPILER_TREE)
    public TypeElement getEnclosingClass() {
        // hide the dummy class that javac uses to enclose the top level declarations
        return (env.outer == null || env.outer == env ? null : env.enclClass.sym);
    }

    @DefinedBy(Api.COMPILER_TREE)
    public ExecutableElement getEnclosingMethod() {
        return (env.enclMethod == null ? null : env.enclMethod.sym);
    }

    @DefinedBy(Api.COMPILER_TREE)
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
