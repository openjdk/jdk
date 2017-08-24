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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

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

    public JDK9Method(Class<?> declaringClass, String name, Class<?>... parameterTypes) {
        try {
            this.method = declaringClass.getMethod(name, parameterTypes);
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }

    /**
     * Determines if the Java runtime is version 8 or earlier.
     */
    public static final boolean Java8OrEarlier = JAVA_SPECIFICATION_VERSION <= 8;

    public final Method method;

    public Class<?> getReturnType() {
        return method.getReturnType();
    }

    /**
     * {@code Class.getModule()}.
     */
    public static final JDK9Method getModule;

    /**
     * {@code java.lang.Module.getPackages()}.
     */
    public static final JDK9Method getPackages;

    /**
     * {@code java.lang.Module.getResourceAsStream(String)}.
     */
    public static final JDK9Method getResourceAsStream;

    /**
     * {@code java.lang.Module.addOpens(String, Module)}.
     */
    public static final JDK9Method addOpens;

    /**
     * {@code java.lang.Module.isOpen(String, Module)}.
     */
    public static final JDK9Method isOpenTo;

    /**
     * Invokes the static Module API method represented by this object.
     */
    @SuppressWarnings("unchecked")
    public <T> T invokeStatic(Object... args) {
        checkAvailability();
        assert Modifier.isStatic(method.getModifiers());
        try {
            return (T) method.invoke(null, args);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new InternalError(e);
        }
    }

    /**
     * Invokes the non-static Module API method represented by this object.
     */
    @SuppressWarnings("unchecked")
    public <T> T invoke(Object receiver, Object... args) {
        checkAvailability();
        assert !Modifier.isStatic(method.getModifiers());
        try {
            return (T) method.invoke(receiver, args);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new InternalError(e);
        }
    }

    private void checkAvailability() throws InternalError {
        if (method == null) {
            throw new InternalError("Cannot use Module API on JDK " + JAVA_SPECIFICATION_VERSION);
        }
    }

    static {
        if (JAVA_SPECIFICATION_VERSION >= 9) {
            getModule = new JDK9Method(Class.class, "getModule");
            Class<?> moduleClass = getModule.getReturnType();
            getPackages = new JDK9Method(moduleClass, "getPackages");
            addOpens = new JDK9Method(moduleClass, "addOpens", String.class, moduleClass);
            getResourceAsStream = new JDK9Method(moduleClass, "getResourceAsStream", String.class);
            isOpenTo = new JDK9Method(moduleClass, "isOpen", String.class, moduleClass);
        } else {
            JDK9Method unavailable = new JDK9Method();
            getModule = unavailable;
            getPackages = unavailable;
            addOpens = unavailable;
            getResourceAsStream = unavailable;
            isOpenTo = unavailable;
        }
    }

    private JDK9Method() {
        method = null;
    }
}
