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



import com.sun.mirror.type.PrimitiveType;
import com.sun.mirror.util.TypeVisitor;
import com.sun.tools.apt.mirror.AptEnv;
import com.sun.tools.javac.code.Type;

import static com.sun.mirror.type.PrimitiveType.Kind.*;


/**
 * Implementation of PrimitiveType.
 */
@SuppressWarnings("deprecation")
class PrimitiveTypeImpl extends TypeMirrorImpl implements PrimitiveType {

    private final Kind kind;    // the kind of primitive


    PrimitiveTypeImpl(AptEnv env, Kind kind) {
        super(env, getType(env, kind));
        this.kind = kind;
    }


    /**
     * {@inheritDoc}
     */
    public Kind getKind() {
        return kind;
    }

    /**
     * {@inheritDoc}
     */
    public void accept(TypeVisitor v) {
        v.visitPrimitiveType(this);
    }


    /**
     * Returns the javac type corresponding to a kind of primitive type.
     */
    private static Type getType(AptEnv env, Kind kind) {
        switch (kind) {
        case BOOLEAN:   return env.symtab.booleanType;
        case BYTE:      return env.symtab.byteType;
        case SHORT:     return env.symtab.shortType;
        case INT:       return env.symtab.intType;
        case LONG:      return env.symtab.longType;
        case CHAR:      return env.symtab.charType;
        case FLOAT:     return env.symtab.floatType;
        case DOUBLE:    return env.symtab.doubleType;
        default:        throw new AssertionError();
        }
    }
}
