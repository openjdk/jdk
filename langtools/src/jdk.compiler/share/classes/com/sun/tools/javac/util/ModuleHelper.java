/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ModuleHelper {
    /** The context key for the module helper. */
    protected static final Context.Key<ModuleHelper> moduleHelperKey = new Context.Key<>();

    /** Get the JavaCompiler instance for this context. */
    public static ModuleHelper instance(Context context) {
        ModuleHelper instance = context.get(moduleHelperKey);
        if (instance == null)
            instance = new ModuleHelper(context);
        return instance;
    }

    public ModuleHelper(Context context) {
        context.put(moduleHelperKey, this);
        Options options = Options.instance(context);
        allowAccessToInternalAPI = options.isSet("accessInternalAPI");
    }

    final boolean allowAccessToInternalAPI;

    private void exportPackageToModule(String packageName, Object target)
            throws ClassNotFoundException, NoSuchMethodException, IllegalArgumentException,
                   InvocationTargetException, IllegalAccessException {
        if (addExportsMethod == null) {
            Class<?> moduleClass = Class.forName("java.lang.reflect.Module");
            addExportsMethod = moduleClass.getDeclaredMethod("addExports",
                    new Class<?>[] { String.class, moduleClass });
        }
        addExportsMethod.invoke(from, new Object[] { packageName, target });
    }

    static final String[] javacInternalPackages = new String[] {
        "com.sun.tools.javac.api",
        "com.sun.tools.javac.code",
        "com.sun.tools.javac.comp",
        "com.sun.tools.javac.file",
        "com.sun.tools.javac.jvm",
        "com.sun.tools.javac.main",
        "com.sun.tools.javac.model",
        "com.sun.tools.javac.parser",
        "com.sun.tools.javac.platform",
        "com.sun.tools.javac.processing",
        "com.sun.tools.javac.tree",
        "com.sun.tools.javac.util",

        "com.sun.tools.doclint",
    };

    public void addExports(ClassLoader classLoader) {
        try {
            if (allowAccessToInternalAPI) {
                if (from == null) {
                    if (getModuleMethod == null) {
                        getModuleMethod = Class.class.getDeclaredMethod("getModule", new Class<?>[0]);
                    }
                    from = getModuleMethod.invoke(getClass(), new Object[0]);
                }
                if (getUnnamedModuleMethod == null) {
                    getUnnamedModuleMethod = ClassLoader.class.getDeclaredMethod("getUnnamedModule", new Class<?>[0]);
                }
                Object target = getUnnamedModuleMethod.invoke(classLoader, new Object[0]);
                for (String pack: javacInternalPackages) {
                    exportPackageToModule(pack, target);
                }
            }
        } catch (Exception e) {
            // do nothing
        }
    }

    // a module instance
    private Object from = null;

    // on java.lang.reflect.Module
    private static Method addExportsMethod = null;

    // on java.lang.ClassLoader
    private static Method getUnnamedModuleMethod = null;

    // on java.lang.Class
    private static Method getModuleMethod = null;
}
