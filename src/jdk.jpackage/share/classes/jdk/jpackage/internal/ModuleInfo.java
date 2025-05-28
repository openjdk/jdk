/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.Reader;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import jdk.internal.util.OperatingSystem;

record ModuleInfo(String name, Optional<String> version, Optional<String> mainClass, Optional<URI> location) {

    Optional<Path> fileLocation() {
        return location.filter(loc -> {
            return loc.getScheme().equals("file");
        }).map(Path::of);
    }

    static ModuleInfo fromModuleReference(ModuleReference mr) {
        final var md = mr.descriptor();
        return new ModuleInfo(md.name(), md.version().map(ModuleDescriptor.Version::toString).or(md::rawVersion), md.mainClass(), mr.location());
    }

    static Optional<ModuleInfo> fromCookedRuntime(String moduleName, Path cookedRuntime) {
        Objects.requireNonNull(moduleName);
        Objects.requireNonNull(cookedRuntime);

        // We can't extract info about version and main class of a module
        // linked in external runtime without running ModuleFinder in that
        // runtime. But this is too much work as the runtime might have been
        // coocked without native launchers. So just make sure the module
        // is linked in the runtime by simply analysing the data
        // of `release` file.

        final Path releaseFile;
        if (!OperatingSystem.isMacOS()) {
            releaseFile = cookedRuntime.resolve("release");
        } else {
            // On Mac `cookedRuntime` can be runtime root or runtime home.
            Path runtimeHome = cookedRuntime.resolve("Contents/Home");
            if (!Files.isDirectory(runtimeHome)) {
                runtimeHome = cookedRuntime;
            }
            releaseFile = runtimeHome.resolve("release");
        }

        try (Reader reader = Files.newBufferedReader(releaseFile)) {
            Properties props = new Properties();
            props.load(reader);
            String moduleList = props.getProperty("MODULES");
            if (moduleList == null) {
                return Optional.empty();
            }

            if ((moduleList.startsWith("\"") && moduleList.endsWith("\""))
                    || (moduleList.startsWith("\'") && moduleList.endsWith(
                    "\'"))) {
                moduleList = moduleList.substring(1, moduleList.length() - 1);
            }

            if (!List.of(moduleList.split("\\s+")).contains(moduleName)) {
                return Optional.empty();
            }
        } catch (IOException|IllegalArgumentException ex) {
            Log.verbose(ex);
            return Optional.empty();
        }

        return Optional.of(new ModuleInfo(moduleName, Optional.empty(), Optional.empty(), Optional.empty()));
    }
}
