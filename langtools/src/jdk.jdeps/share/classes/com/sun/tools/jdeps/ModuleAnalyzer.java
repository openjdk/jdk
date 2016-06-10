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

import static com.sun.tools.jdeps.Graph.*;
import static com.sun.tools.jdeps.JdepsFilter.DEFAULT_FILTER;
import static com.sun.tools.jdeps.Module.*;
import static java.lang.module.ModuleDescriptor.Requires.Modifier.*;
import static java.util.stream.Collectors.*;

import com.sun.tools.classfile.Dependency;
import com.sun.tools.jdeps.JdepsTask.BadArgs;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.module.ModuleDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Analyze module dependences and compare with module descriptor.
 * Also identify any qualified exports not used by the target module.
 */
public class ModuleAnalyzer {
    private static final String JAVA_BASE = "java.base";

    private final JdepsConfiguration configuration;
    private final PrintWriter log;

    private final DependencyFinder dependencyFinder;
    private final Map<Module, ModuleDeps> modules;

    public ModuleAnalyzer(JdepsConfiguration config,
                          PrintWriter log) {
        this(config, log, Collections.emptySet());
    }
    public ModuleAnalyzer(JdepsConfiguration config,
                          PrintWriter log,
                          Set<String> names) {

        if (!config.initialArchives().isEmpty()) {
            String list = config.initialArchives().stream()
                .map(Archive::getPathName).collect(joining(" "));
            throw new JdepsTask.UncheckedBadArgs(new BadArgs("err.invalid.module.option",
                list, "-check"));
        }

        this.configuration = config;
        this.log = log;

        this.dependencyFinder = new DependencyFinder(config, DEFAULT_FILTER);
        if (names.isEmpty()) {
            this.modules = configuration.rootModules().stream()
                .collect(toMap(Function.identity(), ModuleDeps::new));
        } else {
            this.modules = names.stream()
                .map(configuration::findModule)
                .flatMap(Optional::stream)
                .collect(toMap(Function.identity(), ModuleDeps::new));
        }
    }

    public boolean run() throws IOException {
        try {
            // compute "requires public" dependences
            modules.values().forEach(ModuleDeps::computeRequiresPublic);

            modules.values().forEach(md -> {
                // compute "requires" dependences
                md.computeRequires();
                // apply transitive reduction and reports recommended requires.
                md.analyzeDeps();
            });
        } finally {
            dependencyFinder.shutdown();
        }
        return true;
    }

    class ModuleDeps {
        final Module root;
        Set<Module> requiresPublic;
        Set<Module> requires;
        Map<String, Set<String>> unusedQualifiedExports;

        ModuleDeps(Module root) {
            this.root = root;
        }

        /**
         * Compute 'requires public' dependences by analyzing API dependencies
         */
        private void computeRequiresPublic() {
            // record requires public
            this.requiresPublic = computeRequires(true)
                .filter(m -> !m.name().equals(JAVA_BASE))
                .collect(toSet());

            trace("requires public: %s%n", requiresPublic);
        }

        private void computeRequires() {
            this.requires = computeRequires(false).collect(toSet());
            trace("requires: %s%n", requires);
        }

        private Stream<Module> computeRequires(boolean apionly) {
            // analyze all classes

            if (apionly) {
                dependencyFinder.parseExportedAPIs(Stream.of(root));
            } else {
                dependencyFinder.parse(Stream.of(root));
            }

            // find the modules of all the dependencies found
            return dependencyFinder.getDependences(root)
                        .map(Archive::getModule);
        }

        ModuleDescriptor descriptor() {
            return descriptor(requiresPublic, requires);
        }

        private ModuleDescriptor descriptor(Set<Module> requiresPublic,
                                            Set<Module> requires) {

            ModuleDescriptor.Builder builder = new ModuleDescriptor.Builder(root.name());

            if (!root.name().equals(JAVA_BASE))
                builder.requires(MANDATED, JAVA_BASE);

            requiresPublic.stream()
                .filter(m -> !m.name().equals(JAVA_BASE))
                .map(Module::name)
                .forEach(mn -> builder.requires(PUBLIC, mn));

            requires.stream()
                .filter(m -> !requiresPublic.contains(m))
                .filter(m -> !m.name().equals(JAVA_BASE))
                .map(Module::name)
                .forEach(mn -> builder.requires(mn));

            return builder.build();
        }

        ModuleDescriptor reduced() {
            Graph.Builder<Module> bd = new Graph.Builder<>();
            requiresPublic.stream()
                .forEach(m -> {
                    bd.addNode(m);
                    bd.addEdge(root, m);
                });

            // requires public graph
            Graph<Module> rbg = bd.build().reduce();

            // transitive reduction
            Graph<Module> newGraph = buildGraph(requires).reduce(rbg);
            if (DEBUG) {
                System.err.println("after transitive reduction: ");
                newGraph.printGraph(log);
            }

            return descriptor(requiresPublic, newGraph.adjacentNodes(root));
        }


        /**
         * Apply transitive reduction on the resulting graph and reports
         * recommended requires.
         */
        private void analyzeDeps() {
            Graph.Builder<Module> builder = new Graph.Builder<>();
            requiresPublic.stream()
                .forEach(m -> {
                    builder.addNode(m);
                    builder.addEdge(root, m);
                });

            // requires public graph
            Graph<Module> rbg = buildGraph(requiresPublic).reduce();

            // transitive reduction
            Graph<Module> newGraph = buildGraph(requires).reduce(builder.build().reduce());
            if (DEBUG) {
                System.err.println("after transitive reduction: ");
                newGraph.printGraph(log);
            }

            printModuleDescriptor(log, root);

            ModuleDescriptor analyzedDescriptor = descriptor();
            if (!matches(root.descriptor(), analyzedDescriptor)) {
                log.format("  [Suggested module descriptor for %s]%n", root.name());
                analyzedDescriptor.requires()
                    .stream()
                    .sorted(Comparator.comparing(ModuleDescriptor.Requires::name))
                    .forEach(req -> log.format("    requires %s;%n", req));
            }

            ModuleDescriptor reduced = reduced();
            if (!matches(root.descriptor(), reduced)) {
                log.format("  [Transitive reduced graph for %s]%n", root.name());
                reduced.requires()
                    .stream()
                    .sorted(Comparator.comparing(ModuleDescriptor.Requires::name))
                    .forEach(req -> log.format("    requires %s;%n", req));
            }

            checkQualifiedExports();
            log.println();
        }

        private void checkQualifiedExports() {
            // detect any qualified exports not used by the target module
            unusedQualifiedExports = unusedQualifiedExports();
            if (!unusedQualifiedExports.isEmpty())
                log.format("  [Unused qualified exports in %s]%n", root.name());

            unusedQualifiedExports.keySet().stream()
                .sorted()
                .forEach(pn -> log.format("    exports %s to %s%n", pn,
                    unusedQualifiedExports.get(pn).stream()
                        .sorted()
                        .collect(joining(","))));
        }

        private void printModuleDescriptor(PrintWriter out, Module module) {
            ModuleDescriptor descriptor = module.descriptor();
            out.format("%s (%s)%n", descriptor.name(), module.location());

            if (descriptor.name().equals(JAVA_BASE))
                return;

            out.println("  [Module descriptor]");
            descriptor.requires()
                .stream()
                .sorted(Comparator.comparing(ModuleDescriptor.Requires::name))
                .forEach(req -> out.format("    requires %s;%n", req));
        }


        /**
         * Returns a graph of modules required by the specified module.
         *
         * Requires public edges of the dependences are added to the graph.
         */
        private Graph<Module> buildGraph(Set<Module> deps) {
            Graph.Builder<Module> builder = new Graph.Builder<>();
            builder.addNode(root);
            Set<Module> visited = new HashSet<>();
            visited.add(root);
            Deque<Module> deque = new LinkedList<>();
            deps.stream()
                .forEach(m -> {
                    deque.add(m);
                    builder.addEdge(root, m);
                });

            // read requires public from ModuleDescription
            Module source;
            while ((source = deque.poll()) != null) {
                if (visited.contains(source))
                    continue;

                visited.add(source);
                builder.addNode(source);
                Module from = source;
                source.descriptor().requires().stream()
                    .filter(req -> req.modifiers().contains(PUBLIC))
                    .map(ModuleDescriptor.Requires::name)
                    .map(configuration::findModule)
                    .flatMap(Optional::stream)
                    .forEach(m -> {
                        deque.add(m);
                        builder.addEdge(from, m);
                    });
            }
            return builder.build();
        }

        /**
         * Detects any qualified exports not used by the target module.
         */
        private Map<String, Set<String>> unusedQualifiedExports() {
            Map<String, Set<String>> unused = new HashMap<>();

            // build the qualified exports map
            Map<String, Set<String>> qualifiedExports =
                root.exports().entrySet().stream()
                    .filter(e -> !e.getValue().isEmpty())
                    .map(Map.Entry::getKey)
                    .collect(toMap(Function.identity(), _k -> new HashSet<>()));

            Set<Module> mods = new HashSet<>();
            root.exports().values()
                .stream()
                .flatMap(Set::stream)
                .forEach(target -> configuration.findModule(target)
                    .ifPresentOrElse(mods::add,
                        () -> log.format("Warning: %s not found%n", target))
                );

            // parse all target modules
            dependencyFinder.parse(mods.stream());

            // adds to the qualified exports map if a module references it
            mods.stream().forEach(m ->
                m.getDependencies()
                    .map(Dependency.Location::getPackageName)
                    .filter(qualifiedExports::containsKey)
                    .forEach(pn -> qualifiedExports.get(pn).add(m.name())));

            // compare with the exports from ModuleDescriptor
            Set<String> staleQualifiedExports =
                qualifiedExports.keySet().stream()
                    .filter(pn -> !qualifiedExports.get(pn).equals(root.exports().get(pn)))
                    .collect(toSet());

            if (!staleQualifiedExports.isEmpty()) {
                for (String pn : staleQualifiedExports) {
                    Set<String> targets = new HashSet<>(root.exports().get(pn));
                    targets.removeAll(qualifiedExports.get(pn));
                    unused.put(pn, targets);
                }
            }
            return unused;
        }
    }

    private boolean matches(ModuleDescriptor md, ModuleDescriptor other) {
        // build requires public from ModuleDescriptor
        Set<ModuleDescriptor.Requires> reqPublic = md.requires().stream()
            .filter(req -> req.modifiers().contains(PUBLIC))
            .collect(toSet());
        Set<ModuleDescriptor.Requires> otherReqPublic = other.requires().stream()
            .filter(req -> req.modifiers().contains(PUBLIC))
            .collect(toSet());

        if (!reqPublic.equals(otherReqPublic)) {
            trace("mismatch requires public: %s%n", reqPublic);
            return false;
        }

        Set<ModuleDescriptor.Requires> unused = md.requires().stream()
            .filter(req -> !other.requires().contains(req))
            .collect(Collectors.toSet());

        if (!unused.isEmpty()) {
            trace("mismatch requires: %s%n", unused);
            return false;
        }
        return true;
    }

    /**
     * Generate dotfile from module descriptor
     *
     * @param dir output directory
     */
    public boolean genDotFiles(Path dir) throws IOException {
        Files.createDirectories(dir);
        for (Module m : modules.keySet()) {
            genDotFile(dir, m.name());
        }
        return true;
    }


    private void genDotFile(Path dir, String name) throws IOException {
        try (OutputStream os = Files.newOutputStream(dir.resolve(name + ".dot"));
             PrintWriter out = new PrintWriter(os)) {
            Set<Module> modules = configuration.resolve(Set.of(name))
                .collect(Collectors.toSet());

            // transitive reduction
            Graph<String> graph = gengraph(modules);

            out.format("digraph \"%s\" {%n", name);
            DotGraph.printAttributes(out);
            DotGraph.printNodes(out, graph);

            modules.stream()
                .map(Module::descriptor)
                .sorted(Comparator.comparing(ModuleDescriptor::name))
                .forEach(md -> {
                    String mn = md.name();
                    Set<String> requiresPublic = md.requires().stream()
                        .filter(d -> d.modifiers().contains(PUBLIC))
                        .map(d -> d.name())
                        .collect(toSet());

                    DotGraph.printEdges(out, graph, mn, requiresPublic);
                });

            out.println("}");
        }
    }

    /**
     * Returns a Graph of the given Configuration after transitive reduction.
     *
     * Transitive reduction of requires public edge and requires edge have
     * to be applied separately to prevent the requires public edges
     * (e.g. U -> V) from being reduced by a path (U -> X -> Y -> V)
     * in which  V would not be re-exported from U.
     */
    private Graph<String> gengraph(Set<Module> modules) {
        // build a Graph containing only requires public edges
        // with transitive reduction.
        Graph.Builder<String> rpgbuilder = new Graph.Builder<>();
        for (Module module : modules) {
            ModuleDescriptor md = module.descriptor();
            String mn = md.name();
            md.requires().stream()
                    .filter(d -> d.modifiers().contains(PUBLIC))
                    .map(d -> d.name())
                    .forEach(d -> rpgbuilder.addEdge(mn, d));
        }

        Graph<String> rpg = rpgbuilder.build().reduce();

        // build the readability graph
        Graph.Builder<String> builder = new Graph.Builder<>();
        for (Module module : modules) {
            ModuleDescriptor md = module.descriptor();
            String mn = md.name();
            builder.addNode(mn);
            configuration.reads(module)
                    .map(Module::name)
                    .forEach(d -> builder.addEdge(mn, d));
        }

        // transitive reduction of requires edges
        return builder.build().reduce(rpg);
    }

    // ---- for testing purpose
    public ModuleDescriptor[] descriptors(String name) {
        ModuleDeps moduleDeps = modules.keySet().stream()
            .filter(m -> m.name().equals(name))
            .map(modules::get)
            .findFirst().get();

        ModuleDescriptor[] descriptors = new ModuleDescriptor[3];
        descriptors[0] = moduleDeps.root.descriptor();
        descriptors[1] = moduleDeps.descriptor();
        descriptors[2] = moduleDeps.reduced();
        return descriptors;
    }

    public Map<String, Set<String>> unusedQualifiedExports(String name) {
        ModuleDeps moduleDeps = modules.keySet().stream()
            .filter(m -> m.name().equals(name))
            .map(modules::get)
            .findFirst().get();
        return moduleDeps.unusedQualifiedExports;
    }
}
