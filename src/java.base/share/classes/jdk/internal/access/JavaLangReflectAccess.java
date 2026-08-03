/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.access;

import java.lang.reflect.*;
import jdk.internal.reflect.*;

/** An interface which gives privileged packages Java-level access to
    internals of java.lang.reflect. Use as a last resort! */
public interface JavaLangReflectAccess {
    /**
     * Creates a new root constructor from the original one, with
     * a custom accessor. Used by serialization hooks.
     */
    <T> Constructor<T> newConstructorWithAccessor(Constructor<T> original, ConstructorAccessor accessor);

    /** Gets the byte[] that encodes TypeAnnotations on an Executable. */
    public byte[] getExecutableTypeAnnotationBytes(Executable ex);

    /** Gets the shared array of parameter types of an Executable. */
    public Class<?>[] getExecutableSharedParameterTypes(Executable ex);

    /** Gets the shared array of exception types of an Executable. */
    public Class<?>[] getExecutableSharedExceptionTypes(Executable ex);

    // Copying routines, needed to quickly fabricate new Field,
    // Method, and Constructor objects from templates

    /** Makes a "child" copy of a Method */
    public Method      copyMethod(Method arg);

    /** Makes a "child" copy of a Field */
    public Field       copyField(Field arg);

    /** Makes a "child" copy of a Constructor */
    public <T> Constructor<T> copyConstructor(Constructor<T> arg);

    /** Gets the root of the given AccessibleObject object; null if arg is the root */
    public <T extends AccessibleObject> T getRoot(T obj);

    /** Tests if this is a trusted final field */
    public boolean isTrustedFinalField(Field f);

    /** Returns a new instance created by the given constructor with access check */
    public <T> T newInstance(Constructor<T> ctor, Object[] args, Class<?> caller)
        throws IllegalAccessException, InstantiationException, InvocationTargetException;

    /**
     * Check that the caller is allowed to unreflect for mutation a final instance field
     * in a class that is not a record or hidden class.
     */
    void checkAllowedToUnreflectFinalSetter(Class<?> caller, Field f) throws IllegalAccessException;
}
