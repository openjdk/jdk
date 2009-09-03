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
package com.sun.xml.internal.ws.model;

import java.util.*;
import java.util.logging.Logger;
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
public class FieldSignature {

    static String vms(Type t) {
        if (t instanceof Class || t instanceof ParameterizedType) {
            return "L"+fqcn(t)+";";
        } else if (t instanceof GenericArrayType) {
            return "["+vms(((GenericArrayType)t).getGenericComponentType());
        } else if (t instanceof TypeVariable) {
            // While creating wrapper bean fields, it doesn't create with TypeVariables
            // Otherwise, the type variable need to be declared in the wrapper bean class
            // return "T"+((TypeVariable)t).getName()+";";
            return "Ljava/lang/Object;";        // TODO bounds ??
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
                return fqcn(c.getDeclaringClass())+"."+c.getSimpleName();
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
        String sig = "<";
        for(Type t : p.getActualTypeArguments()) {
            sig += vms(t);
        }
        return sig+">";
    }

}
