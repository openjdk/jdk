/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.reflect;

import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ResolvedModule;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.internal.loader.ClassLoaderValue;
import jdk.internal.loader.Loader;
import jdk.internal.loader.LoaderPool;
import jdk.internal.misc.SharedSecrets;
import jdk.internal.module.Modules;
import jdk.internal.module.ServicesCatalog;
import sun.security.util.SecurityConstants;


/**
 * A layer of modules in the Java virtual machine.
 *
 * <p> A layer is created from a graph of modules that is the {@link
 * Configuration} and a function that maps each module to a {@link ClassLoader}.
 * Creating a layer informs the Java virtual machine about the classes that
 * may be loaded from modules so that the Java virtual machine knows which
 * module that each class is a member of. Each layer, except the {@link
 * #empty() empty} layer, has at least one {@link #parents() parent}. </p>
 *
 * <p> Creating a layer creates a {@link Module} object for each {@link
 * ResolvedModule} in the configuration. For each resolved module that is
 * {@link ResolvedModule#reads() read}, the {@code Module} {@link
 * Module#canRead reads} the corresponding run-time {@code Module}, which may
 * be in the same layer or a parent layer. The {@code Module} {@link
 * Module#isExported(String) exports} the packages described by its {@link
 * ModuleDescriptor}. </p>
 *
 * <p> The {@link #defineModulesWithOneLoader defineModulesWithOneLoader} and
 * {@link #defineModulesWithManyLoaders defineModulesWithManyLoaders} methods
 * provide convenient ways to create a {@code Layer} where all modules are
 * mapped to a single class loader or where each module is mapped to its own
 * class loader. The {@link #defineModules defineModules} method is for more
 * advanced cases where modules are mapped to custom class loaders by means of
 * a function specified to the method. Each of these methods has an instance
 * and static variant. The instance methods create a layer with the receiver
 * as the parent layer. The static methods are for more advanced cases where
 * there can be more than one parent layer or a {@link Layer.Controller
 * Controller} is needed to control modules in the layer. </p>
 *
 * <p> A Java virtual machine has at least one non-empty layer, the {@link
 * #boot() boot} layer, that is created when the Java virtual machine is
 * started. The boot layer contains module {@code java.base} and is the only
 * layer in the Java virtual machine with a module named "{@code java.base}".
 * The modules in the boot layer are mapped to the bootstrap class loader and
 * other class loaders that are <a href="../ClassLoader.html#builtinLoaders">
 * built-in</a> into the Java virtual machine. The boot layer will often be
 * the {@link #parents() parent} when creating additional layers. </p>
 *
 * <p> As when creating a {@code Configuration},
 * {@link ModuleDescriptor#isAutomatic() automatic} modules receive
 * <a href="../module/Configuration.html#automaticmoduleresolution">special
 * treatment</a> when creating a layer. An automatic module is created in the
 * Java virtual machine as a {@code Module} that reads every unnamed {@code
 * Module} in the Java virtual machine. </p>
 *
 * <p> Unless otherwise specified, passing a {@code null} argument to a method
 * in this class causes a {@link NullPointerException NullPointerException} to
 * be thrown. </p>
 *
 * <h3> Example usage: </h3>
 *
 * <p> This example creates a configuration by resolving a module named
 * "{@code myapp}" with the configuration for the boot layer as the parent. It
 * then creates a new layer with the modules in this configuration. All modules
 * are defined to the same class loader. </p>
 *
 * <pre>{@code
 *     ModuleFinder finder = ModuleFinder.of(dir1, dir2, dir3);
 *
 *     Layer parent = Layer.boot();
 *
 *     Configuration cf = parent.configuration()
 *         .resolveRequires(finder, ModuleFinder.of(), Set.of("myapp"));
 *
 *     ClassLoader scl = ClassLoader.getSystemClassLoader();
 *
 *     Layer layer = parent.defineModulesWithOneLoader(cf, scl);
 *
 *     Class<?> c = layer.findLoader("myapp").loadClass("app.Main");
 * }</pre>
 *
 * @since 9
 * @see Module#getLayer()
 */

public final class Layer {

    // the empty Layer
    private static final Layer EMPTY_LAYER
        = new Layer(Configuration.empty(), List.of(), null);

    // the configuration from which this Layer was created
    private final Configuration cf;

    // parent layers, empty in the case of the empty layer
    private final List<Layer> parents;

    // maps module name to jlr.Module
    private final Map<String, Module> nameToModule;

    /**
     * Creates a new Layer from the modules in the given configuration.
     */
    private Layer(Configuration cf,
                  List<Layer> parents,
                  Function<String, ClassLoader> clf)
    {
        this.cf = cf;
        this.parents = parents; // no need to do defensive copy

        Map<String, Module> map;
        if (parents.isEmpty()) {
            map = Collections.emptyMap();
        } else {
            map = Module.defineModules(cf, clf, this);
        }
        this.nameToModule = map; // no need to do defensive copy
    }

    /**
     * Controls a layer. The static methods defined by {@link Layer} to create
     * module layers return a {@code Controller} that can be used to control
     * modules in the layer.
     *
     * @apiNote Care should be taken with {@code Controller} objects, they
     * should never be shared with untrusted code.
     *
     * @since 9
     */
    public static final class Controller {
        private final Layer layer;

        Controller(Layer layer) {
            this.layer = layer;
        }

        /**
         * Returns the layer that this object controls.
         *
         * @return the layer
         */
        public Layer layer() {
            return layer;
        }

        private void ensureInLayer(Module source) {
            if (!layer.modules().contains(source))
                throw new IllegalArgumentException(source + " not in layer");
        }


        /**
         * Updates module {@code source} in the layer to read module
         * {@code target}. This method is a no-op if {@code source} already
         * reads {@code target}.
         *
         * @implNote <em>Read edges</em> added by this method are <em>weak</em>
         * and do not prevent {@code target} from being GC'ed when {@code source}
         * is strongly reachable.
         *
         * @param  source
         *         The source module
         * @param  target
         *         The target module to read
         *
         * @return This controller
         *
         * @throws IllegalArgumentException
         *         If {@code source} is not in the layer
         *
         * @see Module#addReads
         */
        public Controller addReads(Module source, Module target) {
            Objects.requireNonNull(source);
            Objects.requireNonNull(target);
            ensureInLayer(source);
            Modules.addReads(source, target);
            return this;
        }

        /**
         * Updates module {@code source} in the layer to open a package to
         * module {@code target}. This method is a no-op if {@code source}
         * already opens the package to at least {@code target}.
         *
         * @param  source
         *         The source module
         * @param  pn
         *         The package name
         * @param  target
         *         The target module to read
         *
         * @return This controller
         *
         * @throws IllegalArgumentException
         *         If {@code source} is not in the layer or the package is not
         *         in the source module
         *
         * @see Module#addOpens
         */
        public Controller addOpens(Module source, String pn, Module target) {
            Objects.requireNonNull(source);
            Objects.requireNonNull(target);
            ensureInLayer(source);
            Modules.addOpens(source, pn, target);
            return this;
        }
    }


    /**
     * Creates a new layer, with this layer as its parent, by defining the
     * modules in the given {@code Configuration} to the Java virtual machine.
     * This method creates one class loader and defines all modules to that
     * class loader. The {@link ClassLoader#getParent() parent} of each class
     * loader is the given parent class loader. This method works exactly as
     * specified by the static {@link
     * #defineModulesWithOneLoader(Configuration,List,ClassLoader)
     * defineModulesWithOneLoader} method when invoked with this layer as the
     * parent. In other words, if this layer is {@code thisLayer} then this
     * method is equivalent to invoking:
     * <pre> {@code
     *     Layer.defineModulesWithOneLoader(cf, List.of(thisLayer), parentLoader).layer();
     * }</pre>
     *
     * @param  cf
     *         The configuration for the layer
     * @param  parentLoader
     *         The parent class loader for the class loader created by this
     *         method; may be {@code null} for the bootstrap class loader
     *
     * @return The newly created layer
     *
     * @throws IllegalArgumentException
     *         If the parent of the given configuration is not the configuration
     *         for this layer
     * @throws LayerInstantiationException
     *         If all modules cannot be defined to the same class loader for any
     *         of the reasons listed above or the layer cannot be created because
     *         the configuration contains a module named "{@code java.base}" or
     *         a module with a package name starting with "{@code java.}"
     * @throws SecurityException
     *         If {@code RuntimePermission("createClassLoader")} or
     *         {@code RuntimePermission("getClassLoader")} is denied by
     *         the security manager
     *
     * @see #findLoader
     */
    public Layer defineModulesWithOneLoader(Configuration cf,
                                            ClassLoader parentLoader) {
        return defineModulesWithOneLoader(cf, List.of(this), parentLoader).layer();
    }


    /**
     * Creates a new layer, with this layer as its parent, by defining the
     * modules in the given {@code Configuration} to the Java virtual machine.
     * Each module is defined to its own {@link ClassLoader} created by this
     * method. The {@link ClassLoader#getParent() parent} of each class loader
     * is the given parent class loader. This method works exactly as specified
     * by the static {@link
     * #defineModulesWithManyLoaders(Configuration,List,ClassLoader)
     * defineModulesWithManyLoaders} method when invoked with this layer as the
     * parent. In other words, if this layer is {@code thisLayer} then this
     * method is equivalent to invoking:
     * <pre> {@code
     *     Layer.defineModulesWithManyLoaders(cf, List.of(thisLayer), parentLoader).layer();
     * }</pre>
     *
     * @param  cf
     *         The configuration for the layer
     * @param  parentLoader
     *         The parent class loader for each of the class loaders created by
     *         this method; may be {@code null} for the bootstrap class loader
     *
     * @return The newly created layer
     *
     * @throws IllegalArgumentException
     *         If the parent of the given configuration is not the configuration
     *         for this layer
     * @throws LayerInstantiationException
     *         If the layer cannot be created because the configuration contains
     *         a module named "{@code java.base}" or a module with a package
     *         name starting with "{@code java.}"
     * @throws SecurityException
     *         If {@code RuntimePermission("createClassLoader")} or
     *         {@code RuntimePermission("getClassLoader")} is denied by
     *         the security manager
     *
     * @see #findLoader
     */
    public Layer defineModulesWithManyLoaders(Configuration cf,
                                              ClassLoader parentLoader) {
        return defineModulesWithManyLoaders(cf, List.of(this), parentLoader).layer();
    }


    /**
     * Creates a new layer, with this layer as its parent, by defining the
     * modules in the given {@code Configuration} to the Java virtual machine.
     * Each module is mapped, by name, to its class loader by means of the
     * given function. This method works exactly as specified by the static
     * {@link #defineModules(Configuration,List,Function) defineModules}
     * method when invoked with this layer as the parent. In other words, if
     * this layer is {@code thisLayer} then this method is equivalent to
     * invoking:
     * <pre> {@code
     *     Layer.defineModules(cf, List.of(thisLayer), clf).layer();
     * }</pre>
     *
     * @param  cf
     *         The configuration for the layer
     * @param  clf
     *         The function to map a module name to a class loader
     *
     * @return The newly created layer
     *
     * @throws IllegalArgumentException
     *         If the parent of the given configuration is not the configuration
     *         for this layer
     * @throws LayerInstantiationException
     *         If creating the {@code Layer} fails for any of the reasons
     *         listed above, the layer cannot be created because the
     *         configuration contains a module named "{@code java.base}",
     *         a module with a package name starting with "{@code java.}" is
     *         mapped to a class loader other than the {@link
     *         ClassLoader#getPlatformClassLoader() platform class loader},
     *         or the function to map a module name to a class loader returns
     *         {@code null}
     * @throws SecurityException
     *         If {@code RuntimePermission("getClassLoader")} is denied by
     *         the security manager
     */
    public Layer defineModules(Configuration cf,
                               Function<String, ClassLoader> clf) {
        return defineModules(cf, List.of(this), clf).layer();
    }

    /**
     * Creates a new layer by defining the modules in the given {@code
     * Configuration} to the Java virtual machine. This method creates one
     * class loader and defines all modules to that class loader.
     *
     * <p> The class loader created by this method implements <em>direct
     * delegation</em> when loading types from modules. When its {@link
     * ClassLoader#loadClass(String, boolean) loadClass} method is invoked to
     * load a class then it uses the package name of the class to map it to a
     * module. This may be a module in this layer and hence defined to the same
     * class loader. It may be a package in a module in a parent layer that is
     * exported to one or more of the modules in this layer. The class
     * loader delegates to the class loader of the module, throwing {@code
     * ClassNotFoundException} if not found by that class loader.
     *
     * When {@code loadClass} is invoked to load classes that do not map to a
     * module then it delegates to the parent class loader. </p>
     *
     * <p> Attempting to create a layer with all modules defined to the same
     * class loader can fail for the following reasons:
     *
     * <ul>
     *
     *     <li><p> <em>Overlapping packages</em>: Two or more modules in the
     *     configuration have the same package. </p></li>
     *
     *     <li><p> <em>Split delegation</em>: The resulting class loader would
     *     need to delegate to more than one class loader in order to load types
     *     in a specific package. </p></li>
     *
     * </ul>
     *
     * <p> If there is a security manager then the class loader created by
     * this method will load classes and resources with privileges that are
     * restricted by the calling context of this method. </p>
     *
     * @param  cf
     *         The configuration for the layer
     * @param  parentLayers
     *         The list parent layers in search order
     * @param  parentLoader
     *         The parent class loader for the class loader created by this
     *         method; may be {@code null} for the bootstrap class loader
     *
     * @return A controller that controls the newly created layer
     *
     * @throws IllegalArgumentException
     *         If the parent configurations do not match the configuration of
     *         the parent layers, including order
     * @throws LayerInstantiationException
     *         If all modules cannot be defined to the same class loader for any
     *         of the reasons listed above or the layer cannot be created because
     *         the configuration contains a module named "{@code java.base}" or
     *         a module with a package name starting with "{@code java.}"
     * @throws SecurityException
     *         If {@code RuntimePermission("createClassLoader")} or
     *         {@code RuntimePermission("getClassLoader")} is denied by
     *         the security manager
     *
     * @see #findLoader
     */
    public static Controller defineModulesWithOneLoader(Configuration cf,
                                                        List<Layer> parentLayers,
                                                        ClassLoader parentLoader)
    {
        List<Layer> parents = new ArrayList<>(parentLayers);
        checkConfiguration(cf, parents);

        checkCreateClassLoaderPermission();
        checkGetClassLoaderPermission();

        try {
            Loader loader = new Loader(cf.modules(), parentLoader);
            loader.initRemotePackageMap(cf, parents);
            Layer layer =  new Layer(cf, parents, mn -> loader);
            return new Controller(layer);
        } catch (IllegalArgumentException e) {
            throw new LayerInstantiationException(e.getMessage());
        }
    }

    /**
     * Creates a new layer by defining the modules in the given {@code
     * Configuration} to the Java virtual machine. Each module is defined to
     * its own {@link ClassLoader} created by this method. The {@link
     * ClassLoader#getParent() parent} of each class loader is the given parent
     * class loader.
     *
     * <p> The class loaders created by this method implement <em>direct
     * delegation</em> when loading types from modules. When {@link
     * ClassLoader#loadClass(String, boolean) loadClass} method is invoked to
     * load a class then it uses the package name of the class to map it to a
     * module. The package may be in the module defined to the class loader.
     * The package may be exported by another module in this layer to the
     * module defined to the class loader. It may be in a package exported by a
     * module in a parent layer. The class loader delegates to the class loader
     * of the module, throwing {@code ClassNotFoundException} if not found by
     * that class loader.
     *
     * When {@code loadClass} is invoked to load classes that do not map to a
     * module then it delegates to the parent class loader. </p>
     *
     * <p> If there is a security manager then the class loaders created by
     * this method will load classes and resources with privileges that are
     * restricted by the calling context of this method. </p>
     *
     * @param  cf
     *         The configuration for the layer
     * @param  parentLayers
     *         The list parent layers in search order
     * @param  parentLoader
     *         The parent class loader for each of the class loaders created by
     *         this method; may be {@code null} for the bootstrap class loader
     *
     * @return A controller that controls the newly created layer
     *
     * @throws IllegalArgumentException
     *         If the parent configurations do not match the configuration of
     *         the parent layers, including order
     * @throws LayerInstantiationException
     *         If the layer cannot be created because the configuration contains
     *         a module named "{@code java.base}" or a module with a package
     *         name starting with "{@code java.}"
     * @throws SecurityException
     *         If {@code RuntimePermission("createClassLoader")} or
     *         {@code RuntimePermission("getClassLoader")} is denied by
     *         the security manager
     *
     * @see #findLoader
     */
    public static Controller defineModulesWithManyLoaders(Configuration cf,
                                                          List<Layer> parentLayers,
                                                          ClassLoader parentLoader)
    {
        List<Layer> parents = new ArrayList<>(parentLayers);
        checkConfiguration(cf, parents);

        checkCreateClassLoaderPermission();
        checkGetClassLoaderPermission();

        LoaderPool pool = new LoaderPool(cf, parents, parentLoader);
        try {
            Layer layer = new Layer(cf, parents, pool::loaderFor);
            return new Controller(layer);
        } catch (IllegalArgumentException e) {
            throw new LayerInstantiationException(e.getMessage());
        }
    }

    /**
     * Creates a new layer by defining the modules in the given {@code
     * Configuration} to the Java virtual machine.
     * Each module is mapped, by name, to its class loader by means of the
     * given function. The class loader delegation implemented by these class
     * loaders must respect module readability. The class loaders should be
     * {@link ClassLoader#registerAsParallelCapable parallel-capable} so as to
     * avoid deadlocks during class loading. In addition, the entity creating
     * a new layer with this method should arrange that the class loaders are
     * ready to load from these modules before there are any attempts to load
     * classes or resources.
     *
     * <p> Creating a {@code Layer} can fail for the following reasons: </p>
     *
     * <ul>
     *
     *     <li><p> Two or more modules with the same package are mapped to the
     *     same class loader. </p></li>
     *
     *     <li><p> A module is mapped to a class loader that already has a
     *     module of the same name defined to it. </p></li>
     *
     *     <li><p> A module is mapped to a class loader that has already
     *     defined types in any of the packages in the module. </p></li>
     *
     * </ul>
     *
     * <p> If the function to map a module name to class loader throws an error
     * or runtime exception then it is propagated to the caller of this method.
     * </p>
     *
     * @apiNote It is implementation specific as to whether creating a Layer
     * with this method is an atomic operation or not. Consequentially it is
     * possible for this method to fail with some modules, but not all, defined
     * to Java virtual machine.
     *
     * @param  cf
     *         The configuration for the layer
     * @param  parentLayers
     *         The list parent layers in search order
     * @param  clf
     *         The function to map a module name to a class loader
     *
     * @return A controller that controls the newly created layer
     *
     * @throws IllegalArgumentException
     *         If the parent configurations do not match the configuration of
     *         the parent layers, including order
     * @throws LayerInstantiationException
     *         If creating the {@code Layer} fails for any of the reasons
     *         listed above, the layer cannot be created because the
     *         configuration contains a module named "{@code java.base}",
     *         a module with a package name starting with "{@code java.}" is
     *         mapped to a class loader other than the {@link
     *         ClassLoader#getPlatformClassLoader() platform class loader},
     *         or the function to map a module name to a class loader returns
     *         {@code null}
     * @throws SecurityException
     *         If {@code RuntimePermission("getClassLoader")} is denied by
     *         the security manager
     */
    public static Controller defineModules(Configuration cf,
                                           List<Layer> parentLayers,
                                           Function<String, ClassLoader> clf)
    {
        List<Layer> parents = new ArrayList<>(parentLayers);
        checkConfiguration(cf, parents);
        Objects.requireNonNull(clf);

        checkGetClassLoaderPermission();

        // For now, no two modules in the boot Layer may contain the same
        // package so we use a simple check for the boot Layer to keep
        // the overhead at startup to a minimum
        if (boot() == null) {
            checkBootModulesForDuplicatePkgs(cf);
        } else {
            checkForDuplicatePkgs(cf, clf);
        }

        try {
            Layer layer = new Layer(cf, parents, clf);
            return new Controller(layer);
        } catch (IllegalArgumentException iae) {
            // IAE is thrown by VM when defining the module fails
            throw new LayerInstantiationException(iae.getMessage());
        }
    }


    /**
     * Checks that the parent configurations match the configuration of
     * the parent layers.
     */
    private static void checkConfiguration(Configuration cf,
                                           List<Layer> parentLayers)
    {
        Objects.requireNonNull(cf);

        List<Configuration> parentConfigurations = cf.parents();
        if (parentLayers.size() != parentConfigurations.size())
            throw new IllegalArgumentException("wrong number of parents");

        int index = 0;
        for (Layer parent : parentLayers) {
            if (parent.configuration() != parentConfigurations.get(index)) {
                throw new IllegalArgumentException(
                        "Parent of configuration != configuration of this Layer");
            }
            index++;
        }
    }

    private static void checkCreateClassLoaderPermission() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null)
            sm.checkPermission(SecurityConstants.CREATE_CLASSLOADER_PERMISSION);
    }

    private static void checkGetClassLoaderPermission() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null)
            sm.checkPermission(SecurityConstants.GET_CLASSLOADER_PERMISSION);
    }

    /**
     * Checks a configuration for the boot Layer to ensure that no two modules
     * have the same package.
     *
     * @throws LayerInstantiationException
     */
    private static void checkBootModulesForDuplicatePkgs(Configuration cf) {
        Map<String, String> packageToModule = new HashMap<>();
        for (ResolvedModule resolvedModule : cf.modules()) {
            ModuleDescriptor descriptor = resolvedModule.reference().descriptor();
            String name = descriptor.name();
            for (String p : descriptor.packages()) {
                String other = packageToModule.putIfAbsent(p, name);
                if (other != null) {
                    throw fail("Package " + p + " in both module "
                               + name + " and module " + other);
                }
            }
        }
    }

    /**
     * Checks a configuration and the module-to-loader mapping to ensure that
     * no two modules mapped to the same class loader have the same package.
     * It also checks that no two automatic modules have the same package.
     *
     * @throws LayerInstantiationException
     */
    private static void checkForDuplicatePkgs(Configuration cf,
                                              Function<String, ClassLoader> clf)
    {
        // HashMap allows null keys
        Map<ClassLoader, Set<String>> loaderToPackages = new HashMap<>();
        for (ResolvedModule resolvedModule : cf.modules()) {
            ModuleDescriptor descriptor = resolvedModule.reference().descriptor();
            ClassLoader loader = clf.apply(descriptor.name());

            Set<String> loaderPackages
                = loaderToPackages.computeIfAbsent(loader, k -> new HashSet<>());

            for (String pkg : descriptor.packages()) {
                boolean added = loaderPackages.add(pkg);
                if (!added) {
                    throw fail("More than one module with package %s mapped" +
                               " to the same class loader", pkg);
                }
            }
        }
    }

    /**
     * Creates a LayerInstantiationException with the a message formatted from
     * the given format string and arguments.
     */
    private static LayerInstantiationException fail(String fmt, Object ... args) {
        String msg = String.format(fmt, args);
        return new LayerInstantiationException(msg);
    }


    /**
     * Returns the configuration for this layer.
     *
     * @return The configuration for this layer
     */
    public Configuration configuration() {
        return cf;
    }


    /**
     * Returns the list of this layer's parents unless this is the
     * {@linkplain #empty empty layer}, which has no parents and so an
     * empty list is returned.
     *
     * @return The list of this layer's parents
     */
    public List<Layer> parents() {
        return parents;
    }


    /**
     * Returns an ordered stream of layers. The first element is is this layer,
     * the remaining elements are the parent layers in DFS order.
     *
     * @implNote For now, the assumption is that the number of elements will
     * be very low and so this method does not use a specialized spliterator.
     */
    Stream<Layer> layers() {
        List<Layer> allLayers = this.allLayers;
        if (allLayers != null)
            return allLayers.stream();

        allLayers = new ArrayList<>();
        Set<Layer> visited = new HashSet<>();
        Deque<Layer> stack = new ArrayDeque<>();
        visited.add(this);
        stack.push(this);

        while (!stack.isEmpty()) {
            Layer layer = stack.pop();
            allLayers.add(layer);

            // push in reverse order
            for (int i = layer.parents.size() - 1; i >= 0; i--) {
                Layer parent = layer.parents.get(i);
                if (!visited.contains(parent)) {
                    visited.add(parent);
                    stack.push(parent);
                }
            }
        }

        this.allLayers = allLayers = Collections.unmodifiableList(allLayers);
        return allLayers.stream();
    }

    private volatile List<Layer> allLayers;

    /**
     * Returns the set of the modules in this layer.
     *
     * @return A possibly-empty unmodifiable set of the modules in this layer
     */
    public Set<Module> modules() {
        return Collections.unmodifiableSet(
                nameToModule.values().stream().collect(Collectors.toSet()));
    }


    /**
     * Returns the module with the given name in this layer, or if not in this
     * layer, the {@linkplain #parents parents} layers. Finding a module in
     * parent layers is equivalent to invoking {@code findModule} on each
     * parent, in search order, until the module is found or all parents have
     * been searched. In a <em>tree of layers</em>  then this is equivalent to
     * a depth-first search.
     *
     * @param  name
     *         The name of the module to find
     *
     * @return The module with the given name or an empty {@code Optional}
     *         if there isn't a module with this name in this layer or any
     *         parent layer
     */
    public Optional<Module> findModule(String name) {
        Objects.requireNonNull(name);
        Module m = nameToModule.get(name);
        if (m != null)
            return Optional.of(m);

        return layers()
                .skip(1)  // skip this layer
                .map(l -> l.nameToModule)
                .filter(map -> map.containsKey(name))
                .map(map -> map.get(name))
                .findAny();
    }


    /**
     * Returns the {@code ClassLoader} for the module with the given name. If
     * a module of the given name is not in this layer then the {@link #parents
     * parent} layers are searched in the manner specified by {@link
     * #findModule(String) findModule}.
     *
     * <p> If there is a security manager then its {@code checkPermission}
     * method is called with a {@code RuntimePermission("getClassLoader")}
     * permission to check that the caller is allowed to get access to the
     * class loader. </p>
     *
     * @apiNote This method does not return an {@code Optional<ClassLoader>}
     * because `null` must be used to represent the bootstrap class loader.
     *
     * @param  name
     *         The name of the module to find
     *
     * @return The ClassLoader that the module is defined to
     *
     * @throws IllegalArgumentException if a module of the given name is not
     *         defined in this layer or any parent of this layer
     *
     * @throws SecurityException if denied by the security manager
     */
    public ClassLoader findLoader(String name) {
        Optional<Module> om = findModule(name);

        // can't use map(Module::getClassLoader) as class loader can be null
        if (om.isPresent()) {
            return om.get().getClassLoader();
        } else {
            throw new IllegalArgumentException("Module " + name
                                               + " not known to this layer");
        }
    }

    /**
     * Returns a string describing this layer.
     *
     * @return A possibly empty string describing this layer
     */
    @Override
    public String toString() {
        return modules().stream()
                .map(Module::getName)
                .collect(Collectors.joining(", "));
    }

    /**
     * Returns the <em>empty</em> layer. There are no modules in the empty
     * layer. It has no parents.
     *
     * @return The empty layer
     */
    public static Layer empty() {
        return EMPTY_LAYER;
    }


    /**
     * Returns the boot layer. The boot layer contains at least one module,
     * {@code java.base}. Its parent is the {@link #empty() empty} layer.
     *
     * @apiNote This method returns {@code null} during startup and before
     *          the boot layer is fully initialized.
     *
     * @return The boot layer
     */
    public static Layer boot() {
        return SharedSecrets.getJavaLangAccess().getBootLayer();
    }


    /**
     * Returns the ServicesCatalog for this Layer, creating it if not
     * already created.
     */
    ServicesCatalog getServicesCatalog() {
        ServicesCatalog servicesCatalog = this.servicesCatalog;
        if (servicesCatalog != null)
            return servicesCatalog;

        synchronized (this) {
            servicesCatalog = this.servicesCatalog;
            if (servicesCatalog == null) {
                servicesCatalog = ServicesCatalog.create();
                nameToModule.values().forEach(servicesCatalog::register);
                this.servicesCatalog = servicesCatalog;
            }
        }

        return servicesCatalog;
    }

    private volatile ServicesCatalog servicesCatalog;


    /**
     * Record that this layer has at least one module defined to the given
     * class loader.
     */
    void bindToLoader(ClassLoader loader) {
        // CLV.computeIfAbsent(loader, (cl, clv) -> new CopyOnWriteArrayList<>())
        List<Layer> list = CLV.get(loader);
        if (list == null) {
            list = new CopyOnWriteArrayList<>();
            List<Layer> previous = CLV.putIfAbsent(loader, list);
            if (previous != null) list = previous;
        }
        list.add(this);
    }

    /**
     * Returns a stream of the layers that have at least one module defined to
     * the given class loader.
     */
    static Stream<Layer> layers(ClassLoader loader) {
        List<Layer> list = CLV.get(loader);
        if (list != null) {
            return list.stream();
        } else {
            return Stream.empty();
        }
    }

    // the list of layers with modules defined to a class loader
    private static final ClassLoaderValue<List<Layer>> CLV = new ClassLoaderValue<>();
}
