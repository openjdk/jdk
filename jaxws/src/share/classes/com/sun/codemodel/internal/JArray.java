/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.codemodel.internal;

import java.util.ArrayList;
import java.util.List;


/**
 * array creation and initialization.
 */
public final class JArray extends JExpressionImpl {

    private final JType type;
    private final JExpression size;
    private List<JExpression> exprs = null;

    /**
     * Add an element to the array initializer
     */
    public JArray add(JExpression e) {
        if (exprs == null)
            exprs = new ArrayList<JExpression>();
        exprs.add(e);
        return this;
    }

    JArray(JType type, JExpression size) {
        this.type = type;
        this.size = size;
    }

    public void generate(JFormatter f) {

        // generally we produce new T[x], but when T is an array type (T=T'[])
        // then new T'[][x] is wrong. It has to be new T'[x][].
        int arrayCount = 0;
        JType t = type;

        while( t.isArray() ) {
            t = t.elementType();
            arrayCount++;
        }

        f.p("new").g(t).p('[');
        if (size != null)
            f.g(size);
        f.p(']');

        for( int i=0; i<arrayCount; i++ )
            f.p("[]");

        if ((size == null) || (exprs != null))
            f.p('{');
        if (exprs != null) {
            f.g(exprs);
        } else {
            f.p(' ');
        }
        if ((size == null) || (exprs != null))
            f.p('}');
    }

}
