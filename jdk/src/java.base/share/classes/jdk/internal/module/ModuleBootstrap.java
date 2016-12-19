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

package jdk.internal.module;

import java.io.File;
import java.io.PrintStream;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.lang.reflect.Layer;
import java.lang.reflect.Module;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import jdk.internal.loader.BootLoader;
import jdk.internal.loader.BuiltinClassLoader;
import jdk.internal.misc.SharedSecrets;
import jdk.internal.perf.PerfCounter;

/**
 * Initializes/boots the module system.
 *
 * The {@link #boot() boot} method is called early in the startup to initialize
 * the module system. In summary, the boot method creates a Configuration by
 * resolving a set of module names specified via the launcher (or equivalent)
 * -m and --add-modules options. The modules are located on a module path that
 * is constructed from the upgrade module path, system modules, and application
 * module path. The Configuration is instantiated as the boot Layer with each
 * module in the the configuration defined to one of the built-in class loaders.
 */

public final class ModuleBootstrap {
    private ModuleBootstrap() { }

    private static final String JAVA_BASE = "java.base";

    private static final String JAVA_SE = "java.se";

    // the token for "all default modules"
    private static final String ALL_DEFAULT = "ALL-DEFAULT";

    // the token for "all unnamed modules"
    private static final String ALL_UNNAMED = "ALL-UNNAMED";

    // the token for "all system modules"
    private static final String ALL_SYSTEM = "ALL-SYSTEM";

    // the token for "all modules on the module path"
    private static final String ALL_MODULE_PATH = "ALL-MODULE-PATH";

    // The ModulePatcher for the initial configuration
    private static final ModulePatcher patcher = initModulePatcher();

    // ModuleFinder for the initial configuration
    private static ModuleFinder initialFinder;

    /**
     * Returns the ModulePatcher for the initial configuration.
     */
    public static ModulePatcher patcher() {
        return patcher;
    }

    /**
     * Returns the ModuleFinder for the initial configuration
     */
    public static ModuleFinder finder() {
        assert initialFinder != null;
        return initialFinder;
    }

    /**
     * Initialize the module system, returning the boot Layer.
     *
     * @see java.lang.System#initPhase2()
     */
    public static Layer boot() {

        long t0 = System.nanoTime();

        // system modules (may be patched)
        ModuleFinder systemModules = ModuleFinder.ofSystem();

        PerfCounters.systemModulesTime.addElapsedTimeFrom(t0);


        long t1 = System.nanoTime();

        // Once we have the system modules then we define the base module to
        // the VM. We do this here so that java.base is defined as early as
        // possible and also that resources in the base module can be located
        // for error messages that may happen from here on.
        ModuleReference base = systemModules.find(JAVA_BASE).orElse(null);
        if (base == null)
            throw new InternalError(JAVA_BASE + " not found");
        URI baseUri = base.location().orElse(null);
        if (baseUri == null)
            throw new InternalError(JAVA_BASE + " does not have a location");
        BootLoader.loadModule(base);
        Modules.defineModule(null, base.descriptor(), baseUri);

        PerfCounters.defineBaseTime.addElapsedTimeFrom(t1);


        long t2 = System.nanoTime();

        // --upgrade-module-path option specified to launcher
        ModuleFinder upgradeModulePath
            = createModulePathFinder("jdk.module.upgrade.path");
        if (upgradeModulePath != null)
            systemModules = ModuleFinder.compose(upgradeModulePath, systemModules);

        // --module-path option specified to the launcher
        ModuleFinder appModulePath = createModulePathFinder("jdk.module.path");

        // The module finder: [--upgrade-module-path] system [--module-path]
        ModuleFinder finder = systemModules;
        if (appModulePath != null)
            finder = ModuleFinder.compose(finder, appModulePath);

        // The root modules to resolve
        Set<String> roots = new HashSet<>();

        // launcher -m option to specify the main/initial module
        String mainModule = System.getProperty("jdk.module.main");
        if (mainModule != null)
            roots.add(mainModule);

        // additional module(s) specified by --add-modules
        boolean addAllDefaultModules = false;
        boolean addAllSystemModules = false;
        boolean addAllApplicationModules = false;
        for (String mod: getExtraAddModules()) {
            switch (mod) {
                case ALL_DEFAULT:
                    addAllDefaultModules = true;
                    break;
                case ALL_SYSTEM:
                    addAllSystemModules = true;
                    break;
                case ALL_MODULE_PATH:
                    addAllApplicationModules = true;
                    break;
                default :
                    roots.add(mod);
            }
        }

        // --limit-modules
        String propValue = getAndRemoveProperty("jdk.module.limitmods");
        if (propValue != null) {
            Set<String> mods = new HashSet<>();
            for (String mod: propValue.split(",")) {
                mods.add(mod);
            }
            finder = limitFinder(finder, mods, roots);
        }

        // If there is no initial module specified then assume that the initial
        // module is the unnamed module of the application class loader. This
        // is implemented by resolving "java.se" and all (non-java.*) modules
        // that export an API. If "java.se" is not observable then all java.*
        // modules are resolved.
        if (mainModule == null || addAllDefaultModules) {
            boolean hasJava = false;
            if (systemModules.find(JAVA_SE).isPresent()) {
                // java.se is a system module
                if (finder == systemModules || finder.find(JAVA_SE).isPresent()) {
                    // java.se is observable
                    hasJava = true;
                    roots.add(JAVA_SE);
                }
            }

            for (ModuleReference mref : systemModules.findAll()) {
                String mn = mref.descriptor().name();
                if (hasJava && mn.startsWith("java."))
                    continue;

                // add as root if observable and exports at least one package
                if ((finder == systemModules || finder.find(mn).isPresent())) {
                    ModuleDescriptor descriptor = mref.descriptor();
                    for (ModuleDescriptor.Exports e : descriptor.exports()) {
                        if (!e.isQualified()) {
                            roots.add(mn);
                            break;
                        }
                    }
                }
            }
        }

        // If `--add-modules ALL-SYSTEM` is specified then all observable system
        // modules will be resolved.
        if (addAllSystemModules) {
            ModuleFinder f = finder;  // observable modules
            systemModules.findAll()
                .stream()
                .map(ModuleReference::descriptor)
                .map(ModuleDescriptor::name)
                .filter(mn -> f.find(mn).isPresent())  // observable
                .forEach(mn -> roots.add(mn));
        }

        // If `--add-modules ALL-MODULE-PATH` is specified then all observable
        // modules on the application module path will be resolved.
        if (appModulePath != null && addAllApplicationModules) {
            ModuleFinder f = finder;  // observable modules
            appModulePath.findAll()
                .stream()
                .map(ModuleReference::descriptor)
                .map(ModuleDescriptor::name)
                .filter(mn -> f.find(mn).isPresent())  // observable
                .forEach(mn -> roots.add(mn));
        }

        PerfCounters.optionsAndRootsTime.addElapsedTimeFrom(t2);


        long t3 = System.nanoTime();

        // determine if post resolution checks are needed
        boolean needPostResolutionChecks = true;
        if (baseUri.getScheme().equals("jrt")   // toLowerCase not needed here
                && (upgradeModulePath == null)
                && (appModulePath == null)
                && (patcher.isEmpty())) {
            needPostResolutionChecks = false;
        }

        PrintStream traceOutput = null;
        if (Boolean.getBoolean("jdk.launcher.traceResolver"))
            traceOutput = System.out;

        // run the resolver to create the configuration
        Configuration cf = SharedSecrets.getJavaLangModuleAccess()
                .resolveRequiresAndUses(finder,
                                        roots,
                                        needPostResolutionChecks,
                                        traceOutput);

        // time to create configuration
        PerfCounters.resolveTime.addElapsedTimeFrom(t3);


        // mapping of modules to class loaders
        Function<String, ClassLoader> clf = ModuleLoaderMap.mappingFunction(cf);

        // check that all modules to be mapped to the boot loader will be
        // loaded from the runtime image
        if (needPostResolutionChecks) {
            for (ResolvedModule resolvedModule : cf.modules()) {
                ModuleReference mref = resolvedModule.reference();
                String name = mref.descriptor().name();
                ClassLoader cl = clf.apply(name);
                if (cl == null) {

                    if (upgradeModulePath != null
                            && upgradeModulePath.find(name).isPresent())
                        fail(name + ": cannot be loaded from upgrade module path");

                    if (!systemModules.find(name).isPresent())
                        fail(name + ": cannot be loaded from application module path");
                }
            }
        }


        long t4 = System.nanoTime();

        // define modules to VM/runtime
        Layer bootLayer = Layer.empty().defineModules(cf, clf);

        PerfCounters.layerCreateTime.addElapsedTimeFrom(t4);


        long t5 = System.nanoTime();

        // define the module to its class loader, except java.base
        for (ResolvedModule resolvedModule : cf.modules()) {
            ModuleReference mref = resolvedModule.reference();
            String name = mref.descriptor().name();
            ClassLoader cl = clf.apply(name);
            if (cl == null) {
                if (!name.equals(JAVA_BASE)) BootLoader.loadModule(mref);
            } else {
                ((BuiltinClassLoader)cl).loadModule(mref);
            }
        }

        PerfCounters.loadModulesTime.addElapsedTimeFrom(t5);


        // --add-reads, -add-exports/-add-opens
        addExtraReads(bootLayer);
        addExtraExportsAndOpens(bootLayer);

        // total time to initialize
        PerfCounters.bootstrapTime.addElapsedTimeFrom(t0);

        // remember the ModuleFinder
        initialFinder = finder;

        return bootLayer;
    }

    /**
     * Returns a ModuleFinder that limits observability to the given root
     * modules, their transitive dependences, plus a set of other modules.
     */
    private static ModuleFinder limitFinder(ModuleFinder finder,
                                            Set<String> roots,
                                            Set<String> otherMods)
    {
        // resolve all root modules
        Configuration cf = Configuration.empty()
                .resolveRequires(finder,
                                 ModuleFinder.of(),
                                 roots);

        // module name -> reference
        Map<String, ModuleReference> map = new HashMap<>();

        // root modules and their transitive dependences
        cf.modules().stream()
            .map(ResolvedModule::reference)
            .forEach(mref -> map.put(mref.descriptor().name(), mref));

        // additional modules
        otherMods.stream()
            .map(finder::find)
            .flatMap(Optional::stream)
            .forEach(mref -> map.putIfAbsent(mref.descriptor().name(), mref));

        // set of modules that are observable
        Set<ModuleReference> mrefs = new HashSet<>(map.values());

        return new ModuleFinder() {
            @Override
            public Optional<ModuleReference> find(String name) {
                return Optional.ofNullable(map.get(name));
            }
            @Override
            public Set<ModuleReference> findAll() {
                return mrefs;
            }
        };
    }

    /**
     * Creates a finder from the module path that is the value of the given
     * system property.
     */
    private static ModuleFinder createModulePathFinder(String prop) {
        String s = System.getProperty(prop);
        if (s == null) {
            return null;
        } else {
            String[] dirs = s.split(File.pathSeparator);
            Path[] paths = new Path[dirs.length];
            int i = 0;
            for (String dir: dirs) {
                paths[i++] = Paths.get(dir);
            }
            return ModuleFinder.of(paths);
        }
    }


    /**
     * Initialize the module patcher for the initial configuration passed on the
     * value of the --patch-module options.
     */
    private static ModulePatcher initModulePatcher() {
        Map<String, List<String>> map = decode("jdk.module.patch.",
                                               File.pathSeparator,
                                               false);
        return new ModulePatcher(map);
    }

    /**
     * Returns the set of module names specified via --add-modules options
     * on the command line
     */
    private static Set<String> getExtraAddModules() {
        String prefix = "jdk.module.addmods.";
        int index = 0;

        // the system property is removed after decoding
        String value = getAndRemoveProperty(prefix + index);
        if (value == null) {
            return Collections.emptySet();
        }

        Set<String> modules = new HashSet<>();
        while (value != null) {
            for (String s : value.split(",")) {
                if (s.length() > 0) modules.add(s);
            }
            index++;
            value = getAndRemoveProperty(prefix + index);
        }

        return modules;
    }

    /**
     * Process the --add-reads options to add any additional read edges that
     * are specified on the command-line.
     */
    private static void addExtraReads(Layer bootLayer) {

        // decode the command line options
        Map<String, List<String>> map = decode("jdk.module.addreads.");
        if (map.isEmpty())
            return;

        for (Map.Entry<String, List<String>> e : map.entrySet()) {

            // the key is $MODULE
            String mn = e.getKey();
            Optional<Module> om = bootLayer.findModule(mn);
            if (!om.isPresent()) {
                warn("Unknown module: " + mn);
                continue;
            }
            Module m = om.get();

            // the value is the set of other modules (by name)
            for (String name : e.getValue()) {
                if (ALL_UNNAMED.equals(name)) {
                    Modules.addReadsAllUnnamed(m);
                } else {
                    om = bootLayer.findModule(name);
                    if (om.isPresent()) {
                        Modules.addReads(m, om.get());
                    } else {
                        warn("Unknown module: " + name);
                    }
                }
            }
        }
    }

    /**
     * Process the --add-exports and --add-opens options to export/open
     * additional packages specified on the command-line.
     */
    private static void addExtraExportsAndOpens(Layer bootLayer) {

        // --add-exports
        String prefix = "jdk.module.addexports.";
        Map<String, List<String>> extraExports = decode(prefix);
        if (!extraExports.isEmpty()) {
            addExtraExportsOrOpens(bootLayer, extraExports, false);
        }

        // --add-opens
        prefix = "jdk.module.addopens.";
        Map<String, List<String>> extraOpens = decode(prefix);
        if (!extraOpens.isEmpty()) {
            addExtraExportsOrOpens(bootLayer, extraOpens, true);
        }
    }

    private static void addExtraExportsOrOpens(Layer bootLayer,
                                               Map<String, List<String>> map,
                                               boolean opens)
    {
        for (Map.Entry<String, List<String>> e : map.entrySet()) {

            // the key is $MODULE/$PACKAGE
            String key = e.getKey();
            String[] s = key.split("/");
            if (s.length != 2)
                fail("Unable to parse: " + key);

            String mn = s[0];
            String pn = s[1];
            if (mn.isEmpty() || pn.isEmpty())
                fail("Module and package name must be specified:" + key);

            // The exporting module is in the boot layer
            Module m;
            Optional<Module> om = bootLayer.findModule(mn);
            if (!om.isPresent()) {
                warn("Unknown module: " + mn);
                continue;
            }

            m = om.get();

            if (!m.getDescriptor().packages().contains(pn)) {
                warn("package " + pn + " not in " + mn);
                continue;
            }

            // the value is the set of modules to export to (by name)
            for (String name : e.getValue()) {
                boolean allUnnamed = false;
                Module other = null;
                if (ALL_UNNAMED.equals(name)) {
                    allUnnamed = true;
                } else {
                    om = bootLayer.findModule(name);
                    if (om.isPresent()) {
                        other = om.get();
                    } else {
                        warn("Unknown module: " + name);
                        continue;
                    }
                }
                if (allUnnamed) {
                    if (opens) {
                        Modules.addOpensToAllUnnamed(m, pn);
                    } else {
                        Modules.addExportsToAllUnnamed(m, pn);
                    }
                } else {
                    if (opens) {
                        Modules.addOpens(m, pn, other);
                    } else {
                        Modules.addExports(m, pn, other);
                    }
                }

            }
        }
    }

    /**
     * Decodes the values of --add-reads, -add-exports, --add-opens or
     * --patch-modules options that are encoded in system properties.
     *
     * @param prefix the system property prefix
     * @praam regex the regex for splitting the RHS of the option value
     */
    private static Map<String, List<String>> decode(String prefix,
                                                    String regex,
                                                    boolean allowDuplicates) {
        int index = 0;
        // the system property is removed after decoding
        String value = getAndRemoveProperty(prefix + index);
        if (value == null)
            return Collections.emptyMap();

        Map<String, List<String>> map = new HashMap<>();

        while (value != null) {

            int pos = value.indexOf('=');
            if (pos == -1)
                fail("Unable to parse: " + value);
            if (pos == 0)
                fail("Missing module name in: " + value);

            // key is <module> or <module>/<package>
            String key = value.substring(0, pos);

            String rhs = value.substring(pos+1);
            if (rhs.isEmpty())
                fail("Unable to parse: " + value);

            // value is <module>(,<module>)* or <file>(<pathsep><file>)*
            if (!allowDuplicates && map.containsKey(key))
                fail(key + " specified more than once");
            List<String> values = map.computeIfAbsent(key, k -> new ArrayList<>());
            for (String s : rhs.split(regex)) {
                if (s.length() > 0) values.add(s);
            }

            index++;
            value = getAndRemoveProperty(prefix + index);
        }

        return map;
    }

    /**
     * Decodes the values of --add-reads, -add-exports or --add-opens
     * which use the "," to separate the RHS of the option value.
     */
    private static Map<String, List<String>> decode(String prefix) {
        return decode(prefix, ",", true);
    }

    /**
     * Gets and remove the named system property
     */
    private static String getAndRemoveProperty(String key) {
        return (String)System.getProperties().remove(key);
    }

    /**
     * Throws a RuntimeException with the given message
     */
    static void fail(String m) {
        throw new RuntimeException(m);
    }

    static void warn(String m) {
        System.err.println("WARNING: " + m);
    }

    static class PerfCounters {

        static PerfCounter systemModulesTime
            = PerfCounter.newPerfCounter("jdk.module.bootstrap.systemModulesTime");
        static PerfCounter defineBaseTime
            = PerfCounter.newPerfCounter("jdk.module.bootstrap.defineBaseTime");
        static PerfCounter optionsAndRootsTime
            = PerfCounter.newPerfCounter("jdk.module.bootstrap.optionsAndRootsTime");
        static PerfCounter resolveTime
            = PerfCounter.newPerfCounter("jdk.module.bootstrap.resolveTime");
        static PerfCounter layerCreateTime
            = PerfCounter.newPerfCounter("jdk.module.bootstrap.layerCreateTime");
        static PerfCounter loadModulesTime
            = PerfCounter.newPerfCounter("jdk.module.bootstrap.loadModulesTime");
        static PerfCounter bootstrapTime
            = PerfCounter.newPerfCounter("jdk.module.bootstrap.totalTime");
    }
}
