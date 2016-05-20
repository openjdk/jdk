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
     * Wrapper class for java.lang.module.Configuration.
     */
    public static final class Configuration {
        private final Object theRealConfiguration;

        private Configuration(Object configuration) {
            this.theRealConfiguration = configuration;
            init();
        }

        public Configuration resolveRequiresAndUses(
                ModuleFinder beforeFinder,
                ModuleFinder afterFinder,
                Collection<String> roots) {
            try {
                Object result = resolveRequiresAndUsesMethod.invoke(theRealConfiguration,
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
        private static Method resolveRequiresAndUsesMethod;

        static final Class<?> getConfigurationClass() {
            init();
            return configurationClass;
        }

        private static void init() {
            if (configurationClass == null) {
                try {
                    configurationClass = Class.forName("java.lang.module.Configuration", false, null);
                    Class<?> moduleFinderInterface = ModuleFinder.getModuleFinderClass();
                    resolveRequiresAndUsesMethod = configurationClass.getDeclaredMethod("resolveRequiresAndUses",
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
     * Wrapper class for java.lang.module.Layer.
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
                    layerClass = Class.forName("java.lang.reflect.Layer", false, null);
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
}
