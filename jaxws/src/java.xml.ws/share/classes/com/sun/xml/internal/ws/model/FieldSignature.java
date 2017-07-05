/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.model;

import java.lang.reflect.*;

/**
 * Creates vm signature string from Type
 *
 * TypeSignature: Z | C | B | S | I | F | J | D | FieldTypeSignature
 * FieldTypeSignature: ClassTypeSignature | [ TypeSignature | TypeVar
 * ClassTypeSignature: L Id ( / Id )* TypeArgs? ( . Id TypeArgs? )* ;
 * TypeArgs: < TypeArg+ >
 * TypeArg: * | ( + | - )? FieldTypeSignature
 * TypeVar: T Id ;
 *
 * @author Jitendra Kotamraju
 */
final class FieldSignature {

    static String vms(Type t) {
        if (t instanceof Class && ((Class)t).isPrimitive()) {
            Class c = (Class)t;
            if (c == Integer.TYPE) {
                return "I";
            } else if (c == Void.TYPE) {
                return "V";
            } else if (c == Boolean.TYPE) {
                return "Z";
            } else if (c == Byte.TYPE) {
                return "B";
            } else if (c == Character.TYPE) {
                return "C";
            } else if (c == Short.TYPE) {
                return "S";
            } else if (c == Double.TYPE) {
                return "D";
            } else if (c == Float.TYPE) {
                return "F";
            } else if (c == Long.TYPE) {
                return "J";
            }
        } else if (t instanceof Class && ((Class)t).isArray()) {
            return "["+vms(((Class)t).getComponentType());
        } else if (t instanceof Class || t instanceof ParameterizedType) {
            return "L"+fqcn(t)+";";
        } else if (t instanceof GenericArrayType) {
            return "["+vms(((GenericArrayType)t).getGenericComponentType());
        } else if (t instanceof TypeVariable) {
            // While creating wrapper bean fields, it doesn't create with TypeVariables
            // Otherwise, the type variable need to be declared in the wrapper bean class
            // return "T"+((TypeVariable)t).getName()+";";
            return "Ljava/lang/Object;";
        } else if (t instanceof WildcardType) {
            WildcardType w = (WildcardType)t;
            if (w.getLowerBounds().length > 0) {
                return "-"+vms(w.getLowerBounds()[0]);
            } else if (w.getUpperBounds().length > 0) {
                Type wt = w.getUpperBounds()[0];
                if (wt.equals(Object.class)) {
                    return "*";
                } else {
                    return "+"+vms(wt);
                }
            }
        }
        throw new IllegalArgumentException("Illegal vms arg " + t);
    }

    private static String fqcn(Type t) {
        if (t instanceof Class) {
            Class c = (Class)t;
            if (c.getDeclaringClass() == null) {
                return c.getName().replace('.', '/');
            } else {
                return fqcn(c.getDeclaringClass())+"$"+c.getSimpleName();
            }
        } else if (t instanceof ParameterizedType) {
            ParameterizedType p = (ParameterizedType)t;
            if (p.getOwnerType() == null) {
                return fqcn(p.getRawType())+args(p);
            } else {
                assert p.getRawType() instanceof Class;
                return fqcn(p.getOwnerType())+"."+
                        ((Class)p.getRawType()).getSimpleName()+args(p);
            }
        }
        throw new IllegalArgumentException("Illegal fqcn arg = "+t);
    }

    private static String args(ParameterizedType p) {
        StringBuilder sig = new StringBuilder("<");
        for(Type t : p.getActualTypeArguments()) {
            sig.append(vms(t));
        }
        return sig.append(">").toString();
    }

}
