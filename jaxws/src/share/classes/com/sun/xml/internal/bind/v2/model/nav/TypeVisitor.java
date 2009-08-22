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

package com.sun.xml.internal.bind.v2.model.nav;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;

/**
 * @author Kohsuke Kawaguchi
 */
abstract class TypeVisitor<T,P> {
    public final T visit( Type t, P param ) {
        assert t!=null;

        if (t instanceof Class)
            return onClass((Class)t,param);
        if (t instanceof ParameterizedType)
            return onParameterizdType( (ParameterizedType)t,param);
        if(t instanceof GenericArrayType)
            return onGenericArray((GenericArrayType)t,param);
        if(t instanceof WildcardType)
            return onWildcard((WildcardType)t,param);
        if(t instanceof TypeVariable)
            return onVariable((TypeVariable)t,param);

        // covered all the cases
        assert false;
        throw new IllegalArgumentException();
    }

    protected abstract T onClass(Class c, P param);
    protected abstract T onParameterizdType(ParameterizedType p, P param);
    protected abstract T onGenericArray(GenericArrayType g, P param);
    protected abstract T onVariable(TypeVariable v, P param);
    protected abstract T onWildcard(WildcardType w, P param);
}
