/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.jpackage.internal;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.spi.ToolProvider;
import java.util.jar.JarFile;
import java.lang.module.Configuration;
import java.lang.module.ResolvedModule;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import jdk.internal.module.ModulePath;


final class JLinkBundlerHelper {

    private static final ToolProvider JLINK_TOOL =
            ToolProvider.findFirst("jlink").orElseThrow();

    static void execute(Map<String, ? super Object> params,
            AbstractAppImageBuilder imageBuilder)
            throws IOException, Exception {

        List<Path> modulePath =
                StandardBundlerParam.MODULE_PATH.fetchFrom(params);
        Set<String> addModules =
                StandardBundlerParam.ADD_MODULES.fetchFrom(params);
        Set<String> limitModules =
                StandardBundlerParam.LIMIT_MODULES.fetchFrom(params);
        List<String> options =
                StandardBundlerParam.JLINK_OPTIONS.fetchFrom(params);
        Path outputDir = imageBuilder.getRuntimeRoot();

        LauncherData launcherData = StandardBundlerParam.LAUNCHER_DATA.fetchFrom(
                params);

        boolean bindServices =
                StandardBundlerParam.BIND_SERVICES.fetchFrom(params);

        // Modules
        if (!launcherData.isModular() && addModules.isEmpty()) {
            addModules.add(ModuleHelper.ALL_DEFAULT);
        }

        Set<String> modules = new ModuleHelper(
                modulePath, addModules, limitModules).modules();

        if (launcherData.isModular()) {
            modules.add(launcherData.moduleName());
        }

        runJLink(outputDir, modulePath, modules, limitModules,
                options, bindServices);

        imageBuilder.prepareApplicationFiles(params);
    }

    /*
     * Returns the set of modules that would be visible by default for
     * a non-modular-aware application consisting of the given elements.
     */
    private static Set<String> getDefaultModules(
            Collection<Path> paths, Collection<String> addModules) {

        // the modules in the run-time image that export an API
        Stream<String> systemRoots = ModuleFinder.ofSystem().findAll().stream()
                .map(ModuleReference::descriptor)
                .filter(JLinkBundlerHelper::exportsAPI)
                .map(ModuleDescriptor::name);

        Set<String> roots = Stream.concat(systemRoots,
                 addModules.stream()).collect(Collectors.toSet());

        ModuleFinder finder = createModuleFinder(paths);

        return Configuration.empty()
                .resolveAndBind(finder, ModuleFinder.of(), roots)
                .modules()
                .stream()
                .map(ResolvedModule::name)
                .collect(Collectors.toSet());
    }

    /*
     * Returns true if the given module exports an API to all module.
     */
    private static boolean exportsAPI(ModuleDescriptor descriptor) {
        return descriptor.exports()
                .stream()
                .anyMatch(e -> !e.isQualified());
    }

    static ModuleFinder createModuleFinder(Collection<Path> modulePath) {
        return ModuleFinder.compose(
                ModulePath.of(JarFile.runtimeVersion(), true,
                        modulePath.toArray(Path[]::new)),
                ModuleFinder.ofSystem());
    }

    private static class ModuleHelper {
        // The token for "all modules on the module path".
        private static final String ALL_MODULE_PATH = "ALL-MODULE-PATH";

        // The token for "all valid runtime modules".
        static final String ALL_DEFAULT = "ALL-DEFAULT";

        private final Set<String> modules = new HashSet<>();
        ModuleHelper(List<Path> paths, Set<String> addModules,
                Set<String> limitModules) {
            boolean addAllModulePath = false;
            boolean addDefaultMods = false;

            for (Iterator<String> iterator = addModules.iterator();
                    iterator.hasNext();) {
                String module = iterator.next();

                switch (module) {
                    case ALL_MODULE_PATH:
                        iterator.remove();
                        addAllModulePath = true;
                        break;
                    case ALL_DEFAULT:
                        iterator.remove();
                        addDefaultMods = true;
                        break;
                    default:
                        this.modules.add(module);
                }
            }

            if (addAllModulePath) {
                this.modules.addAll(getModuleNamesFromPath(paths));
            } else if (addDefaultMods) {
                this.modules.addAll(getDefaultModules(
                        paths, addModules));
            }
        }

        Set<String> modules() {
            return modules;
        }

        private static Set<String> getModuleNamesFromPath(List<Path> paths) {

            return createModuleFinder(paths)
                    .findAll()
                    .stream()
                    .map(ModuleReference::descriptor)
                    .map(ModuleDescriptor::name)
                    .collect(Collectors.toSet());
        }
    }

    private static void runJLink(Path output, List<Path> modulePath,
            Set<String> modules, Set<String> limitModules,
            List<String> options, boolean bindServices)
            throws PackagerException {

        // This is just to ensure jlink is given a non-existant directory
        // The passed in output path should be non-existant or empty directory
        try {
            IOUtils.deleteRecursive(output.toFile());
        } catch (IOException ioe) {
            throw new PackagerException(ioe);
        }

        ArrayList<String> args = new ArrayList<String>();
        args.add("--output");
        args.add(output.toString());
        if (modulePath != null && !modulePath.isEmpty()) {
            args.add("--module-path");
            args.add(getPathList(modulePath));
        }
        if (modules != null && !modules.isEmpty()) {
            args.add("--add-modules");
            args.add(getStringList(modules));
        }
        if (limitModules != null && !limitModules.isEmpty()) {
            args.add("--limit-modules");
            args.add(getStringList(limitModules));
        }
        if (options != null) {
            for (String option : options) {
                if (option.startsWith("--output") ||
                        option.startsWith("--add-modules") ||
                        option.startsWith("--module-path")) {
                    throw new PackagerException("error.blocked.option", option);
                }
                args.add(option);
            }
        }
        if (bindServices) {
            args.add("--bind-services");
        }

        StringWriter writer = new StringWriter();
        PrintWriter pw = new PrintWriter(writer);

        Log.verbose("jlink arguments: " + args);
        int retVal = JLINK_TOOL.run(pw, pw, args.toArray(new String[0]));
        String jlinkOut = writer.toString();

        if (retVal != 0) {
            throw new PackagerException("error.jlink.failed" , jlinkOut);
        } else if (jlinkOut.length() > 0) {
            Log.verbose("jlink output: " + jlinkOut);
        }
    }

    private static String getPathList(List<Path> pathList) {
        return pathList.stream()
                .map(Path::toString)
                .map(Matcher::quoteReplacement)
                .collect(Collectors.joining(File.pathSeparator));
    }

    private static String getStringList(Set<String> strings) {
        return Matcher.quoteReplacement(strings.stream().collect(
                Collectors.joining(",")));
    }
}
