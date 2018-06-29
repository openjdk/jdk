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
package com.oracle.mxtool.junit;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

/**
 * Facade for the {@code java.lang.Module} class introduced in JDK9 that allows tests to be
 * developed against JDK8 but use module logic if deployed on JDK9.
 */
class JLModule {

    private final Object realModule;

    JLModule(Object module) {
        this.realModule = module;
    }

    private static final Class<?> moduleClass;
    private static final Class<?> layerClass;

    private static final Method bootMethod;
    private static final Method modulesMethod;
    private static final Method getModuleMethod;
    private static final Method getUnnamedModuleMethod;
    private static final Method getNameMethod;
    private static final Method getPackagesMethod;
    private static final Method isExportedMethod;
    private static final Method isExported2Method;
    private static final Method addExportsMethod;
    private static final Method addOpensMethod;
    static {
        try {
            moduleClass = findModuleClass();
            Class<?> modulesClass = Class.forName("jdk.internal.module.Modules");
            layerClass = findModuleLayerClass();
            bootMethod = layerClass.getMethod("boot");
            modulesMethod = layerClass.getMethod("modules");
            getModuleMethod = Class.class.getMethod("getModule");
            getUnnamedModuleMethod = ClassLoader.class.getMethod("getUnnamedModule");
            getNameMethod = moduleClass.getMethod("getName");
            getPackagesMethod = moduleClass.getMethod("getPackages");
            isExportedMethod = moduleClass.getMethod("isExported", String.class);
            isExported2Method = moduleClass.getMethod("isExported", String.class, moduleClass);
            addExportsMethod = modulesClass.getDeclaredMethod("addExports", moduleClass, String.class, moduleClass);
            addOpensMethod = getDeclaredMethodOptional(modulesClass, "addOpens", moduleClass, String.class, moduleClass);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    // API change http://hg.openjdk.java.net/jdk9/dev/hotspot/rev/afedee84773e.
    protected static Class<?> findModuleClass() throws ClassNotFoundException {
        try {
            return Class.forName("java.lang.Module");
        } catch (ClassNotFoundException e) {
            return Class.forName("java.lang.reflect.Module");
        }
    }

    // API change http://hg.openjdk.java.net/jdk9/dev/hotspot/rev/afedee84773e.
    protected static Class<?> findModuleLayerClass() throws ClassNotFoundException {
        try {
            return Class.forName("java.lang.ModuleLayer");
        } catch (ClassNotFoundException e) {
            return Class.forName("java.lang.reflect.Layer");
        }
    }

    private static Method getDeclaredMethodOptional(Class<?> declaringClass, String name, Class<?>... parameterTypes) {
        try {
            return declaringClass.getDeclaredMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    public static JLModule fromClass(Class<?> cls) {
        try {
            return new JLModule(getModuleMethod.invoke(cls));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static JLModule find(String name) {
        try {
            Object bootLayer = bootMethod.invoke(null);
            Set<Object> modules = (Set<Object>) modulesMethod.invoke(bootLayer);
            for (Object m : modules) {
                JLModule module = new JLModule(m);
                String mname = module.getName();
                if (mname.equals(name)) {
                    return module;
                }
            }
        } catch (Exception e) {
            throw new InternalError(e);
        }
        return null;
    }

    public static JLModule getUnnamedModuleFor(ClassLoader cl) {
        try {
            return new JLModule(getUnnamedModuleMethod.invoke(cl));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public String getName() {
        try {
            return (String) getNameMethod.invoke(realModule);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Exports all packages in this module to a given module.
     */
    public void exportAllPackagesTo(JLModule module) {
        if (this != module) {
            for (String pkg : getPackages()) {
                // Export all JVMCI packages dynamically instead
                // of requiring a long list of -XaddExports
                // options on the JVM command line.
                if (!isExported(pkg, module)) {
                    addExports(pkg, module);
                    addOpens(pkg, module);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public Iterable<String> getPackages() {
        try {
            // API change http://hg.openjdk.java.net/jdk9/dev/hotspot/rev/afedee84773e#l1.15
            Object res = getPackagesMethod.invoke(realModule);
            if (res instanceof String[]) {
                return Arrays.asList((String[]) res);
            }
            return (Set<String>) res;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public boolean isExported(String pn) {
        try {
            return (Boolean) isExportedMethod.invoke(realModule, pn);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public boolean isExported(String pn, JLModule other) {
        try {
            return (Boolean) isExported2Method.invoke(realModule, pn, other.realModule);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public void addExports(String pn, JLModule other) {
        try {
            addExportsMethod.invoke(null, realModule, pn, other.realModule);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public void addOpens(String pn, JLModule other) {
        if (addOpensMethod != null) {
            try {
                addOpensMethod.invoke(null, realModule, pn, other.realModule);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
    }

    @Override
    public String toString() {
        return realModule.toString();
    }
}
