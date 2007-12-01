/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.corba.se.internal.io;

public class IIOPInputStream {
    private static native Object allocateNewObject(Class aclass,
                                                   Class initclass)
        throws InstantiationException, IllegalAccessException;
    /* Create a pending exception.  This is needed to get around
     * the fact that the *Delegate methods do not explicitly
     * declare that they throw exceptions.
     *
     * This native methods creates an exception of the given type with
     * the given message string and posts it to the pending queue.
     */
    private static native void throwExceptionType(Class c, String message);

    /* The following native methods of the form set*Field are used
     * to set private, protected, and package private fields
     * of an Object.
     */
    private static native void setObjectField(Object o, Class c, String fieldName, String fieldSig, Object v);
    private static native void setBooleanField(Object o, Class c, String fieldName, String fieldSig, boolean v);
    private static native void setByteField(Object o, Class c, String fieldName, String fieldSig, byte v);
    private static native void setCharField(Object o, Class c, String fieldName, String fieldSig, char v);
    private static native void setShortField(Object o, Class c, String fieldName, String fieldSig, short v);
    private static native void setIntField(Object o, Class c, String fieldName, String fieldSig, int v);
    private static native void setLongField(Object o, Class c, String fieldName, String fieldSig, long v);
    private static native void setFloatField(Object o, Class c, String fieldName, String fieldSig, float v);
    private static native void setDoubleField(Object o, Class c, String fieldName, String fieldSig, double v);
    private static native void readObject(Object obj, Class asClass, Object ois);

    private static native void setObjectFieldOpt(Object o, long fieldID, Object v);
    private static native void setBooleanFieldOpt(Object o, long fieldID, boolean v);
    private static native void setByteFieldOpt(Object o, long fieldID, byte v);
    private static native void setCharFieldOpt(Object o, long fieldID, char v);
    private static native void setShortFieldOpt(Object o, long fieldID, short v);
    private static native void setIntFieldOpt(Object o, long fieldID, int v);
    private static native void setLongFieldOpt(Object o, long fieldID, long v);

    private static native void setFloatFieldOpt(Object o, long fieldID, float v);
    private static native void setDoubleFieldOpt(Object o, long fieldID, double v);
}
