/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import jdk.internal.misc.CDS;

/**
 * Used by ModuleBootstrap for archiving the configuration for the boot layer,
 * and the system module finder.
 */
class ArchivedModuleGraph {
    private static ArchivedModuleGraph archivedModuleGraph;

    private final boolean hasSplitPackages;
    private final boolean hasIncubatorModules;
    private final ModuleFinder finder;
    private final Configuration configuration;
    private final Function<String, ClassLoader> classLoaderFunction;
    private final String mainModule;
    private final Set<String> addModules;

    private ArchivedModuleGraph(boolean hasSplitPackages,
                                boolean hasIncubatorModules,
                                ModuleFinder finder,
                                Configuration configuration,
                                Function<String, ClassLoader> classLoaderFunction,
                                String mainModule,
                                Set<String> addModules) {
        this.hasSplitPackages = hasSplitPackages;
        this.hasIncubatorModules = hasIncubatorModules;
        this.finder = finder;
        this.configuration = configuration;
        this.classLoaderFunction = classLoaderFunction;
        this.mainModule = mainModule;
        this.addModules = addModules;
    }

    ModuleFinder finder() {
        return finder;
    }

    Configuration configuration() {
        return configuration;
    }

    Function<String, ClassLoader> classLoaderFunction() {
        return classLoaderFunction;
    }

    boolean hasSplitPackages() {
        return hasSplitPackages;
    }

    boolean hasIncubatorModules() {
        return hasIncubatorModules;
    }

    static boolean sameAddModules(Set<String> addModules) {
        if (archivedModuleGraph.addModules == null || addModules == null) {
            return false;
        }

        if (archivedModuleGraph.addModules.size() != addModules.size()) {
            return false;
        }

        return archivedModuleGraph.addModules.containsAll(addModules);
    }

    /**
     * Returns the ArchivedModuleGraph for the given initial module.
     */
    static ArchivedModuleGraph get(String mainModule, Set<String> addModules) {
        ArchivedModuleGraph graph = archivedModuleGraph;
        if ((graph != null) && Objects.equals(graph.mainModule, mainModule) && sameAddModules(addModules)) {
            return graph;
        } else {
            return null;
        }
    }

    /**
     * Archive the module graph for the given initial module.
     */
    static void archive(boolean hasSplitPackages,
                        boolean hasIncubatorModules,
                        ModuleFinder finder,
                        Configuration configuration,
                        Function<String, ClassLoader> classLoaderFunction,
                        String mainModule,
                        Set<String> addModules) {
        archivedModuleGraph = new ArchivedModuleGraph(hasSplitPackages,
                                                      hasIncubatorModules,
                                                      finder,
                                                      configuration,
                                                      classLoaderFunction,
                                                      mainModule,
                                                      addModules);
    }

    static {
        CDS.initializeFromArchive(ArchivedModuleGraph.class);
    }
}
