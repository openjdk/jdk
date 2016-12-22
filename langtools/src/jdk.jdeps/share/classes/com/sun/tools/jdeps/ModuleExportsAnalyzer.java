/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.module.ModuleDescriptor;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.sun.tools.jdeps.Analyzer.NOT_FOUND;

/**
 * Analyze module dependences and any reference to JDK internal APIs.
 * It can apply transition reduction on the resulting module graph.
 *
 * The result prints one line per module it depends on
 * one line per JDK internal API package it references:
 *     $MODULE[/$PACKAGE]
 *
 */
public class ModuleExportsAnalyzer extends DepsAnalyzer {
    // source archive to its dependences and JDK internal APIs it references
    private final Map<Archive, Map<Archive,Set<String>>> deps = new HashMap<>();
    private final boolean reduced;
    private final PrintWriter writer;
    public ModuleExportsAnalyzer(JdepsConfiguration config,
                                 JdepsFilter filter,
                                 boolean reduced,
                                 PrintWriter writer) {
        super(config, filter, null,
              Analyzer.Type.PACKAGE,
              false /* all classes */);
        this.reduced = reduced;
        this.writer = writer;
    }

    @Override
    public boolean run() throws IOException {
        // analyze dependences
        boolean rc = super.run();

        // A visitor to record the module-level dependences as well as
        // use of JDK internal APIs
        Analyzer.Visitor visitor = (origin, originArchive, target, targetArchive) -> {
            Set<String> jdkInternals =
                deps.computeIfAbsent(originArchive, _k -> new HashMap<>())
                    .computeIfAbsent(targetArchive, _k -> new HashSet<>());

            Module module = targetArchive.getModule();
            if (originArchive.getModule() != module &&
                    module.isJDK() && !module.isExported(target)) {
                // use of JDK internal APIs
                jdkInternals.add(target);
            }
        };

        // visit the dependences
        archives.stream()
            .filter(analyzer::hasDependences)
            .sorted(Comparator.comparing(Archive::getName))
            .forEach(archive -> analyzer.visitDependences(archive, visitor));


        // print the dependences on named modules
        printDependences();

        // print the dependences on unnamed module
        deps.values().stream()
            .flatMap(map -> map.keySet().stream())
            .filter(archive -> !archive.getModule().isNamed())
            .map(archive -> archive != NOT_FOUND
                                ? "unnamed module: " + archive.getPathName()
                                : archive.getPathName())
            .distinct()
            .sorted()
            .forEach(archive -> writer.format("   %s%n", archive));

        return rc;
    }

    private void printDependences() {
        // find use of JDK internals
        Map<Module, Set<String>> jdkinternals = new HashMap<>();
        deps.keySet().stream()
            .filter(source -> !source.getModule().isNamed())
            .map(deps::get)
            .flatMap(map -> map.entrySet().stream())
            .filter(e -> e.getValue().size() > 0)
            .forEach(e -> jdkinternals.computeIfAbsent(e.getKey().getModule(),
                                                       _k -> new HashSet<>())
                                      .addAll(e.getValue()));


        // build module graph
        ModuleGraphBuilder builder = new ModuleGraphBuilder(configuration);
        Module root = new RootModule("root");
        builder.addModule(root);
        // find named module dependences
        deps.keySet().stream()
            .filter(source -> !source.getModule().isNamed())
            .map(deps::get)
            .flatMap(map -> map.keySet().stream())
            .filter(m -> m.getModule().isNamed())
            .map(Archive::getModule)
            .forEach(m -> builder.addEdge(root, m));

        // module dependences
        Set<Module> modules = builder.build().adjacentNodes(root);

        // if reduced is set, apply transition reduction
        Set<Module> reducedSet;
        if (reduced) {
            Set<Module> nodes = builder.reduced().adjacentNodes(root);
            if (nodes.size() == 1) {
                // java.base only
                reducedSet = nodes;
            } else {
                // java.base is mandated and can be excluded from the reduced graph
                reducedSet = nodes.stream()
                    .filter(m -> !"java.base".equals(m.name()) ||
                                    jdkinternals.containsKey("java.base"))
                    .collect(Collectors.toSet());
            }
        } else {
            reducedSet = modules;
        }

        modules.stream()
               .sorted(Comparator.comparing(Module::name))
               .forEach(m -> {
                    if (jdkinternals.containsKey(m)) {
                        jdkinternals.get(m).stream()
                            .sorted()
                            .forEach(pn -> writer.format("   %s/%s%n", m, pn));
                    } else if (reducedSet.contains(m)){
                        // if the transition reduction is applied, show the reduced graph
                        writer.format("   %s%n", m);
                    }
            });
    }


    private class RootModule extends Module {
        final ModuleDescriptor descriptor;
        RootModule(String name) {
            super(name);

            ModuleDescriptor.Builder builder = ModuleDescriptor.module(name);
            this.descriptor = builder.build();
        }

        @Override
        public ModuleDescriptor descriptor() {
            return descriptor;
        }
    }

}
