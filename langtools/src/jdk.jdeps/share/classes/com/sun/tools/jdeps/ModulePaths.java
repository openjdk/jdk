/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.jdeps;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.ProviderNotFoundException;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.module.ModuleDescriptor.Requires.Modifier.*;

public class ModulePaths {
    final ModuleFinder finder;
    final Map<String, Module> modules = new LinkedHashMap<>();

    public ModulePaths(String upgradeModulePath, String modulePath) {
        this(upgradeModulePath, modulePath, Collections.emptyList());
    }

    public ModulePaths(String upgradeModulePath, String modulePath, List<Path> jars) {
        ModuleFinder finder = ModuleFinder.ofSystem();
        if (upgradeModulePath != null) {
            finder = ModuleFinder.compose(createModulePathFinder(upgradeModulePath), finder);
        }
        if (jars.size() > 0) {
            finder = ModuleFinder.compose(finder, ModuleFinder.of(jars.toArray(new Path[0])));
        }
        if (modulePath != null) {
            finder = ModuleFinder.compose(finder, createModulePathFinder(modulePath));
        }
        this.finder = finder;

        // add modules from modulepaths
        finder.findAll().stream().forEach(mref ->
            modules.computeIfAbsent(mref.descriptor().name(), mn -> toModule(mn, mref))
        );
    }

    /**
     * Returns the list of Modules that can be found in the specified
     * module paths.
     */
    Map<String, Module> getModules() {
        return modules;
    }

    Set<Module> dependences(String... roots) {
        Configuration cf = configuration(roots);
        return cf.modules().stream()
                .map(ResolvedModule::name)
                .map(modules::get)
                .collect(Collectors.toSet());
    }

    Configuration configuration(String... roots) {
        return Configuration.empty().resolveRequires(finder, ModuleFinder.empty(), Set.of(roots));
    }

    private static ModuleFinder createModulePathFinder(String mpaths) {
        if (mpaths == null) {
            return null;
        } else {
            String[] dirs = mpaths.split(File.pathSeparator);
            Path[] paths = new Path[dirs.length];
            int i = 0;
            for (String dir : dirs) {
                paths[i++] = Paths.get(dir);
            }
            return ModuleFinder.of(paths);
        }
    }

    private static Module toModule(String mn, ModuleReference mref) {
        return SystemModulePath.find(mn)
                               .orElse(toModule(new Module.Builder(mn), mref));
    }

    private static Module toModule(Module.Builder builder, ModuleReference mref) {
        ModuleDescriptor md = mref.descriptor();
        builder.descriptor(md);
        for (ModuleDescriptor.Requires req : md.requires()) {
            builder.require(req.name(), req.modifiers().contains(PUBLIC));
        }
        for (ModuleDescriptor.Exports exp : md.exports()) {
            builder.export(exp.source(), exp.targets());
        }
        builder.packages(md.packages());

        try {
            URI location = mref.location()
                               .orElseThrow(FileNotFoundException::new);
            builder.location(location);
            builder.classes(getClassReader(location, md.name()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return builder.build();
    }

    static class SystemModulePath {
        final static Module JAVA_BASE;

        private final static FileSystem fs;
        private final static Path root;
        private final static Map<String, Module> installed = new HashMap<>();
        static {
            if (isJrtAvailable()) {
                // jrt file system
                fs = FileSystems.getFileSystem(URI.create("jrt:/"));
                root = fs.getPath("/modules");
            } else {
                // exploded image
                String javahome = System.getProperty("java.home");
                fs = FileSystems.getDefault();
                root = Paths.get(javahome, "modules");
            }

            ModuleFinder.ofSystem().findAll().stream()
                 .forEach(mref ->
                     installed.computeIfAbsent(mref.descriptor().name(),
                                               mn -> toModule(new Module.Builder(mn, true), mref))
                 );
            JAVA_BASE = installed.get("java.base");

            Profile.init(installed);
        }

        private static boolean isJrtAvailable() {
            try {
                FileSystems.getFileSystem(URI.create("jrt:/"));
                return true;
            } catch (ProviderNotFoundException | FileSystemNotFoundException e) {
                return false;
            }
        }

        public static Optional<Module> find(String mn) {
            return installed.containsKey(mn) ? Optional.of(installed.get(mn))
                                             : Optional.empty();
        }

        public static boolean contains(Module m) {
            return installed.containsValue(m);
        }

        public static ClassFileReader getClassReader(String modulename) throws IOException {
            Path mp = root.resolve(modulename);
            if (Files.exists(mp) && Files.isDirectory(mp)) {
                return ClassFileReader.newInstance(fs, mp);
            } else {
                throw new FileNotFoundException(mp.toString());
            }
        }
    }

    /**
     * Returns a ModuleClassReader that only reads classes for the given modulename.
     */
    public static ClassFileReader getClassReader(URI location, String modulename)
            throws IOException {
        if (location.getScheme().equals("jrt")) {
            return SystemModulePath.getClassReader(modulename);
        } else {
            Path path = Paths.get(location);
            return ClassFileReader.newInstance(path);
        }
    }
}
