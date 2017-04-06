/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import static java.lang.module.ModuleDescriptor.Requires.Modifier.*;
import static java.util.stream.Collectors.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generate dot graph for modules
 */
public class ModuleDotGraph {
    private final Map<String, Configuration> configurations;
    private final boolean apiOnly;
    public ModuleDotGraph(JdepsConfiguration config, boolean apiOnly) {
        this(config.rootModules().stream()
                   .map(Module::name)
                   .sorted()
                   .collect(toMap(Function.identity(), mn -> config.resolve(Set.of(mn)))),
             apiOnly);
    }

    public ModuleDotGraph(Map<String, Configuration> configurations, boolean apiOnly) {
        this.configurations = configurations;
        this.apiOnly = apiOnly;
    }

    /**
     * Generate dotfile for all modules
     *
     * @param dir output directory
     */
    public boolean genDotFiles(Path dir) throws IOException {
        Files.createDirectories(dir);
        for (String mn : configurations.keySet()) {
            Path path = dir.resolve(mn + ".dot");
            genDotFile(path, mn, configurations.get(mn));
        }
        return true;
    }

    /**
     * Generate dotfile of the given path
     */
    public void genDotFile(Path path, String name, Configuration configuration)
        throws IOException
    {
        // transitive reduction
        Graph<String> graph = apiOnly
                ? requiresTransitiveGraph(configuration, Set.of(name))
                : gengraph(configuration);

        DotGraphBuilder builder = new DotGraphBuilder(name, graph);
        builder.subgraph("se", "java", DotGraphBuilder.ORANGE,
                         DotGraphBuilder.JAVA_SE_SUBGRAPH)
               .subgraph("jdk", "jdk", DotGraphBuilder.BLUE,
                         DotGraphBuilder.JDK_SUBGRAPH)
               .descriptors(graph.nodes().stream()
                                 .map(mn -> configuration.findModule(mn).get()
                                                .reference().descriptor()));
        // build dot file
        builder.build(path);
    }

    /**
     * Returns a Graph of the given Configuration after transitive reduction.
     *
     * Transitive reduction of requires transitive edge and requires edge have
     * to be applied separately to prevent the requires transitive edges
     * (e.g. U -> V) from being reduced by a path (U -> X -> Y -> V)
     * in which  V would not be re-exported from U.
     */
    private Graph<String> gengraph(Configuration cf) {
        Graph.Builder<String> builder = new Graph.Builder<>();
        cf.modules().stream()
            .forEach(resolvedModule -> {
                String mn = resolvedModule.reference().descriptor().name();
                builder.addNode(mn);
                resolvedModule.reads().stream()
                    .map(ResolvedModule::name)
                    .forEach(target -> builder.addEdge(mn, target));
            });

        Graph<String> rpg = requiresTransitiveGraph(cf, builder.nodes);
        return builder.build().reduce(rpg);
    }


    /**
     * Returns a Graph containing only requires transitive edges
     * with transitive reduction.
     */
    public Graph<String> requiresTransitiveGraph(Configuration cf,
                                                 Set<String> roots)
    {
        Deque<String> deque = new ArrayDeque<>(roots);
        Set<String> visited = new HashSet<>();
        Graph.Builder<String> builder = new Graph.Builder<>();

        while (deque.peek() != null) {
            String mn = deque.pop();
            if (visited.contains(mn))
                continue;

            visited.add(mn);
            builder.addNode(mn);
            ModuleDescriptor descriptor = cf.findModule(mn).get()
                .reference().descriptor();
            descriptor.requires().stream()
                .filter(d -> d.modifiers().contains(TRANSITIVE)
                                || d.name().equals("java.base"))
                .map(d -> d.name())
                .forEach(d -> {
                    deque.add(d);
                    builder.addEdge(mn, d);
                });
        }

        return builder.build().reduce();
    }

    public static class DotGraphBuilder {
        static final Set<String> JAVA_SE_SUBGRAPH = javaSE();
        static final Set<String> JDK_SUBGRAPH = jdk();

        private static Set<String> javaSE() {
            String root = "java.se.ee";
            ModuleFinder system = ModuleFinder.ofSystem();
            if (system.find(root).isPresent()) {
                return Stream.concat(Stream.of(root),
                                     Configuration.empty().resolve(system,
                                                                   ModuleFinder.of(),
                                                                   Set.of(root))
                                                  .findModule(root).get()
                                                  .reads().stream()
                                                  .map(ResolvedModule::name))
                             .collect(toSet());
            } else {
                // approximation
                return system.findAll().stream()
                    .map(ModuleReference::descriptor)
                    .map(ModuleDescriptor::name)
                    .filter(name -> name.startsWith("java.") &&
                                        !name.equals("java.smartcardio"))
                    .collect(Collectors.toSet());
            }
        }

        private static Set<String> jdk() {
            return ModuleFinder.ofSystem().findAll().stream()
                    .map(ModuleReference::descriptor)
                    .map(ModuleDescriptor::name)
                    .filter(name -> !JAVA_SE_SUBGRAPH.contains(name) &&
                                        (name.startsWith("java.") ||
                                            name.startsWith("jdk.") ||
                                            name.startsWith("javafx.")))
                    .collect(Collectors.toSet());
        }

        static class SubGraph {
            final String name;
            final String group;
            final String color;
            final Set<String> nodes;
            SubGraph(String name, String group, String color, Set<String> nodes) {
                this.name = Objects.requireNonNull(name);
                this.group = Objects.requireNonNull(group);
                this.color = Objects.requireNonNull(color);
                this.nodes = Objects.requireNonNull(nodes);
            }
        }

        static final String ORANGE = "#e76f00";
        static final String BLUE = "#437291";
        static final String GRAY = "#dddddd";
        static final String BLACK = "#000000";

        static final String FONT_NAME = "DejaVuSans";
        static final int FONT_SIZE = 12;
        static final int ARROW_SIZE = 1;
        static final int ARROW_WIDTH = 2;
        static final int RANK_SEP = 1;

        static final String REEXPORTS = "";
        static final String REQUIRES = "style=\"dashed\"";
        static final String REQUIRES_BASE = "color=\"" + GRAY + "\"";

        // can be configured
        static double rankSep   = RANK_SEP;
        static String fontColor = BLACK;
        static String fontName  = FONT_NAME;
        static int fontsize     = FONT_SIZE;
        static int arrowWidth   = ARROW_WIDTH;
        static int arrowSize    = ARROW_SIZE;
        static final Map<String, Integer> weights = new HashMap<>();
        static final List<Set<String>> ranks = new ArrayList<>();

        private final String name;
        private final Graph<String> graph;
        private final Set<ModuleDescriptor> descriptors = new TreeSet<>();
        private final List<SubGraph> subgraphs = new ArrayList<>();
        public DotGraphBuilder(String name, Graph<String> graph) {
            this.name = name;
            this.graph = graph;
        }

        public DotGraphBuilder descriptors(Stream<ModuleDescriptor> descriptors) {
            descriptors.forEach(this.descriptors::add);
            return this;
        }

        public void build(Path filename) throws IOException {
            try (BufferedWriter writer = Files.newBufferedWriter(filename);
                 PrintWriter out = new PrintWriter(writer)) {

                out.format("digraph \"%s\" {%n", name);
                out.format("  nodesep=.5;%n");
                out.format("  ranksep=%f;%n", rankSep);
                out.format("  pencolor=transparent;%n");
                out.format("  node [shape=plaintext, fontname=\"%s\", fontsize=%d, margin=\".2,.2\"];%n",
                           fontName, fontsize);
                out.format("  edge [penwidth=%d, color=\"#999999\", arrowhead=open, arrowsize=%d];%n",
                           arrowWidth, arrowSize);

                // same RANKS
                ranks.stream()
                     .map(nodes -> descriptors.stream()
                                        .map(ModuleDescriptor::name)
                                        .filter(nodes::contains)
                                        .map(mn -> "\"" + mn + "\"")
                                        .collect(joining(",")))
                     .filter(group -> group.length() > 0)
                     .forEach(group -> out.format("  {rank=same %s}%n", group));

                subgraphs.forEach(subgraph -> {
                    out.format("  subgraph %s {%n", subgraph.name);
                    descriptors.stream()
                        .map(ModuleDescriptor::name)
                        .filter(subgraph.nodes::contains)
                        .forEach(mn -> printNode(out, mn, subgraph.color, subgraph.group));
                    out.format("  }%n");
                });

                descriptors.stream()
                    .filter(md -> graph.contains(md.name()) &&
                                    !graph.adjacentNodes(md.name()).isEmpty())
                    .forEach(md -> printNode(out, md, graph.adjacentNodes(md.name())));

                out.println("}");
            }
        }

        public DotGraphBuilder subgraph(String name, String group, String color,
                                 Set<String> nodes) {
            subgraphs.add(new SubGraph(name, group, color, nodes));
            return this;
        }

        public void printNode(PrintWriter out, String node, String color, String group) {
            out.format("  \"%s\" [fontcolor=\"%s\", group=%s];%n",
                       node, color, group);
        }

        public void printNode(PrintWriter out, ModuleDescriptor md, Set<String> edges) {
            Set<String> requiresTransitive = md.requires().stream()
                .filter(d -> d.modifiers().contains(TRANSITIVE))
                .map(d -> d.name())
                .collect(toSet());

            String mn = md.name();
            edges.stream().forEach(dn -> {
                String attr = dn.equals("java.base") ? REQUIRES_BASE
                    : (requiresTransitive.contains(dn) ? REEXPORTS : REQUIRES);

                int w = weightOf(mn, dn);
                if (w > 1) {
                    if (!attr.isEmpty())
                        attr += ", ";

                    attr += "weight=" + w;
                }
                out.format("  \"%s\" -> \"%s\" [%s];%n", mn, dn, attr);
            });
        }

        public int weightOf(String s, String t) {
            int w = weights.getOrDefault(s + ":" + t, 1);
            if (w != 1)
                return w;
            if (s.startsWith("java.") && t.startsWith("java."))
                return 10;
            return 1;
        }

        public static void sameRankNodes(Set<String> nodes) {
            ranks.add(nodes);
        }

        public static void weight(String s, String t, int w) {
            weights.put(s + ":" + t, w);
        }

        public static void setRankSep(double value) {
            rankSep = value;
        }

        public static void setFontSize(int size) {
            fontsize = size;
        }

        public static void setFontColor(String color) {
            fontColor = color;
        }

        public static void setArrowSize(int size) {
            arrowSize = size;
        }

        public static void setArrowWidth(int width) {
            arrowWidth = width;
        }
    }
}
