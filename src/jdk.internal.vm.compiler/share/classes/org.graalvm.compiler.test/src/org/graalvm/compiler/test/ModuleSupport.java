/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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


package org.graalvm.compiler.test;

import java.io.IOException;
import java.lang.module.ModuleDescriptor.Requires;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.graalvm.compiler.debug.DebugOptions;

import jdk.internal.module.Modules;

public class ModuleSupport {

    public static void exportPackageTo(Class<?> moduleMember, String packageName, Class<?> requestor) {
        Module moduleToExport = moduleMember.getModule();
        Module requestorModule = requestor.getModule();
        if (moduleToExport != requestorModule) {
            Modules.addExports(moduleToExport, packageName, requestorModule);
        }
    }

    public static void exportAllPackagesTo(Class<?> moduleMember, Class<?> requestor) {
        Module moduleToExport = moduleMember.getModule();
        Module requestorModule = requestor.getModule();
        if (moduleToExport != requestorModule) {
            for (String packageName : moduleToExport.getPackages()) {
                Modules.addExports(moduleToExport, packageName, requestorModule);
            }
        }
    }

    public static void exportAllPackagesTo(Class<?> moduleMember, ClassLoader cl) {
        Module moduleToExport = moduleMember.getModule();
        Module unnamedModule = cl.getUnnamedModule();
        for (String packageName : moduleToExport.getPackages()) {
            Modules.addExports(moduleToExport, packageName, unnamedModule);
        }
    }

    @SuppressWarnings("unused")
    public static void exportAndOpenAllPackagesToUnnamed(String name) {
        Module module = ModuleLayer.boot().findModule(name).orElseThrow();
        Set<String> packages = module.getPackages();
        for (String pkg : packages) {
            Modules.addExportsToAllUnnamed(module, pkg);
            Modules.addOpensToAllUnnamed(module, pkg);
        }
    }

    public static List<String> getJRTGraalClassNames() throws IOException {
        List<String> classNames = new ArrayList<>();
        FileSystem fs = FileSystems.newFileSystem(URI.create("jrt:/"), Collections.emptyMap());
        Module graalModule = DebugOptions.class.getModule();
        Set<String> graalModuleSet = new HashSet<>();
        graalModuleSet.add(graalModule.getName());
        for (Module module : graalModule.getLayer().modules()) {
            if (requires(module, graalModule)) {
                graalModuleSet.add(module.getName());
            }
        }

        Path top = fs.getPath("/modules/");
        Files.find(top, Integer.MAX_VALUE,
                        (path, attrs) -> attrs.isRegularFile()).forEach(p -> {
                            int nameCount = p.getNameCount();
                            if (nameCount > 2) {
                                String base = p.getName(nameCount - 1).toString();
                                if (base.endsWith(".class") && !base.equals("module-info.class")) {
                                    String module = p.getName(1).toString();
                                    if (graalModuleSet.contains(module)) {
                                        // Strip module prefix and convert to dotted
                                        // form
                                        String className = p.subpath(2, nameCount).toString().replace('/', '.');
                                        // Strip ".class" suffix
                                        className = className.replace('/', '.').substring(0, className.length() - ".class".length());
                                        classNames.add(className);
                                    }
                                }
                            }
                        });
        return classNames;
    }

    private static boolean requires(Module module, Module graalModule) {
        ModuleLayer graalLayer = graalModule.getLayer();
        for (Requires r : module.getDescriptor().requires()) {
            if (r.name().equals(graalModule.getName())) {
                return true;
            }
            Module dep = graalLayer.findModule(r.name()).get();
            if (requires(dep, graalModule)) {
                return true;
            }
        }
        return false;
    }
}
