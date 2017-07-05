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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collection;
import java.util.ServiceLoader;

/**
 *  This class provides wrappers for classes and methods that are new in JDK 9, and which are not
 *  available on older versions of the platform on which javac may be compiled and run.
 *  In future releases, when javac is always compiled on JDK 9 or later, the use of these wrappers
 *  can be replaced by use of the real underlying classes.
 *
 *  <p>Wrapper classes provide a subset of the API of the wrapped classes, as needed for use
 *  in javac. Wrapper objects contain an {@code Object} reference to the underlying runtime object,
 *  and {@code Class} and {@code Method} objects for obtaining or using such instances via
 *  runtime reflection.  The {@code Class} and {@code Method} objects are set up on a per-class
 *  basis, by an {@code init} method, which is called from static methods on the wrapper class,
 *  or in the constructor, when instances are created.
 *  <p>
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class JDK9Wrappers {

    /**
     * Helper class for new method in java.util.ServiceLoader.
     */
    public static final class ServiceLoaderHelper {
        @SuppressWarnings("unchecked")
        public static <S> ServiceLoader<S> load(Layer layer, Class<S> service) {
            try {
                init();
                Object result = loadMethod.invoke(null, layer.theRealLayer, service);
                return (ServiceLoader<S>)result;
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
                    | SecurityException ex) {
                throw new Abort(ex);
            }
        }

        // -----------------------------------------------------------------------------------------

        private static Method loadMethod = null;

        private static void init() {
            if (loadMethod == null) {
                try {
                    Class<?> layerClass = Layer.layerClass;
                    loadMethod = ServiceLoader.class.getDeclaredMethod("load", layerClass, Class.class);
                } catch (NoSuchMethodException | SecurityException ex) {
                    throw new Abort(ex);
                }
            }
        }
    }

    /**
     * Wrapper class for java.lang.module.ModuleDescriptor and ModuleDescriptor.Version.
     */
    public static class ModuleDescriptor {
        public static class Version {
            public static final String CLASSNAME = "java.lang.module.ModuleDescriptor$Version";
            private final Object theRealVersion;

            private Version(Object version) {
                this.theRealVersion = version;
            }

            public static Version parse(String v) {
                try {
                    init();
                    Object result = parseMethod.invoke(null, v);
                    Version version = new Version(result);
                    return version;
                } catch (InvocationTargetException ex) {
                    if (ex.getCause() instanceof IllegalArgumentException) {
                        throw (IllegalArgumentException) ex.getCause();
                    } else {
                        throw new Abort(ex);
                    }
                } catch (IllegalAccessException | IllegalArgumentException | SecurityException ex) {
                    throw new Abort(ex);
                }
            }

            @Override
            public String toString() {
                return theRealVersion.toString();
            }

            // -----------------------------------------------------------------------------------------

            private static Class<?> versionClass = null;
            private static Method parseMethod = null;

            private static void init() {
                if (versionClass == null) {
                    try {
                        versionClass = Class.forName(CLASSNAME, false, null);
                        parseMethod = versionClass.getDeclaredMethod("parse", String.class);
                    } catch (ClassNotFoundException | NoSuchMethodException | SecurityException ex) {
                        throw new Abort(ex);
                    }
                }
            }
        }
    }

    /**
     * Wrapper class for java.lang.module.ModuleFinder.
     */
    public static class ModuleFinder {
        private final Object theRealModuleFinder;

        private ModuleFinder(Object moduleFinder) {
            this.theRealModuleFinder = moduleFinder;
            init();
        }

        public static ModuleFinder of(Path... dirs) {
            try {
                init();
                Object result = ofMethod.invoke(null, (Object)dirs);
                ModuleFinder mFinder = new ModuleFinder(result);
                return mFinder;
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
                    | SecurityException ex) {
                throw new Abort(ex);
            }
        }

        // -----------------------------------------------------------------------------------------

        private static Class<?> moduleFinderClass = null;
        private static Method ofMethod;

        static final Class<?> getModuleFinderClass() {
            init();
            return moduleFinderClass;
        }

        private static void init() {
            if (moduleFinderClass == null) {
                try {
                    moduleFinderClass = Class.forName("java.lang.module.ModuleFinder", false, null);
                    ofMethod = moduleFinderClass.getDeclaredMethod("of", Path[].class);
                } catch (ClassNotFoundException | NoSuchMethodException | SecurityException ex) {
                    throw new Abort(ex);
                }
            }
        }
    }

    /**
     * Wrapper class for java.lang.Module. To materialize a handle use the static factory
     * methods Module#getModule(Class<?>) or Module#getUnnamedModule(ClassLoader).
     */
    public static class Module {

        private final Object theRealModule;

        private Module(Object module) {
            this.theRealModule = module;
            init();
        }

        public static Module getModule(Class<?> clazz) {
            try {
                init();
                Object result = getModuleMethod.invoke(clazz, new Object[0]);
                return new Module(result);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
                    | SecurityException ex) {
                throw new Abort(ex);
            }
        }

        public static Module getUnnamedModule(ClassLoader classLoader) {
            try {
                init();
                Object result = getUnnamedModuleMethod.invoke(classLoader, new Object[0]);
                return new Module(result);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
                    | SecurityException ex) {
                throw new Abort(ex);
            }
        }

        public Module addExports(String pn, Module other) {
            try {
                addExportsMethod.invoke(theRealModule, new Object[] { pn, other.theRealModule});
            } catch (IllegalAccessException | InvocationTargetException ex) {
                throw new Abort(ex);
            }
            return this;
        }

        public Module addUses(Class<?> st) {
            try {
                addUsesMethod.invoke(theRealModule, new Object[] { st });
            } catch (IllegalAccessException | InvocationTargetException ex) {
                throw new Abort(ex);
            }
            return this;
        }

        // -----------------------------------------------------------------------------------------
        // on java.lang.Module
        private static Method addExportsMethod = null;
        // on java.lang.Module
        private static Method addUsesMethod = null;
        // on java.lang.Class
        private static Method getModuleMethod;
        // on java.lang.ClassLoader
        private static Method getUnnamedModuleMethod;

        private static void init() {
            if (addExportsMethod == null) {
                try {
                    Class<?> moduleClass = Class.forName("java.lang.Module", false, null);
                    addUsesMethod = moduleClass.getDeclaredMethod("addUses", new Class<?>[] { Class.class });
                    addExportsMethod = moduleClass.getDeclaredMethod("addExports",
                                                        new Class<?>[] { String.class, moduleClass });
                    getModuleMethod = Class.class.getDeclaredMethod("getModule", new Class<?>[0]);
                    getUnnamedModuleMethod = ClassLoader.class.getDeclaredMethod("getUnnamedModule", new Class<?>[0]);
                } catch (ClassNotFoundException | NoSuchMethodException | SecurityException ex) {
                    throw new Abort(ex);
                }
            }
        }
    }

    /**
     * Wrapper class for java.lang.module.Configuration.
     */
    public static final class Configuration {
        private final Object theRealConfiguration;

        private Configuration(Object configuration) {
            this.theRealConfiguration = configuration;
            init();
        }

        public Configuration resolveAndBind(
                ModuleFinder beforeFinder,
                ModuleFinder afterFinder,
                Collection<String> roots) {
            try {
                Object result = resolveAndBindMethod.invoke(theRealConfiguration,
                                    beforeFinder.theRealModuleFinder,
                                    afterFinder.theRealModuleFinder,
                                    roots
                                );
                Configuration configuration = new Configuration(result);
                return configuration;
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
                    | SecurityException ex) {
                throw new Abort(ex);
            }
        }

        // -----------------------------------------------------------------------------------------

        private static Class<?> configurationClass = null;
        private static Method resolveAndBindMethod;

        static final Class<?> getConfigurationClass() {
            init();
            return configurationClass;
        }

        private static void init() {
            if (configurationClass == null) {
                try {
                    configurationClass = Class.forName("java.lang.module.Configuration", false, null);
                    Class<?> moduleFinderInterface = ModuleFinder.getModuleFinderClass();
                    resolveAndBindMethod = configurationClass.getDeclaredMethod("resolveAndBind",
                                moduleFinderInterface,
                                moduleFinderInterface,
                                Collection.class
                    );
                } catch (ClassNotFoundException | NoSuchMethodException | SecurityException ex) {
                    throw new Abort(ex);
                }
            }
        }
    }

    /**
     * Wrapper class for java.lang.ModuleLayer.
     */
    public static final class Layer {
        private final Object theRealLayer;

        private Layer(Object layer) {
            this.theRealLayer = layer;
        }

        public static Layer boot() {
            try {
                init();
                Object result = bootMethod.invoke(null);
                Layer layer = new Layer(result);
                return layer;
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
                    | SecurityException ex) {
                throw new Abort(ex);
            }
        }

        public Configuration configuration() {
            try {
                Object result = configurationMethod.invoke(theRealLayer);
                Configuration configuration = new Configuration(result);
                return configuration;
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
                    | SecurityException ex) {
                throw new Abort(ex);
            }
        }

        public Layer defineModulesWithOneLoader(Configuration configuration, ClassLoader parentClassLoader) {
            try {
                Object result = defineModulesWithOneLoaderMethod.invoke(
                        theRealLayer, configuration.theRealConfiguration, parentClassLoader);
                Layer layer = new Layer(result);
                return layer;
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
                    | SecurityException ex) {
                throw new Abort(ex);
            }
        }

        // -----------------------------------------------------------------------------------------

        private static Class<?> layerClass = null;
        private static Method bootMethod;
        private static Method defineModulesWithOneLoaderMethod;
        private static Method configurationMethod;

        private static void init() {
            if (layerClass == null) {
                try {
                    layerClass = Class.forName("java.lang.ModuleLayer", false, null);
                    bootMethod = layerClass.getDeclaredMethod("boot");
                    defineModulesWithOneLoaderMethod = layerClass.getDeclaredMethod("defineModulesWithOneLoader",
                                Configuration.getConfigurationClass(),
                                ClassLoader.class);
                    configurationMethod = layerClass.getDeclaredMethod("configuration");
                } catch (ClassNotFoundException | NoSuchMethodException | SecurityException ex) {
                    throw new Abort(ex);
                }
            }
        }
    }


    /**
     * Helper class for new method in jdk.internal.misc.VM.
     */
    public static final class VMHelper {
        public static final String CLASSNAME = "jdk.internal.misc.VM";

        @SuppressWarnings("unchecked")
        public static String[] getRuntimeArguments() {
            try {
                init();
                Object result = getRuntimeArgumentsMethod.invoke(null);
                return (String[])result;
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
                    | SecurityException ex) {
                throw new Abort(ex);
            }
        }

        // -----------------------------------------------------------------------------------------

        private static Class<?> vmClass = null;
        private static Method getRuntimeArgumentsMethod = null;

        private static void init() {
            if (vmClass == null) {
                try {
                    vmClass = Class.forName(CLASSNAME, false, null);
                    getRuntimeArgumentsMethod = vmClass.getDeclaredMethod("getRuntimeArguments");
                } catch (ClassNotFoundException | NoSuchMethodException | SecurityException ex) {
                    throw new Abort(ex);
                }
            }
        }
    }

    /**
     * Helper class for new method in jdk.internal.jmod.JmodFile
     */
    public static final class JmodFile {
        public static final String JMOD_FILE_CLASSNAME = "jdk.internal.jmod.JmodFile";

        public static void checkMagic(Path file) throws IOException {
            try {
                init();
                checkMagicMethod.invoke(null, file);
            } catch (InvocationTargetException ex) {
                if (ex.getCause() instanceof IOException) {
                    throw IOException.class.cast(ex.getCause());
                }
                throw new Abort(ex);
            } catch (IllegalAccessException | IllegalArgumentException | SecurityException ex) {
                throw new Abort(ex);
            }
        }

        // -----------------------------------------------------------------------------------------

        private static Class<?> jmodFileClass = null;
        private static Method checkMagicMethod = null;

        private static void init() {
            if (jmodFileClass == null) {
                try {
                    jmodFileClass = Class.forName(JMOD_FILE_CLASSNAME, false, null);
                    checkMagicMethod = jmodFileClass.getDeclaredMethod("checkMagic", Path.class);
                } catch (ClassNotFoundException | NoSuchMethodException | SecurityException ex) {
                    throw new Abort(ex);
                }
            }
        }
    }
}
