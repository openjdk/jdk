/*
 * Copyright 1994-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.tools.java;

/**
 * This class represents an Java method type.
 * It overrides the relevant methods in class Type.
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 *
 * @author      Arthur van Hoff
 */
public final
class MethodType extends Type {
    /**
     * The return type.
     */
    Type returnType;

    /**
     * The argument types.
     */
    Type argTypes[];

    /**
     * Construct a method type. Use Type.tMethod to create
     * a new method type.
     * @see Type.tMethod
     */
    MethodType(String typeSig, Type returnType, Type argTypes[]) {
        super(TC_METHOD, typeSig);
        this.returnType = returnType;
        this.argTypes = argTypes;
    }

    public Type getReturnType() {
        return returnType;
    }

    public Type getArgumentTypes()[] {
        return argTypes;
    }

    public boolean equalArguments(Type t) {
        if (t.typeCode != TC_METHOD) {
            return false;
        }
        MethodType m = (MethodType)t;
        if (argTypes.length != m.argTypes.length) {
            return false;
        }
        for (int i = argTypes.length - 1 ; i >= 0 ; i--) {
            if (argTypes[i] != m.argTypes[i]) {
                return false;
            }
        }
        return true;
    }

    public int stackSize() {
        int n = 0;
        for (int i = 0 ; i < argTypes.length ; i++) {
            n += argTypes[i].stackSize();
        }
        return n;
    }

    public String typeString(String id, boolean abbrev, boolean ret) {
        StringBuffer buf = new StringBuffer();
        buf.append(id);
        buf.append('(');
        for (int i = 0 ; i < argTypes.length ; i++) {
            if (i > 0) {
                buf.append(", ");
            }
            buf.append(argTypes[i].typeString("", abbrev, ret));
        }
        buf.append(')');

        return ret ? getReturnType().typeString(buf.toString(), abbrev, ret) : buf.toString();
    }
}
