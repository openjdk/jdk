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
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final boolean showJdkInternals;
    private final boolean reduced;
    private final PrintWriter writer;
    private final String separator;
    public ModuleExportsAnalyzer(JdepsConfiguration config,
                                 JdepsFilter filter,
                                 boolean showJdkInternals,
                                 boolean reduced,
                                 PrintWriter writer,
                                 String separator) {
        super(config, filter, null,
              Analyzer.Type.PACKAGE,
              false /* all classes */);
        this.showJdkInternals = showJdkInternals;
        this.reduced = reduced;
        this.writer = writer;
        this.separator = separator;
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
            if (showJdkInternals && originArchive.getModule() != module &&
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

        Set<Module> modules = modules();
        if (showJdkInternals) {
            // print modules and JDK internal API dependences
            printDependences(modules);
        } else {
            // print module dependences
            writer.println(modules.stream().map(Module::name).sorted()
                                  .collect(Collectors.joining(separator)));
        }
        return rc;
    }

    private Set<Module> modules() {
        // build module graph
        ModuleGraphBuilder builder = new ModuleGraphBuilder(configuration);
        Module root = new RootModule("root");
        builder.addModule(root);
        // find named module dependences
        dependenceStream()
            .flatMap(map -> map.keySet().stream())
            .filter(m -> m.getModule().isNamed()
                && !configuration.rootModules().contains(m))
            .map(Archive::getModule)
            .forEach(m -> builder.addEdge(root, m));

        // build module dependence graph
        // if reduced is set, apply transition reduction
        Graph<Module> g = reduced ? builder.reduced() : builder.build();
        return g.adjacentNodes(root);
    }

    private void printDependences(Set<Module> modules) {
        // find use of JDK internals
        Map<Module, Set<String>> jdkinternals = new HashMap<>();
        dependenceStream()
            .flatMap(map -> map.entrySet().stream())
            .filter(e -> e.getValue().size() > 0)
            .forEach(e -> jdkinternals.computeIfAbsent(e.getKey().getModule(),
                                                       _k -> new TreeSet<>())
                                      .addAll(e.getValue()));

        // print modules and JDK internal API dependences
        Stream.concat(modules.stream(), jdkinternals.keySet().stream())
              .sorted(Comparator.comparing(Module::name))
              .distinct()
              .forEach(m -> {
                  if (jdkinternals.containsKey(m)) {
                      jdkinternals.get(m).stream()
                          .forEach(pn -> writer.format("   %s/%s%s", m, pn, separator));
                  } else {
                      writer.format("   %s%s", m, separator);
                  }
              });
    }

    /*
     * Returns a stream of dependence map from an Archive to the set of JDK
     * internal APIs being used.
     */
    private Stream<Map<Archive, Set<String>>> dependenceStream() {
        return deps.keySet().stream()
                   .filter(source -> !source.getModule().isNamed()
                            || configuration.rootModules().contains(source))
                   .map(deps::get);
    }

    private class RootModule extends Module {
        final ModuleDescriptor descriptor;
        RootModule(String name) {
            super(name);

            ModuleDescriptor.Builder builder = ModuleDescriptor.newModule(name);
            this.descriptor = builder.build();
        }

        @Override
        public ModuleDescriptor descriptor() {
            return descriptor;
        }
    }

}
