/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.jar.JarFile;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.internal.module.ModulePath;
import jdk.jpackage.internal.model.AppImageLayout;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.LauncherModularStartupInfo;
import jdk.jpackage.internal.model.LauncherStartupInfo;
import jdk.jpackage.internal.model.PackagerException;
import jdk.jpackage.internal.model.RuntimeBuilder;

final class JLinkRuntimeBuilder implements RuntimeBuilder {

    private JLinkRuntimeBuilder(List<String> jlinkCmdLine) {
        this.jlinkCmdLine = jlinkCmdLine;
    }

    @Override
    public void createRuntime(AppImageLayout appImageLayout) throws PackagerException {
        var args = new ArrayList<String>();
        args.add("--output");
        args.add(appImageLayout.runtimeDirectory().toString());
        args.addAll(jlinkCmdLine);

        StringWriter writer = new StringWriter();
        PrintWriter pw = new PrintWriter(writer);

        int retVal = LazyLoad.JLINK_TOOL.run(pw, pw, args.toArray(String[]::new));
        String jlinkOut = writer.toString();

        args.add(0, "jlink");
        Log.verbose(args, List.of(jlinkOut), retVal, -1);
        if (retVal != 0) {
            throw new PackagerException("error.jlink.failed", jlinkOut);
        }
    }

    static ModuleFinder createModuleFinder(Collection<Path> modulePath) {
        return ModuleFinder.compose(
                ModulePath.of(JarFile.runtimeVersion(), true,
                        modulePath.toArray(Path[]::new)),
                ModuleFinder.ofSystem());
    }

    static RuntimeBuilder createJLinkRuntimeBuilder(List<Path> modulePath, Set<String> addModules,
            Set<String> limitModules, List<String> options, List<LauncherStartupInfo> startupInfos) throws ConfigException {
        return new JLinkRuntimeBuilder(createJLinkCmdline(modulePath, addModules, limitModules,
                options, startupInfos));
    }

    private static List<String> createJLinkCmdline(List<Path> modulePath, Set<String> addModules,
            Set<String> limitModules, List<String> options, List<LauncherStartupInfo> startupInfos) throws ConfigException {
        List<String> launcherModules = startupInfos.stream().map(si -> {
            if (si instanceof LauncherModularStartupInfo siModular) {
                return siModular.moduleName();
            } else {
                return (String) null;
            }
        }).filter(Objects::nonNull).toList();

        if (launcherModules.isEmpty() && addModules.isEmpty()) {
            addModules = Set.of(ALL_DEFAULT);
        }

        var modules = createModuleList(modulePath, addModules, limitModules);

        modules.addAll(launcherModules);

        var args = new ArrayList<String>();
        if (!modulePath.isEmpty()) {
            args.add("--module-path");
            args.add(getPathList(modulePath));
        }
        if (!modules.isEmpty()) {
            args.add("--add-modules");
            args.add(getStringList(modules));
        }
        if (!limitModules.isEmpty()) {
            args.add("--limit-modules");
            args.add(getStringList(limitModules));
        }

        for (String option : options) {
            switch (option) {
                case "--output", "--add-modules", "--module-path" -> {
                    throw new ConfigException(MessageFormat.format(I18N.getString(
                            "error.blocked.option"), option), null);
                }
                default -> {
                    args.add(option);
                }
            }
        }

        return args;
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
                .filter(JLinkRuntimeBuilder::exportsAPI)
                .map(ModuleDescriptor::name);

        Set<String> roots = Stream.concat(systemRoots,
                addModules.stream()).collect(Collectors.toSet());

        ModuleFinder finder = createModuleFinder(paths);

        // Don't perform service bindings by default as outlined by JEP 343
        // and JEP 392
        return Configuration.empty()
                .resolve(finder, ModuleFinder.of(), roots)
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

    private static Set<String> createModuleList(List<Path> paths,
            Set<String> addModules, Set<String> limitModules) {

        final Set<String> modules = new HashSet<>();

        final Map<String, Supplier<Collection<String>>> phonyModules = Map.of(
                ALL_MODULE_PATH,
                () -> createModuleFinder(paths)
                        .findAll()
                        .stream()
                        .map(ModuleReference::descriptor)
                        .map(ModuleDescriptor::name)
                        .collect(Collectors.toSet()),
                ALL_DEFAULT,
                () -> getDefaultModules(paths, modules));

        Supplier<Collection<String>> phonyModule = null;
        for (var module : addModules) {
            phonyModule = phonyModules.get(module);
            if (phonyModule == null) {
                modules.add(module);
            }
        }

        if (phonyModule != null) {
            modules.addAll(phonyModule.get());
        }

        return modules;
    }

    private static String getPathList(List<Path> pathList) {
        return pathList.stream()
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator));
    }

    private static String getStringList(Set<String> strings) {
        return strings.stream().collect(Collectors.joining(","));
    }

    private final List<String> jlinkCmdLine;

    // The token for "all modules on the module path".
    private static final String ALL_MODULE_PATH = "ALL-MODULE-PATH";

    // The token for "all valid runtime modules".
    private static final String ALL_DEFAULT = "ALL-DEFAULT";

    private static class LazyLoad {

        static final ToolProvider JLINK_TOOL = ToolProvider.findFirst(
                "jlink").orElseThrow();
    };
}
