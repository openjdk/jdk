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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collection;
import java.util.ServiceLoader;

/** This class provides wrappers for classes and methods that are new in JDK 9, and which are not
 *  available on older versions of the platform on which javac may be compiled and run.
 *  In future releases, when javac is always compiled on JDK 9 or later, the use of these wrappers
 *  can be replaced by use of the real underlying classes.
 */
public class ModuleWrappers {
    public static final class ServiceLoaderHelper {
        @SuppressWarnings("unchecked")
        public static <S> ServiceLoader<S> load(Layer layer, Class<S> service) {
            try {
                Class<?> layerClass = LayerHelper.getLayerClass();
                Method loadMethod = ServiceLoader.class
                        .getDeclaredMethod("load", layerClass, Class.class);
                Object result = loadMethod.invoke(ServiceLoader.class, layer.theRealLayer, service);
                return (ServiceLoader<S>)result;
            } catch (NoSuchMethodException |
                    SecurityException |
                    IllegalArgumentException |
                    IllegalAccessException |
                    InvocationTargetException ex) {
                throw new Abort(ex);
            }
        }
    }

    public static class ModuleFinder {
        Object theRealModuleFinder;

        private ModuleFinder(Object moduleFinder) {
            this.theRealModuleFinder = moduleFinder;
        }

        public static ModuleFinder of(Path... dirs) {
            try {
                Object result = ModuleFinderHelper.getOfMethod()
                        .invoke(ModuleFinderHelper.moduleFinderInterface, (Object)dirs);
                ModuleFinder mFinder = new ModuleFinder(result);
                return mFinder;
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                throw new Abort(ex);
            }
        }

        public static ModuleFinder empty() {
            try {
                Object result = ModuleFinderHelper.getEmptyMethod()
                        .invoke(ModuleFinderHelper.moduleFinderInterface);
                ModuleFinder mFinder = new ModuleFinder(result);
                return mFinder;
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                throw new Abort(ex);
            }
        }
    }

    private static class ModuleFinderHelper {
        static Method ofMethod = null;
        static Method emptyMethod = null;
        static Class<?> moduleFinderInterface;

        static Method getOfMethod() {
            if (ModuleFinderHelper.ofMethod == null) {
                try {
                    getModuleFinderInterface();
                    ofMethod = moduleFinderInterface.getDeclaredMethod("of", Path[].class);
                } catch (NoSuchMethodException | SecurityException ex) {
                    throw new Abort(ex);
                }
            }
            return ofMethod;
        }

        static Method getEmptyMethod() {
            if (emptyMethod == null) {
                try {
                    getModuleFinderInterface();
                    emptyMethod = moduleFinderInterface.getDeclaredMethod("empty");
                } catch (NoSuchMethodException | SecurityException ex) {
                    throw new Abort(ex);
                }
            }
            return emptyMethod;
        }

        static Class<?> getModuleFinderInterface() {
            if (moduleFinderInterface == null) {
                try {
                    moduleFinderInterface = Class.forName("java.lang.module.ModuleFinder", false, ClassLoader.getSystemClassLoader());
                } catch (ClassNotFoundException ex) {
                    throw new Abort(ex);
                }
            }
            return moduleFinderInterface;
        }
    }

    public static final class Configuration {
        Object theRealConfiguration;

        private Configuration(Object configuration) {
            this.theRealConfiguration = configuration;
        }

        public Configuration resolveRequiresAndUses(
                ModuleFinder beforeFinder,
                ModuleFinder afterFinder,
                Collection<String> roots) {
            try {
                Object result = ConfigurationHelper.getResolveRequiresAndUses()
                        .invoke(theRealConfiguration,
                                    beforeFinder.theRealModuleFinder,
                                    afterFinder.theRealModuleFinder,
                                    roots
                                );
                Configuration configuration = new Configuration(result);
                return configuration;
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                throw new Abort(ex);
            }
        }
    }

    private static class ConfigurationHelper {
        static Method resolveRequiresAndUsesMethod;
        static Class<?> configurationClass;

        static Method getResolveRequiresAndUses() {
            if (resolveRequiresAndUsesMethod == null) {
                try {
                    getConfigurationClass();
                    Class<?> moduleFinderInterface = ModuleFinderHelper.getModuleFinderInterface();
                    Class<?> configurationClass = ConfigurationHelper.getConfigurationClass();
                    resolveRequiresAndUsesMethod = configurationClass.getDeclaredMethod("resolveRequiresAndUses",
                                moduleFinderInterface,
                                moduleFinderInterface,
                                Collection.class
                    );
                } catch (NoSuchMethodException | SecurityException ex) {
                    throw new Abort(ex);
                }
            }
            return resolveRequiresAndUsesMethod;
        }

        static Class<?> getConfigurationClass() {
            if (configurationClass == null) {
                try {
                    configurationClass = Class.forName("java.lang.module.Configuration", false, ClassLoader.getSystemClassLoader());
                } catch (ClassNotFoundException ex) {
                    throw new Abort(ex);
                }
            }
            return configurationClass;
        }
    }

    public static final class Layer {
        Object theRealLayer;

        private Layer(Object layer) {
            this.theRealLayer = layer;
        }

        public static Layer boot() {
            try {
                Object result = LayerHelper.getBootMethod().invoke(LayerHelper.getLayerClass());
                Layer layer = new Layer(result);
                return layer;
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                throw new Abort(ex);
            }
        }

        public Configuration configuration() {
            try {
                Object result = LayerHelper.getConfigurationMethod().invoke(theRealLayer);
                Layer layer = new Layer(result);
                return new Configuration(result);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                throw new Abort(ex);
            }
        }

        public Layer defineModulesWithOneLoader(Configuration configuration, ClassLoader parentClassLoader) {
            try {
                Object result = LayerHelper.getDefineModulesWithOneLoaderMethod()
                        .invoke(theRealLayer, configuration.theRealConfiguration, parentClassLoader);
                Layer layer = new Layer(result);
                return layer;
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                throw new Abort(ex);
            }
        }

    }

    private static class LayerHelper {
        static Class<?> layerClass;
        static Method bootMethod;
        static Method defineModulesWithOneLoaderMethod = null;
        static Method configurationMethod;

        static Class<?> getLayerClass() {
            if (layerClass == null) {
                try {
                    layerClass = Class.forName("java.lang.reflect.Layer", false, ClassLoader.getSystemClassLoader());
                } catch (ClassNotFoundException ex) {
                    throw new Abort(ex);
                }
            }
            return layerClass;
        }

        static Method getBootMethod() {
            if (bootMethod == null) {
                try {
                    bootMethod = getLayerClass().getDeclaredMethod("boot");
                } catch (NoSuchMethodException | SecurityException ex) {
                    throw new Abort(ex);
                }
            }
            return bootMethod;
        }

        static Method getDefineModulesWithOneLoaderMethod() {
            if (defineModulesWithOneLoaderMethod == null) {
                try {
                    defineModulesWithOneLoaderMethod = getLayerClass().getDeclaredMethod("defineModulesWithOneLoader",
                                ConfigurationHelper.getConfigurationClass(),
                                ClassLoader.class
                    );
                } catch (NoSuchMethodException | SecurityException ex) {
                    throw new Abort(ex);
                }
            }
            return defineModulesWithOneLoaderMethod;
        }

        static Method getConfigurationMethod() {
            if (configurationMethod == null) {
                try {
                    configurationMethod =  getLayerClass().getDeclaredMethod("configuration");
                } catch (NoSuchMethodException | SecurityException ex) {
                    throw new Abort(ex);
                }
            }
            return configurationMethod;
        }
    }
}
