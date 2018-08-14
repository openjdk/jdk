/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.module.ModuleFinder;
import java.util.Objects;
import jdk.internal.misc.VM;

/**
 * Used by ModuleBootstrap to obtain the archived system modules and finder.
 */
final class ArchivedModuleGraph {
    private static String archivedMainModule;
    private static SystemModules archivedSystemModules;
    private static ModuleFinder archivedModuleFinder;
    private static Configuration archivedConfiguration;

    private final SystemModules systemModules;
    private final ModuleFinder finder;
    private final Configuration configuration;

    private ArchivedModuleGraph(SystemModules modules,
                                ModuleFinder finder,
                                Configuration configuration) {
        this.systemModules = modules;
        this.finder = finder;
        this.configuration = configuration;
    }

    SystemModules systemModules() {
        return systemModules;
    }

    ModuleFinder finder() {
        return finder;
    }

    Configuration configuration() {
        return configuration;
    }

    // A factory method that ModuleBootstrap can use to obtain the
    // ArchivedModuleGraph.
    static ArchivedModuleGraph get(String mainModule) {
        if (Objects.equals(mainModule, archivedMainModule)
                && archivedSystemModules != null
                && archivedModuleFinder != null
                && archivedConfiguration != null) {
            return new ArchivedModuleGraph(archivedSystemModules,
                                           archivedModuleFinder,
                                           archivedConfiguration);
        } else {
            return null;
        }
    }

    // Used at CDS dump time
    static void archive(String mainModule,
                        SystemModules systemModules,
                        ModuleFinder finder,
                        Configuration configuration) {
        if (archivedMainModule != null)
            throw new UnsupportedOperationException();
        archivedMainModule = mainModule;
        archivedSystemModules = systemModules;
        archivedModuleFinder = finder;
        archivedConfiguration = configuration;
    }

    static {
        VM.initializeFromArchive(ArchivedModuleGraph.class);
    }
}
