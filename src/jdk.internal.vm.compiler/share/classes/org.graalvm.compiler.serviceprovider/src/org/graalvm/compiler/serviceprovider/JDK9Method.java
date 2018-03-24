/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.compiler.serviceprovider;

import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * Reflection based access to API introduced by JDK 9. This allows the API to be used in code that
 * must be compiled on a JDK prior to 9.
 */
public final class JDK9Method {

    private static int getJavaSpecificationVersion() {
        String value = System.getProperty("java.specification.version");
        if (value.startsWith("1.")) {
            value = value.substring(2);
        }
        return Integer.parseInt(value);
    }

    /**
     * The integer value corresponding to the value of the {@code java.specification.version} system
     * property after any leading {@code "1."} has been stripped.
     */
    public static final int JAVA_SPECIFICATION_VERSION = getJavaSpecificationVersion();

    public static MethodHandle lookupMethodHandle(Class<?> declaringClass, String name, Class<?>... parameterTypes) {
        try {
            return MethodHandles.lookup().unreflect(declaringClass.getMethod(name, parameterTypes));
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }

    private static Method lookupMethod(Class<?> declaringClass, String name, Class<?>... parameterTypes) {
        try {
            return declaringClass.getMethod(name, parameterTypes);
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }

    /**
     * Determines if the Java runtime is version 8 or earlier.
     */
    public static final boolean Java8OrEarlier = JAVA_SPECIFICATION_VERSION <= 8;

    /**
     * {@code Class.getModule()}.
     */
    private static final MethodHandle getModuleHandle;

    public static Object getModule(Class<?> clazz) {
        try {
            return getModuleHandle.invoke(clazz);
        } catch (Throwable throwable) {
            throw new InternalError(throwable);
        }
    }

    /**
     * {@code java.lang.Module.getPackages()}.
     */
    private static final MethodHandle getPackages;

    public static Set<String> getPackages(Object module) {
        try {
            return (Set<String>) getPackages.invoke(module);
        } catch (Throwable throwable) {
            throw new InternalError(throwable);
        }
    }

    /**
     * {@code java.lang.Module.getResourceAsStream(String)}.
     */
    private static final MethodHandle getResourceAsStream;

    public static InputStream getResourceAsStream(Object module, String resource) {
        try {
            return (InputStream) getResourceAsStream.invoke(module, resource);
        } catch (Throwable throwable) {
            throw new InternalError(throwable);
        }
    }

    /**
     * {@code java.lang.Module.addOpens(String, Module)}. This only seems to work correctly when
     * invoked through reflection.
     */
    public static final Method addOpens;

    /**
     * {@code java.lang.Module.isOpen(String, Module)}.
     */
    private static final MethodHandle isOpenTo;

    public static boolean isOpenTo(Object module1, String pkg, Object module2) {
        try {
            return (boolean) isOpenTo.invoke(module1, pkg, module2);
        } catch (Throwable throwable) {
            throw new InternalError(throwable);
        }
    }

    public static final Class<?> MODULE_CLASS;

    static {
        if (JAVA_SPECIFICATION_VERSION >= 9) {
            try {
                MODULE_CLASS = Class.class.getMethod("getModule").getReturnType();
                getModuleHandle = lookupMethodHandle(Class.class, "getModule");
                getPackages = lookupMethodHandle(MODULE_CLASS, "getPackages");
                addOpens = lookupMethod(MODULE_CLASS, "addOpens", String.class, MODULE_CLASS);
                getResourceAsStream = lookupMethodHandle(MODULE_CLASS, "getResourceAsStream", String.class);
                isOpenTo = lookupMethodHandle(MODULE_CLASS, "isOpen", String.class, MODULE_CLASS);
            } catch (NoSuchMethodException e) {
                throw new InternalError(e);
            }
        } else {
            MODULE_CLASS = null;
            getModuleHandle = null;
            getPackages = null;
            addOpens = null;
            getResourceAsStream = null;
            isOpenTo = null;
        }
    }
}
