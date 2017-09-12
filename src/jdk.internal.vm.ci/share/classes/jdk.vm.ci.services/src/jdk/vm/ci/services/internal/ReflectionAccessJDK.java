/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.services.internal;

import java.lang.reflect.Method;
import java.util.Set;

import jdk.vm.ci.services.Services;

/**
 * Reflection based access to API introduced in JDK 9. This allows the API to be used in code that
 * must be compiled (but not executed) on JDK 8.
 */
public final class ReflectionAccessJDK {

    /**
     * {@code Class.getModule()}.
     */
    private static final Method getModule;

    /**
     * {@code java.lang.Module.addOpens(String, Module)}.
     */
    private static final Method addOpens;

    /**
     * {@code java.lang.Module.getPackages(Module, String, Module)}.
     */
    private static final Method getPackages;

    /**
     * {@code java.lang.Module.isOpen(String, Module)}.
     */
    private static final Method isOpenTo;

    /**
     * Opens all JVMCI packages to the module of a given class.
     *
     * @param other all JVMCI packages will be opened to the module of this class
     */
    @SuppressWarnings("unchecked")
    public static void openJVMCITo(Class<?> other) {
        try {
            Object jvmci = getModule.invoke(Services.class);
            Object otherModule = getModule.invoke(other);
            if (jvmci != otherModule) {
                Set<String> packages = (Set<String>) getPackages.invoke(jvmci);
                for (String pkg : packages) {
                    boolean opened = (Boolean) isOpenTo.invoke(jvmci, pkg, otherModule);
                    if (!opened) {
                        addOpens.invoke(jvmci, pkg, otherModule);
                    }
                }
            }
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }

    static {
        try {
            getModule = Class.class.getMethod("getModule");
            Class<?> moduleClass = getModule.getReturnType();
            getPackages = moduleClass.getMethod("getPackages");
            isOpenTo = moduleClass.getMethod("isOpen", String.class, moduleClass);
            addOpens = moduleClass.getDeclaredMethod("addOpens", String.class, moduleClass);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new InternalError(e);
        }
    }
}
