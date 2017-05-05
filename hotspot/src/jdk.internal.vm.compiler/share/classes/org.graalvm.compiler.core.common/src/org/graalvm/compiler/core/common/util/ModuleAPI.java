/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.core.common.util.Util.JAVA_SPECIFICATION_VERSION;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

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

    private ModuleAPI(Method method) {
        this.method = method;
    }

    private final Method method;

    /**
     * {@code Class.getModule()}.
     */
    public static final ModuleAPI getModule;

    /**
     * {@code jdk.internal.module.Modules.addExports(Module, String, Module)}.
     */
    public static final ModuleAPI addExports;

    /**
     * {@code jdk.internal.module.Modules.addOpens(Module, String, Module)}.
     */
    public static final ModuleAPI addOpens;

    /**
     * {@code java.lang.Module.getResourceAsStream(String)}.
     */
    public static final ModuleAPI getResourceAsStream;

    /**
     * {@code java.lang.Module.getPackages()}.
     */
    public static final ModuleAPI getPackages;

    /**
     * {@code java.lang.Module.canRead(Module)}.
     */
    public static final ModuleAPI canRead;

    /**
     * {@code java.lang.Module.isExported(String)}.
     */
    public static final ModuleAPI isExported;

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

    /**
     * Opens all packages in {@code moduleMember}'s module for deep reflection (i.e., allow
     * {@link AccessibleObject#setAccessible(boolean)} to be called for any class/method/field) by
     * {@code requestor}'s module.
     */
    public static void openAllPackagesForReflectionTo(Class<?> moduleMember, Class<?> requestor) {
        Object moduleToOpen = getModule.invoke(moduleMember);
        Object requestorModule = getModule.invoke(requestor);
        if (moduleToOpen != requestorModule) {
            String[] packages = getPackages.invoke(moduleToOpen);
            for (String pkg : packages) {
                addOpens.invokeStatic(moduleToOpen, pkg, requestorModule);
            }
        }
    }

    /**
     * Opens {@code declaringClass}'s package to allow a method declared in {@code accessor} to call
     * {@link AccessibleObject#setAccessible(boolean)} on an {@link AccessibleObject} representing a
     * field or method declared by {@code declaringClass}.
     */
    public static void openForReflectionTo(Class<?> declaringClass, Class<?> accessor) {
        Object moduleToOpen = getModule.invoke(declaringClass);
        Object accessorModule = getModule.invoke(accessor);
        if (moduleToOpen != accessorModule) {
            addOpens.invokeStatic(moduleToOpen, declaringClass.getPackage().getName(), accessorModule);
        }
    }

    /**
     * Exports the package named {@code packageName} declared in {@code moduleMember}'s module to
     * {@code requestor}'s module.
     */
    public static void exportPackageTo(Class<?> moduleMember, String packageName, Class<?> requestor) {
        Object moduleToExport = getModule.invoke(moduleMember);
        Object requestorModule = getModule.invoke(requestor);
        if (moduleToExport != requestorModule) {
            addExports.invokeStatic(moduleToExport, packageName, requestorModule);
        }
    }

    private void checkAvailability() throws InternalError {
        if (method == null) {
            throw new InternalError("Cannot use Module API on JDK " + JAVA_SPECIFICATION_VERSION);
        }
    }

    static {
        if (JAVA_SPECIFICATION_VERSION >= 9) {
            try {
                getModule = new ModuleAPI(Class.class.getMethod("getModule"));
                Class<?> moduleClass = getModule.method.getReturnType();
                Class<?> modulesClass = Class.forName("jdk.internal.module.Modules");
                getResourceAsStream = new ModuleAPI(moduleClass.getMethod("getResourceAsStream", String.class));
                getPackages = new ModuleAPI(moduleClass.getMethod("getPackages"));
                canRead = new ModuleAPI(moduleClass.getMethod("canRead", moduleClass));
                isExported = new ModuleAPI(moduleClass.getMethod("isExported", String.class));
                isExportedTo = new ModuleAPI(moduleClass.getMethod("isExported", String.class, moduleClass));
                addExports = new ModuleAPI(modulesClass.getDeclaredMethod("addExports", moduleClass, String.class, moduleClass));
                addOpens = new ModuleAPI(modulesClass.getDeclaredMethod("addOpens", moduleClass, String.class, moduleClass));
            } catch (NoSuchMethodException | SecurityException | ClassNotFoundException e) {
                throw new InternalError(e);
            }
        } else {
            ModuleAPI unavailable = new ModuleAPI(null);
            getModule = unavailable;
            getResourceAsStream = unavailable;
            getPackages = unavailable;
            canRead = unavailable;
            isExported = unavailable;
            isExportedTo = unavailable;
            addExports = unavailable;
            addOpens = unavailable;
        }

    }
}
