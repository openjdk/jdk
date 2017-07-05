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
import java.lang.module.Configuration;
import java.lang.module.ModuleReference;
import java.lang.module.ModuleFinder;
import java.lang.module.ResolvedModule;
import java.lang.reflect.Layer;
import java.lang.reflect.Module;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jdk.internal.loader.BootLoader;
import jdk.internal.loader.BuiltinClassLoader;
import jdk.internal.perf.PerfCounter;

/**
 * Initializes/boots the module system.
 *
 * The {@link #boot() boot} method is called early in the startup to initialize
 * the module system. In summary, the boot method creates a Configuration by
 * resolving a set of module names specified via the launcher (or equivalent)
 * -m and -addmods options. The modules are located on a module path that is
 * constructed from the upgrade, system and application module paths. The
 * Configuration is reified by creating the boot Layer with each module in the
 * the configuration defined to one of the built-in class loaders. The mapping
 * of modules to class loaders is statically mapped in a helper class.
 */

public final class ModuleBootstrap {
    private ModuleBootstrap() { }

    private static final String JAVA_BASE = "java.base";

    // the token for "all unnamed modules"
    private static final String ALL_UNNAMED = "ALL-UNNAMED";

    // the token for "all system modules"
    private static final String ALL_SYSTEM = "ALL-SYSTEM";

    // the token for "all modules on the module path"
    private static final String ALL_MODULE_PATH = "ALL-MODULE-PATH";

    // ModuleFinder for the initial configuration
    private static ModuleFinder initialFinder;

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

        // system module path
        ModuleFinder systemModulePath = ModuleFinder.ofSystem();

        // Once we have the system module path then we define the base module.
        // We do this here so that java.base is defined to the VM as early as
        // possible and also that resources in the base module can be located
        // for error messages that may happen from here on.
        Optional<ModuleReference> obase = systemModulePath.find(JAVA_BASE);
        if (!obase.isPresent())
            throw new InternalError(JAVA_BASE + " not found");
        ModuleReference base = obase.get();
        BootLoader.loadModule(base);
        Modules.defineModule(null, base.descriptor(), base.location().orElse(null));


        // -upgrademodulepath option specified to launcher
        ModuleFinder upgradeModulePath
            = createModulePathFinder("jdk.upgrade.module.path");

        // -modulepath option specified to the launcher
        ModuleFinder appModulePath = createModulePathFinder("jdk.module.path");

        // The module finder: [-upgrademodulepath] system-module-path [-modulepath]
        ModuleFinder finder = systemModulePath;
        if (upgradeModulePath != null)
            finder = ModuleFinder.compose(upgradeModulePath, finder);
        if (appModulePath != null)
            finder = ModuleFinder.compose(finder, appModulePath);

        // launcher -m option to specify the initial module
        String mainModule = System.getProperty("jdk.module.main");

        // additional module(s) specified by -addmods
        boolean addAllSystemModules = false;
        boolean addAllApplicationModules = false;
        Set<String> addModules = null;
        String propValue = System.getProperty("jdk.launcher.addmods");
        if (propValue != null) {
            addModules = new HashSet<>();
            for (String mod: propValue.split(",")) {
                switch (mod) {
                    case ALL_SYSTEM:
                        addAllSystemModules = true;
                        break;
                    case ALL_MODULE_PATH:
                        addAllApplicationModules = true;
                        break;
                    default :
                        addModules.add(mod);
                }
            }
        }

        // The root modules to resolve
        Set<String> roots = new HashSet<>();

        // main/initial module
        if (mainModule != null) {
            roots.add(mainModule);
            if (addAllApplicationModules)
                fail(ALL_MODULE_PATH + " not allowed with initial module");
        }

        // If -addmods is specified then those modules need to be resolved
        if (addModules != null)
            roots.addAll(addModules);


        // -limitmods
        boolean limitmods = false;
        propValue = System.getProperty("jdk.launcher.limitmods");
        if (propValue != null) {
            Set<String> mods = new HashSet<>();
            for (String mod: propValue.split(",")) {
                mods.add(mod);
            }
            finder = limitFinder(finder, mods, roots);
            limitmods = true;
        }


        // If there is no initial module specified then assume that the
        // initial module is the unnamed module of the application class
        // loader. By convention, and for compatibility, this is
        // implemented by putting the names of all modules on the system
        // module path into the set of modules to resolve.
        //
        // If `-addmods ALL-SYSTEM` is used then all modules on the system
        // module path will be resolved, irrespective of whether an initial
        // module is specified.
        //
        // If `-addmods ALL-MODULE-PATH` is used, and no initial module is
        // specified, then all modules on the application module path will
        // be resolved.
        //
        if (mainModule == null || addAllSystemModules) {
            Set<ModuleReference> mrefs;
            if (addAllApplicationModules) {
                assert mainModule == null;
                mrefs = finder.findAll();
            } else {
                mrefs = systemModulePath.findAll();
                if (limitmods) {
                    ModuleFinder f = finder;
                    mrefs = mrefs.stream()
                        .filter(m -> f.find(m.descriptor().name()).isPresent())
                        .collect(Collectors.toSet());
                }
            }
            // map to module names
            for (ModuleReference mref : mrefs) {
                roots.add(mref.descriptor().name());
            }
        }

        long t1 = System.nanoTime();

        // run the resolver to create the configuration

        Configuration cf = Configuration.empty()
                .resolveRequiresAndUses(finder,
                                        ModuleFinder.empty(),
                                        roots);

        // time to create configuration
        PerfCounters.resolveTime.addElapsedTimeFrom(t1);

        // mapping of modules to class loaders
        Function<String, ClassLoader> clf = ModuleLoaderMap.mappingFunction(cf);

        // check that all modules to be mapped to the boot loader will be
        // loaded from the system module path
        if (finder != systemModulePath) {
            for (ResolvedModule resolvedModule : cf.modules()) {
                ModuleReference mref = resolvedModule.reference();
                String name = mref.descriptor().name();
                ClassLoader cl = clf.apply(name);
                if (cl == null) {

                    if (upgradeModulePath != null
                            && upgradeModulePath.find(name).isPresent())
                        fail(name + ": cannot be loaded from upgrade module path");

                    if (!systemModulePath.find(name).isPresent())
                        fail(name + ": cannot be loaded from application module path");
                }
            }
        }

        long t2 = System.nanoTime();

        // define modules to VM/runtime
        Layer bootLayer = Layer.empty().defineModules(cf, clf);

        PerfCounters.layerCreateTime.addElapsedTimeFrom(t2);

        long t3 = System.nanoTime();

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

        PerfCounters.loadModulesTime.addElapsedTimeFrom(t3);

        // -XaddReads and -XaddExports
        addExtraReads(bootLayer);
        addExtraExports(bootLayer);

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
                                 ModuleFinder.empty(),
                                 roots);

        // module name -> reference
        Map<String, ModuleReference> map = new HashMap<>();
        cf.modules().stream()
            .map(ResolvedModule::reference)
            .forEach(mref -> map.put(mref.descriptor().name(), mref));

        // set of modules that are observable
        Set<ModuleReference> mrefs = new HashSet<>(map.values());

        // add the other modules
        for (String mod : otherMods) {
            Optional<ModuleReference> omref = finder.find(mod);
            if (omref.isPresent()) {
                ModuleReference mref = omref.get();
                map.putIfAbsent(mod, mref);
                mrefs.add(mref);
            } else {
                // no need to fail
            }
        }

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
     * Process the -XaddReads options to add any additional read edges that
     * are specified on the command-line.
     */
    private static void addExtraReads(Layer bootLayer) {

        // decode the command line options
        Map<String, Set<String>> map = decode("jdk.launcher.addreads.");

        for (Map.Entry<String, Set<String>> e : map.entrySet()) {

            // the key is $MODULE
            String mn = e.getKey();
            Optional<Module> om = bootLayer.findModule(mn);
            if (!om.isPresent())
                fail("Unknown module: " + mn);
            Module m = om.get();

            // the value is the set of other modules (by name)
            for (String name : e.getValue()) {

                Module other;
                if (ALL_UNNAMED.equals(name)) {
                    other = null;  // loose
                } else {
                    om = bootLayer.findModule(name);
                    if (!om.isPresent())
                        fail("Unknown module: " + name);
                    other = om.get();
                }

                Modules.addReads(m, other);
            }
        }
    }


    /**
     * Process the -XaddExports options to add any additional read edges that
     * are specified on the command-line.
     */
    private static void addExtraExports(Layer bootLayer) {

        // decode the command line options
        Map<String, Set<String>> map = decode("jdk.launcher.addexports.");

        for (Map.Entry<String, Set<String>> e : map.entrySet()) {

            // the key is $MODULE/$PACKAGE
            String key = e.getKey();
            String[] s = key.split("/");
            if (s.length != 2)
                fail("Unable to parse: " + key);

            String mn = s[0];
            String pn = s[1];

            // The exporting module is in the boot layer
            Module m;
            Optional<Module> om = bootLayer.findModule(mn);
            if (!om.isPresent())
                fail("Unknown module: " + mn);
            m = om.get();

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
                        fail("Unknown module: " + name);
                    }
                }

                if (allUnnamed) {
                    Modules.addExportsToAllUnnamed(m, pn);
                } else {
                    Modules.addExports(m, pn, other);
                }
            }
        }
    }


    /**
     * Decodes the values of -XaddReads or -XaddExports options
     *
     * The format of the options is: $KEY=$MODULE(,$MODULE)*
     *
     * For transition purposes, this method allows the first usage to be
     *     $KEY=$MODULE(,$KEY=$MODULE)
     * This format will eventually be removed.
     */
    private static Map<String, Set<String>> decode(String prefix) {
        int index = 0;
        String value = System.getProperty(prefix + index);
        if (value == null)
            return Collections.emptyMap();

        Map<String, Set<String>> map = new HashMap<>();

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

            // new format $MODULE(,$MODULE)* or old format $(MODULE)=...
            pos = rhs.indexOf('=');

            // old format only allowed in first -X option
            if (pos >= 0 && index > 0)
                fail("Unable to parse: " + value);

            if (pos == -1) {

                // new format: $KEY=$MODULE(,$MODULE)*

                Set<String> values = map.get(key);
                if (values != null)
                    fail(key + " specified more than once");

                values = new HashSet<>();
                map.put(key, values);
                for (String s : rhs.split(",")) {
                    if (s.length() > 0) values.add(s);
                }

            } else {

                // old format: $KEY=$MODULE(,$KEY=$MODULE)*

                assert index == 0;  // old format only allowed in first usage

                for (String expr : value.split(",")) {
                    if (expr.length() > 0) {
                        String[] s = expr.split("=");
                        if (s.length != 2)
                            fail("Unable to parse: " + expr);

                        map.computeIfAbsent(s[0], k -> new HashSet<>()).add(s[1]);
                    }
                }
            }

            index++;
            value = System.getProperty(prefix + index);
        }

        return map;
    }


    /**
     * Throws a RuntimeException with the given message
     */
    static void fail(String m) {
        throw new RuntimeException(m);
    }

    static class PerfCounters {
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
