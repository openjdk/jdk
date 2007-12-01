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


public class IIOPOutputStream {

    /* Create a pending exception.  This is needed to get around
     * the fact that the *Delegate methods do not explicitly
     * declare that they throw exceptions.
     *
     * This native method creates an exception of the given type with
     * the given message string and posts it to the pending queue.
     */
    private static native void throwExceptionType(Class c, String message);

    private static native Object getObjectFieldOpt(Object o, long fieldID);
    private static native boolean getBooleanFieldOpt(Object o, long fieldID);
    private static native byte getByteFieldOpt(Object o, long fieldID);
    private static native char getCharFieldOpt(Object o, long fieldID);
    private static native short getShortFieldOpt(Object o, long fieldID);
    private static native int getIntFieldOpt(Object o, long fieldID);
    private static native long getLongFieldOpt(Object o, long fieldID);
    private static native float getFloatFieldOpt(Object o, long fieldID);
    private static native double getDoubleFieldOpt(Object o, long fieldID);

    private static native void writeObject(Object obj, Class asClass, Object oos) throws IllegalAccessException;
}
