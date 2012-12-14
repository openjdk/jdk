/*
 * Copyright (c) 2003, 2012, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.javadoc.*;

import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.tree.JCTree.*;

/**
 * Represents an element of an annotation type.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Scott Seligman
 * @since 1.5
 */

public class AnnotationTypeElementDocImpl
        extends MethodDocImpl implements AnnotationTypeElementDoc {

    public AnnotationTypeElementDocImpl(DocEnv env, MethodSymbol sym) {
        super(env, sym);
    }

    public AnnotationTypeElementDocImpl(DocEnv env, MethodSymbol sym, TreePath treePath) {
        super(env, sym, treePath);
    }

    /**
     * Returns true, as this is an annotation type element.
     * (For legacy doclets, return false.)
     */
    public boolean isAnnotationTypeElement() {
        return !isMethod();
    }

    /**
     * Returns false.  Although this is technically a method, we don't
     * consider it one for this purpose.
     * (For legacy doclets, return true.)
     */
    public boolean isMethod() {
        return env.legacyDoclet;
    }

    /**
     * Returns false, even though this is indeed abstract.  See
     * MethodDocImpl.isAbstract() for the (il)logic behind this.
     */
    public boolean isAbstract() {
        return false;
    }

    /**
     * Returns the default value of this element.
     * Returns null if this element has no default.
     */
    public AnnotationValue defaultValue() {
        return (sym.defaultValue == null)
               ? null
               : new AnnotationValueImpl(env, sym.defaultValue);
    }
}
