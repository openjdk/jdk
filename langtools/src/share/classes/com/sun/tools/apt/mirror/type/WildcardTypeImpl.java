/*
 * Copyright 2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.apt.mirror.type;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import com.sun.mirror.declaration.*;
import com.sun.mirror.type.*;
import com.sun.mirror.util.TypeVisitor;
import com.sun.tools.apt.mirror.AptEnv;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Symbol.*;


/**
 * Implementation of WildcardType
 */
@SuppressWarnings("deprecation")
public class WildcardTypeImpl extends TypeMirrorImpl implements WildcardType {

    protected Type.WildcardType type;

    WildcardTypeImpl(AptEnv env, Type.WildcardType type) {
        super(env, type);
        this.type = type;
    }


    /**
     * Returns the string form of a wildcard type, consisting of "?"
     * and any "extends" or "super" clause.
     * Delimiting brackets are not included.  Class names are qualified.
     */
    public String toString() {
        return toString(env, type);
    }

    /**
     * {@inheritDoc}
     */
    public Collection<ReferenceType> getUpperBounds() {
        return type.isSuperBound()
                ? Collections.<ReferenceType>emptyList()
                : typeToCollection(type.type);
    }

    /**
     * {@inheritDoc}
     */
    public Collection<ReferenceType> getLowerBounds() {
        return type.isExtendsBound()
                ? Collections.<ReferenceType>emptyList()
                : typeToCollection(type.type);
    }

    /**
     * Gets the ReferenceType for a javac Type object, and returns
     * it in a singleton collection.  If type is null, returns an empty
     * collection.
     */
    private Collection<ReferenceType> typeToCollection(Type type) {
        ArrayList<ReferenceType> res = new ArrayList<ReferenceType>(1);
        if (type != null) {
            res.add((ReferenceType) env.typeMaker.getType(type));
        }
        return res;
    }

    /**
     * {@inheritDoc}
     */
    public void accept(TypeVisitor v) {
        v.visitWildcardType(this);
    }


    /**
     * Returns the string form of a wildcard type, consisting of "?"
     * and any "extends" or "super" clause.
     * See {@link #toString()} for details.
     */
    static String toString(AptEnv env, Type.WildcardType wildThing) {
        return wildThing.toString();
    }
}
