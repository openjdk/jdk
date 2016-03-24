/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import static com.sun.tools.jdeps.Analyzer.Type.CLASS;
import static com.sun.tools.jdeps.Analyzer.NOT_FOUND;
import static com.sun.tools.jdeps.Module.trace;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ModuleInfoBuilder {
    final ModulePaths modulePaths;
    final DependencyFinder dependencyFinder;
    final JdepsFilter filter;
    final Analyzer analyzer;
    final Map<Module, Module> strictModules = new HashMap<>();
    ModuleInfoBuilder(ModulePaths modulePaths, DependencyFinder finder) {
        this.modulePaths = modulePaths;
        this.dependencyFinder = finder;
        this.filter = new JdepsFilter.Builder().filter(true, true).build();
        this.analyzer = new Analyzer(CLASS, filter);
    }

    private Stream<Module> automaticModules() {
        return modulePaths.getModules().values()
                .stream()
                .filter(Module::isAutomatic);
    }

    /**
     * Compute 'requires public' dependences by analyzing API dependencies
     */
    Map<Module, Set<Module>> computeRequiresPublic() throws IOException {
        dependencyFinder.findDependencies(filter, true /* api only */, 1);
        Analyzer pass1 = new Analyzer(Analyzer.Type.CLASS, filter);

        pass1.run(dependencyFinder.archives());

        return automaticModules().collect(Collectors.toMap(Function.identity(),
                source -> pass1.requires(source)
                               .map(Archive::getModule)
                               .collect(Collectors.toSet())));
    }

    boolean run(Analyzer.Type verbose, boolean quiet) throws IOException {
        // add all automatic modules to the root set
        automaticModules().forEach(dependencyFinder::addRoot);

        // pass 1: find API dependencies
        Map<Module, Set<Module>> requiresPublic = computeRequiresPublic();

        // pass 2: analyze all class dependences
        dependencyFinder.findDependencies(filter, false /* all classes */, 1);
        analyzer.run(dependencyFinder.archives());

        // computes requires and requires public
        automaticModules().forEach(m -> {
            Map<String, Boolean> requires;
            if (requiresPublic.containsKey(m)) {
                requires = requiresPublic.get(m)
                        .stream()
                        .collect(Collectors.toMap(Archive::getName, (v) -> Boolean.TRUE));
            } else {
                requires = new HashMap<>();
            }
            analyzer.requires(m)
                    .forEach(d -> requires.putIfAbsent(d.getName(), Boolean.FALSE));

            trace("strict module %s requires %s%n", m.name(), requires);
            strictModules.put(m, m.toStrictModule(requires));
        });

        // find any missing dependences
        Optional<Module> missingDeps = automaticModules()
                .filter(this::missingDep)
                .findAny();
        if (missingDeps.isPresent()) {
            automaticModules()
                    .filter(this::missingDep)
                    .forEach(m -> {
                        System.err.format("Missing dependencies from %s%n", m.name());
                        analyzer.visitDependences(m,
                                new Analyzer.Visitor() {
                                    @Override
                                    public void visitDependence(String origin, Archive originArchive,
                                                                String target, Archive targetArchive) {
                                        if (targetArchive == NOT_FOUND)
                                            System.err.format("   %-50s -> %-50s %s%n",
                                                    origin, target, targetArchive.getName());
                                    }
                                }, verbose);
                        System.err.println();
                    });

            System.err.println("ERROR: missing dependencies (check \"requires NOT_FOUND;\")");
        }
        return missingDeps.isPresent() ? false : true;
    }

    private boolean missingDep(Archive m) {
        return analyzer.requires(m).filter(a -> a.equals(NOT_FOUND))
                       .findAny().isPresent();
    }

    void build(Path dir) throws IOException {
        ModuleInfoWriter writer = new ModuleInfoWriter(dir);
        writer.generateOutput(strictModules.values(), analyzer);
    }

    private class ModuleInfoWriter {
        private final Path outputDir;
        ModuleInfoWriter(Path dir) {
            this.outputDir = dir;
        }

        void generateOutput(Iterable<Module> modules, Analyzer analyzer) throws IOException {
            // generate module-info.java file for each archive
            for (Module m : modules) {
                if (m.packages().contains("")) {
                    System.err.format("ERROR: %s contains unnamed package.  " +
                                      "module-info.java not generated%n", m.getPathName());
                    continue;
                }

                String mn = m.getName();
                Path srcFile = outputDir.resolve(mn).resolve("module-info.java");
                Files.createDirectories(srcFile.getParent());
                System.out.println("writing to " + srcFile);
                try (PrintWriter pw = new PrintWriter(Files.newOutputStream(srcFile))) {
                    printModuleInfo(pw, m);
                }
            }
        }

        private void printModuleInfo(PrintWriter writer, Module m) {
            writer.format("module %s {%n", m.name());

            Map<String, Module> modules = modulePaths.getModules();
            Map<String, Boolean> requires = m.requires();
            // first print the JDK modules
            requires.keySet().stream()
                    .filter(mn -> !mn.equals("java.base"))   // implicit requires
                    .filter(mn -> modules.containsKey(mn) && modules.get(mn).isJDK())
                    .sorted()
                    .forEach(mn -> {
                        String modifier = requires.get(mn) ? "public " : "";
                        writer.format("    requires %s%s;%n", modifier, mn);
                    });

            // print requires non-JDK modules
            requires.keySet().stream()
                    .filter(mn -> !modules.containsKey(mn) || !modules.get(mn).isJDK())
                    .sorted()
                    .forEach(mn -> {
                        String modifier = requires.get(mn) ? "public " : "";
                        writer.format("    requires %s%s;%n", modifier, mn);
                    });

            m.packages().stream()
                    .sorted()
                    .forEach(pn -> writer.format("    exports %s;%n", pn));

            m.provides().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> {
                        String service = e.getKey();
                        e.getValue().stream()
                                .sorted()
                                .forEach(impl -> writer.format("    provides %s with %s;%n", service, impl));
                    });

            writer.println("}");
        }
    }
}
