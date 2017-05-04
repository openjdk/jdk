/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.module.Configuration;
import java.lang.module.ResolvedModule;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import jdk.internal.loader.ClassLoaders;


/**
 * Supports the mapping of modules to class loaders. The set of modules mapped
 * to the boot and platform class loaders is generated at build time from
 * this source file.
 */
public final class ModuleLoaderMap {

    /**
     * Maps the system modules to the built-in class loaders.
     */
    public static final class Mapper implements Function<String, ClassLoader> {
        private final Map<String, ClassLoader> map;

        Mapper(Map<String, ClassLoader> map) {
            this.map = map; // defensive copy not needed
        }

        @Override
        public ClassLoader apply(String name) {
            return map.get(name);
        }
    }

    /**
     * Returns the names of the modules defined to the boot loader.
     */
    public static Set<String> bootModules() {
        // The list of boot modules generated at build time.
        String[] BOOT_MODULES = new String[] { "@@BOOT_MODULE_NAMES@@" };
        Set<String> bootModules = new HashSet<>(BOOT_MODULES.length);
        for (String mn : BOOT_MODULES) {
            bootModules.add(mn);
        }
        return bootModules;
    }

    /**
     * Returns the names of the modules defined to the platform loader.
     */
    public static Set<String> platformModules() {
        // The list of platform modules generated at build time.
        String[] PLATFORM_MODULES = new String[] { "@@PLATFORM_MODULE_NAMES@@" };
        Set<String> platformModules = new HashSet<>(PLATFORM_MODULES.length);
        for (String mn : PLATFORM_MODULES) {
            platformModules.add(mn);
        }
        return platformModules;
    }

    /**
     * Returns the function to map modules in the given configuration to the
     * built-in class loaders.
     */
    static Function<String, ClassLoader> mappingFunction(Configuration cf) {
        Set<String> bootModules = bootModules();
        Set<String> platformModules = platformModules();

        ClassLoader platformClassLoader = ClassLoaders.platformClassLoader();
        ClassLoader appClassLoader = ClassLoaders.appClassLoader();

        Map<String, ClassLoader> map = new HashMap<>();
        for (ResolvedModule resolvedModule : cf.modules()) {
            String mn = resolvedModule.name();
            if (!bootModules.contains(mn)) {
                if (platformModules.contains(mn)) {
                    map.put(mn, platformClassLoader);
                } else {
                    map.put(mn, appClassLoader);
                }
            }
        }
        return new Mapper(map);
    }
}
