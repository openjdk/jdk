/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.io;

import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;

import static jdk.internal.event.SerializationMisdeclarationEvent.*;
import static java.lang.reflect.Modifier.*;

final class SerializationMisdeclarationChecker {

    private static final String SUID_NAME = "serialVersionUID";
    private static final String SERIAL_PERSISTENT_FIELDS_NAME = "serialPersistentFields";
    private static final String WRITE_OBJECT_NAME = "writeObject";
    private static final String READ_OBJECT_NAME = "readObject";
    private static final String READ_OBJECT_NO_DATA_NAME = "readObjectNoData";
    private static final String WRITE_REPLACE_NAME = "writeReplace";
    private static final String READ_RESOLVE_NAME = "readResolve";

    private static final Class<?>[] WRITE_OBJECT_PARAM_TYPES = {ObjectOutputStream.class};
    private static final Class<?>[] READ_OBJECT_PARAM_TYPES = {ObjectInputStream.class};
    private static final Class<?>[] READ_OBJECT_NO_DATA_PARAM_TYPES = {};
    private static final Class<?>[] WRITE_REPLACE_PARAM_TYPES = {};
    private static final Class<?>[] READ_RESOLVE_PARAM_TYPES = {};

    private final Class<?> cl;

    SerializationMisdeclarationChecker(Class<?> cl) {
        this.cl = cl;
    }

    @SuppressWarnings("removal")
    void checkMisdeclarations() {
        AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
            privilegedCheckSerialVersionUID();
            privilegedCheckSerialPersistentFields();

            privilegedCheckPrivateMethod(WRITE_OBJECT_NAME, WRITE_OBJECT_PARAM_TYPES, Void.TYPE);
            privilegedCheckPrivateMethod(READ_OBJECT_NAME, READ_OBJECT_PARAM_TYPES, Void.TYPE);
            privilegedCheckPrivateMethod(READ_OBJECT_NO_DATA_NAME, READ_OBJECT_NO_DATA_PARAM_TYPES, Void.TYPE);

            privilegedCheckAccessibleMethod(WRITE_REPLACE_NAME, WRITE_REPLACE_PARAM_TYPES, Object.class);
            privilegedCheckAccessibleMethod(READ_RESOLVE_NAME, READ_RESOLVE_PARAM_TYPES, Object.class);

            return null;
        });
    }

    private void privilegedCheckSerialVersionUID() {
        Field f = declaredField(cl, SUID_NAME);
        if (f == null) {
            if (isOrdinaryClass()) {
                commitEvent(SUID_NAME +
                        " should be declared explicitly as a private static final long field");
            }
            return;
        }
        if (cl.isEnum()) {
            commitEvent(SUID_NAME +
                    " in an enum class is not effective");
        }
        if (!isPrivate(f)) {
            commitEvent(SUID_NAME +
                    " should be private");
        }
        if (!isStatic(f)) {
            commitEvent(SUID_NAME +
                    " must be static to be effective");
        }
        if (!isFinal(f)) {
            commitEvent(SUID_NAME +
                    " must be final to be effective");
        }
        if (f.getType() != Long.TYPE) {
            commitEvent(SUID_NAME +
                    " must be of type long to be effective");
        }
    }

    private void privilegedCheckSerialPersistentFields() {
        Field f = declaredField(cl, SERIAL_PERSISTENT_FIELDS_NAME);
        if (f == null) {
            return;
        }
        if (cl.isRecord()) {
            commitEvent(SERIAL_PERSISTENT_FIELDS_NAME +
                    " in a record class is not effective");
        } else if (cl.isEnum()) {
            commitEvent(SERIAL_PERSISTENT_FIELDS_NAME +
                    " in an enum class is not effective");
        }
        if (!isPrivate(f)) {
            commitEvent(SERIAL_PERSISTENT_FIELDS_NAME +
                    " must be private to be effective");
        }
        if (!isStatic(f)) {
            commitEvent(SERIAL_PERSISTENT_FIELDS_NAME +
                    " must be static to be effective");
        }
        if (!isFinal(f)) {
            commitEvent(SERIAL_PERSISTENT_FIELDS_NAME +
                    " must be final to be effective");
        }
        if (f.getType() != ObjectStreamField[].class) {
            commitEvent(SERIAL_PERSISTENT_FIELDS_NAME +
                    " should be of type ObjectStreamField[]");
        }
        if (!isStatic(f)) {
            return;
        }
        f.setAccessible(true);
        Object spf = objectFromStatic(f);
        if (spf == null) {
            commitEvent(SERIAL_PERSISTENT_FIELDS_NAME +
                    " must be non-null to be effective");
            return;
        }
        if (!(spf instanceof ObjectStreamField[])) {
            commitEvent(SERIAL_PERSISTENT_FIELDS_NAME +
                    " must be an instance of ObjectStreamField[] to be effective");
        }
    }

    private void privilegedCheckPrivateMethod(String name, Class<?>[] paramTypes, Class<?> retType) {
        for (Method m : cl.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                privilegedCheckPrivateMethod(m, paramTypes, retType);
            }
        }
    }

    private void privilegedCheckPrivateMethod(Method m, Class<?>[] paramTypes, Class<?> retType) {
        if (cl.isEnum()) {
            commitEvent("method " + m + " on an enum class is not effective");
        } else if (cl.isRecord()) {
            commitEvent("method " + m + " on an record class is not effective");
        }
        if (!isPrivate(m)) {
            commitEvent("method " + m + " must be private to be effective");
        }
        if (isStatic(m)) {
            commitEvent("method " + m + " must be non-static to be effective");
        }
        if (m.getReturnType() != retType) {
            commitEvent("method " + m + " must have return type " + retType + " to be effective");
        }
        if (!Arrays.equals(m.getParameterTypes(), paramTypes)) {
            commitEvent("method " + m + " must have parameter types " + Arrays.toString(paramTypes) + " to be effective");
        }
    }

    private void privilegedCheckAccessibleMethod(String name,
            Class<?>[] paramTypes, Class<?> retType) {
        for (Class<?> cls = cl; cls != null; cls = cls.getSuperclass()) {
            for (Method m : cls.getDeclaredMethods()) {
                if (m.getName().equals(name)) {
                    privilegedCheckAccessibleMethod(cls, m, paramTypes, retType);
                }
            }
        }
    }

    private void privilegedCheckAccessibleMethod(Class<?> cls, Method m,
            Class<?>[] paramTypes, Class<?> retType) {
        if (cls.isEnum()) {
            commitEvent("method " + m + " on an enum class is not effective");
        }
        if (isAbstract(m)) {
            commitEvent("method " + m + " must be non-abstract to be effective");
        }
        if (isStatic(m)) {
            commitEvent("method " + m + " must be non-static to be effective");
        }
        if (m.getReturnType() != retType) {
            commitEvent("method " + m + " must have return type " + retType + " to be effective");
        }
        if (!Arrays.equals(m.getParameterTypes(), paramTypes)) {
            commitEvent("method " + m + " must have parameter types " + Arrays.toString(paramTypes) + " to be effective");
        }
        if (isPrivate(m) && cl != cls
                || isPackageProtected(m) && !isSamePackage(cl, cls)) {
            commitEvent("method " + m + " is not accessible");
        }
    }

    private static boolean isSamePackage(Class<?> cl0, Class<?> cl1) {
        return cl0.getClassLoader() == cl1.getClassLoader()
                && cl0.getPackageName().equals(cl1.getPackageName());
    }

    private boolean isOrdinaryClass() {
        /* class Enum and class Record are not considered ordinary classes */
        return !(cl.isRecord() || cl.isEnum() || cl.isArray()
                || Enum.class == cl || Record.class == cl
                || Proxy.isProxyClass(cl));
    }

    private static boolean isPrivate(Member m) {
        return (m.getModifiers() & PRIVATE) != 0;
    }

    private static boolean isPackageProtected(Member m) {
        return (m.getModifiers() & (PRIVATE | PROTECTED | PUBLIC)) == 0;
    }

    private static boolean isAbstract(Member m) {
        return (m.getModifiers() & ABSTRACT) != 0;
    }

    private static boolean isFinal(Member m) {
        return (m.getModifiers() & FINAL) != 0;
    }

    private static boolean isStatic(Member m) {
        return (m.getModifiers() & STATIC) != 0;
    }

    private static Field declaredField(Class<?> cl, String name) {
        try {
            return cl.getDeclaredField(name);
        } catch (NoSuchFieldException ignored) {
        }
        return null;
    }

    private static Object objectFromStatic(Field f) {
        try {
            return f.get(null);
        } catch (IllegalAccessException ignored) {
        }
        return null;
    }

    private void commitEvent(String msg, Object... args) {
        commitEvent(cl, msg);
    }

    private static void commitEvent(Class<?> cls, String msg) {
        commit(timestamp(), cls, msg);
    }

}
