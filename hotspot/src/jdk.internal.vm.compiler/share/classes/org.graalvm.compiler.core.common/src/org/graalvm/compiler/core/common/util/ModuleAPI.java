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
package org.graalvm.compiler.core.common.util;

import static org.graalvm.compiler.serviceprovider.JDK9Method.JAVA_SPECIFICATION_VERSION;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.graalvm.compiler.debug.GraalError;

/**
 * Reflection based access to the Module API introduced by JDK 9. This allows the API to be used in
 * code that must be compiled on a JDK prior to 9. Use of this class must be guarded by a test for
 * JDK 9 or later. For example:
 *
 * <pre>
 * if (Util.JAVA_SPECIFICATION_VERSION >= 9) {
 *     // Use of ModuleAPI
 * }
 * </pre>
 */
public final class ModuleAPI {

    public ModuleAPI(Class<?> declaringClass, String name, Class<?>... parameterTypes) {
        try {
            this.method = declaringClass.getMethod(name, parameterTypes);
        } catch (Exception e) {
            throw new GraalError(e);
        }
    }

    public final Method method;

    public Class<?> getReturnType() {
        return method.getReturnType();
    }

    /**
     * {@code Class.getModule()}.
     */
    public static final ModuleAPI getModule;

    /**
     * {@code java.lang.Module.getResourceAsStream(String)}.
     */
    public static final ModuleAPI getResourceAsStream;

    /**
     * {@code java.lang.Module.isExported(String, Module)}.
     */
    public static final ModuleAPI isExportedTo;

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
            throw new GraalError(e);
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
            throw new GraalError(e);
        }
    }

    private void checkAvailability() throws GraalError {
        if (method == null) {
            throw new GraalError("Cannot use Module API on JDK " + JAVA_SPECIFICATION_VERSION);
        }
    }

    static {
        if (JAVA_SPECIFICATION_VERSION >= 9) {
            getModule = new ModuleAPI(Class.class, "getModule");
            Class<?> moduleClass = getModule.getReturnType();
            getResourceAsStream = new ModuleAPI(moduleClass, "getResourceAsStream", String.class);
            isExportedTo = new ModuleAPI(moduleClass, "isExported", String.class, moduleClass);
        } else {
            ModuleAPI unavailable = new ModuleAPI();
            getModule = unavailable;
            getResourceAsStream = unavailable;
            isExportedTo = unavailable;
        }
    }

    private ModuleAPI() {
        method = null;
    }
}
