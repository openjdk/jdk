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


import com.sun.mirror.declaration.*;
import com.sun.mirror.type.*;
import com.sun.tools.apt.mirror.AptEnv;
import com.sun.tools.javac.code.*;


/**
 * Implementation of TypeMirror
 */
@SuppressWarnings("deprecation")
public abstract class TypeMirrorImpl implements TypeMirror {

    protected final AptEnv env;
    public final Type type;


    protected TypeMirrorImpl(AptEnv env, Type type) {
        this.env = env;
        this.type = type;
    }


    /**
     * {@inheritDoc}
     */
    public String toString() {
        return type.toString();
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object obj) {
        if (obj instanceof TypeMirrorImpl) {
            TypeMirrorImpl that = (TypeMirrorImpl) obj;
            return env.jctypes.isSameType(this.type, that.type);
        } else {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    public int hashCode() {
        return Types.hashCode(type);
    }
}
