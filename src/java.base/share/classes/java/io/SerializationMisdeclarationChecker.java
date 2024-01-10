/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

    /*
     * The sharing of a single Class<?>[] instance here is just to avoid wasting
     * space, and should not be considered as a conceptual sharing of types.
     */
    private static final Class<?>[] READ_OBJECT_NO_DATA_PARAM_TYPES = {};
    private static final Class<?>[] WRITE_REPLACE_PARAM_TYPES = READ_OBJECT_NO_DATA_PARAM_TYPES;
    private static final Class<?>[] READ_RESOLVE_PARAM_TYPES = READ_OBJECT_NO_DATA_PARAM_TYPES;

    static void checkMisdeclarations(Class<?> cl) {
        checkSerialVersionUID(cl);
        checkSerialPersistentFields(cl);

        checkPrivateMethod(cl, WRITE_OBJECT_NAME,
                WRITE_OBJECT_PARAM_TYPES, Void.TYPE);
        checkPrivateMethod(cl, READ_OBJECT_NAME,
                READ_OBJECT_PARAM_TYPES, Void.TYPE);
        checkPrivateMethod(cl, READ_OBJECT_NO_DATA_NAME,
                READ_OBJECT_NO_DATA_PARAM_TYPES, Void.TYPE);

        checkAccessibleMethod(cl, WRITE_REPLACE_NAME,
                WRITE_REPLACE_PARAM_TYPES, Object.class);
        checkAccessibleMethod(cl, READ_RESOLVE_NAME,
                READ_RESOLVE_PARAM_TYPES, Object.class);
    }

    private static void checkSerialVersionUID(Class<?> cl) {
        Field f = privilegedDeclaredField(cl, SUID_NAME);
        if (f == null) {
            if (isOrdinaryClass(cl)) {
                commitEvent(cl, SUID_NAME + " should be declared explicitly" +
                        " as a private static final long field");
            }
            return;
        }
        if (cl.isEnum()) {
            commitEvent(cl, SUID_NAME + " should not be declared in an enum class");
        }
        if (!isPrivate(f)) {
            commitEvent(cl, SUID_NAME + " should be private");
        }
        if (!isStatic(f)) {
            commitEvent(cl, SUID_NAME + " must be static");
        }
        if (!isFinal(f)) {
            commitEvent(cl, SUID_NAME + " must be final");
        }
        if (f.getType() != Long.TYPE) {
            commitEvent(cl, SUID_NAME + " must be of type long");
        }
    }

    private static void checkSerialPersistentFields(Class<?> cl) {
        Field f = privilegedDeclaredField(cl, SERIAL_PERSISTENT_FIELDS_NAME);
        if (f == null) {
            return;
        }
        if (cl.isRecord()) {
            commitEvent(cl, SERIAL_PERSISTENT_FIELDS_NAME +
                    " should not be declared in a record class");
        } else if (cl.isEnum()) {
            commitEvent(cl, SERIAL_PERSISTENT_FIELDS_NAME +
                    " should not be declared in an enum class");
        }
        if (!isPrivate(f)) {
            commitEvent(cl, SERIAL_PERSISTENT_FIELDS_NAME + " must be private");
        }
        if (!isStatic(f)) {
            commitEvent(cl, SERIAL_PERSISTENT_FIELDS_NAME + " must be static");
        }
        if (!isFinal(f)) {
            commitEvent(cl, SERIAL_PERSISTENT_FIELDS_NAME + " must be final");
        }
        if (f.getType() != ObjectStreamField[].class) {
            commitEvent(cl, SERIAL_PERSISTENT_FIELDS_NAME +
                    " should be of type ObjectStreamField[]");
        }
        if (!isStatic(f)) {
            return;
        }
        f.setAccessible(true);
        Object spf = objectFromStatic(f);
        if (spf == null) {
            commitEvent(cl, SERIAL_PERSISTENT_FIELDS_NAME + " should be non-null");
            return;
        }
        if (!(spf instanceof ObjectStreamField[])) {
            commitEvent(cl, SERIAL_PERSISTENT_FIELDS_NAME +
                    " must be an instance of ObjectStreamField[]");
        }
    }

    private static void checkPrivateMethod(Class<?> cl,
            String name, Class<?>[] paramTypes, Class<?> retType) {
        for (Method m : privilegedDeclaredMethods(cl)) {
            if (m.getName().equals(name)) {
                checkPrivateMethod(cl, m, paramTypes, retType);
            }
        }
    }

    private static void checkPrivateMethod(Class<?> cl,
            Method m, Class<?>[] paramTypes, Class<?> retType) {
        if (cl.isEnum()) {
            commitEvent(cl, "method " + m + " should not be declared in an enum class");
        } else if (cl.isRecord()) {
            commitEvent(cl, "method " + m + " should not be declared in a record class");
        }
        if (!isPrivate(m)) {
            commitEvent(cl, "method " + m + " must be private");
        }
        if (isStatic(m)) {
            commitEvent(cl, "method " + m + " must be non-static");
        }
        if (m.getReturnType() != retType) {
            commitEvent(cl, "method " + m + " must have return type " + retType);
        }
        if (!Arrays.equals(m.getParameterTypes(), paramTypes)) {
            commitEvent(cl, "method " + m + " must have parameter types " + Arrays.toString(paramTypes));
        }
    }

    private static void checkAccessibleMethod(Class<?> cl,
            String name, Class<?>[] paramTypes, Class<?> retType) {
        for (Class<?> superCl = cl; superCl != null; superCl = superCl.getSuperclass()) {
            for (Method m : privilegedDeclaredMethods(superCl)) {
                if (m.getName().equals(name)) {
                    checkAccessibleMethod(cl, superCl, m, paramTypes, retType);
                }
            }
        }
    }

    private static void checkAccessibleMethod(Class<?> cl,
            Class<?> superCl, Method m, Class<?>[] paramTypes, Class<?> retType) {
        if (superCl.isEnum()) {
            commitEvent(cl, "method " + m + " should not be declared in an enum class");
        }
        if (isAbstract(m)) {
            commitEvent(cl, "method " + m + " must be non-abstract");
        }
        if (isStatic(m)) {
            commitEvent(cl, "method " + m + " must be non-static");
        }
        if (m.getReturnType() != retType) {
            commitEvent(cl, "method " + m + " must have return type " + retType);
        }
        if (!Arrays.equals(m.getParameterTypes(), paramTypes)) {
            commitEvent(cl, "method " + m + " must have parameter types " + Arrays.toString(paramTypes));
        }
        if (isPrivate(m) && cl != superCl
                || isPackageProtected(m) && !isSamePackage(cl, superCl)) {
            commitEvent(cl, "method " + m + " is not accessible");
        }
    }

    private static boolean isSamePackage(Class<?> cl0, Class<?> cl1) {
        return cl0.getClassLoader() == cl1.getClassLoader()
                && cl0.getPackageName().equals(cl1.getPackageName());
    }

    private static boolean isOrdinaryClass(Class<?> cl) {
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

    @SuppressWarnings("removal")
    private static Field privilegedDeclaredField(Class<?> cl, String name) {
        if (System.getSecurityManager() == null) {
            return declaredField(cl, name);
        }
        return AccessController.doPrivileged((PrivilegedAction<Field>) () ->
                declaredField(cl, name));
    }

    private static Field declaredField(Class<?> cl, String name) {
        try {
            return cl.getDeclaredField(name);
        } catch (NoSuchFieldException ignored) {
        }
        return null;
    }

    @SuppressWarnings("removal")
    private static Method[] privilegedDeclaredMethods(Class<?> cl) {
        if (System.getSecurityManager() == null) {
            return cl.getDeclaredMethods();
        }
        return AccessController.doPrivileged(
                (PrivilegedAction<Method[]>) cl::getDeclaredMethods);
    }

    private static Object objectFromStatic(Field f) {
        try {
            return f.get(null);
        } catch (IllegalAccessException ignored) {
        }
        return null;
    }

    private static void commitEvent(Class<?> cl, String msg) {
        commit(timestamp(), cl, msg);
    }

}
