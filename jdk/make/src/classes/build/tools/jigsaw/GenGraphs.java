/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.jigsaw;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import static java.lang.module.ModuleDescriptor.Requires.Modifier.PUBLIC;

/**
 * Generate the DOT file for a module graph for each module in the JDK
 * after transitive reduction.
 */
public class GenGraphs {

    public static void main(String[] args) throws Exception {

        if (args.length != 1) {
            System.err.println("ERROR: specify the output directory");
            System.exit(1);
        }
        Path dir = Paths.get(args[0]);
        Files.createDirectories(dir);

        ModuleFinder finder = ModuleFinder.ofSystem();

        Set<ModuleDescriptor> javaSEModules
            = new TreeSet<>(finder.findAll().stream()
                                  .map(ModuleReference::descriptor)
                                  .filter(m -> (m.name().startsWith("java.") &&
                                               !m.name().equals("java.smartcardio")))
                                  .collect(Collectors.toSet()));
        Set<ModuleDescriptor> jdkModules
            = new TreeSet<>(finder.findAll().stream()
                                  .map(ModuleReference::descriptor)
                                  .filter(m -> !javaSEModules.contains(m))
                                  .collect(Collectors.toSet()));

        GenGraphs genGraphs = new GenGraphs(javaSEModules, jdkModules);
        Set<String> mods = new HashSet<>();
        for (ModuleReference mref: finder.findAll()) {
            ModuleDescriptor descriptor = mref.descriptor();
            String name = descriptor.name();
            mods.add(name);
            Configuration cf = Configuration.empty()
                    .resolveRequires(finder,
                                     ModuleFinder.empty(),
                                     Set.of(name));
            genGraphs.genDotFile(dir, name, cf);
        }

        Configuration cf = Configuration.empty()
                .resolveRequires(finder,
                                 ModuleFinder.empty(),
                                 mods);
        genGraphs.genDotFile(dir, "jdk", cf);

    }

    private final Set<ModuleDescriptor> javaGroup;
    private final Set<ModuleDescriptor> jdkGroup;

    GenGraphs(Set<ModuleDescriptor> javaGroup, Set<ModuleDescriptor> jdkGroup) {
        this.javaGroup = Collections.unmodifiableSet(javaGroup);
        this.jdkGroup = Collections.unmodifiableSet(jdkGroup);
    }

    private static final String ORANGE = "#e76f00";
    private static final String BLUE = "#437291";
    private static final String GRAY = "#dddddd";

    private static final String REEXPORTS = "";
    private static final String REQUIRES = "style=\"dashed\"";
    private static final String REQUIRES_BASE = "color=\"" + GRAY + "\"";

    private static final Map<String,Integer> weights = new HashMap<>();

    private static void weight(String s, String t, int w) {
        weights.put(s + ":" + t, w);
    }

    private static int weightOf(String s, String t) {
        int w = weights.getOrDefault(s + ":" + t, 1);
        if (w != 1)
            return w;
        if (s.startsWith("java.") && t.startsWith("java."))
            return 10;
        return 1;
    }

    static {
        int h = 1000;
        weight("java.se", "java.compact3", h * 10);
        weight("jdk.compact3", "java.compact3", h * 10);
        weight("java.compact3", "java.compact2", h * 10);
        weight("java.compact2", "java.compact1", h * 10);
        weight("java.compact1", "java.logging", h * 10);
        weight("java.logging", "java.base", h * 10);
    }

    private void genDotFile(Path dir, String name, Configuration cf) throws IOException {
        try (PrintStream out
                 = new PrintStream(Files.newOutputStream(dir.resolve(name + ".dot")))) {

            Map<String, ModuleDescriptor> nameToModule = cf.modules().stream()
                    .map(ResolvedModule::reference)
                    .map(ModuleReference::descriptor)
                    .collect(Collectors.toMap(ModuleDescriptor::name, Function.identity()));

            Set<ModuleDescriptor> descriptors = new TreeSet<>(nameToModule.values());

            out.format("digraph \"%s\" {%n", name);
            out.format("size=\"25,25\";");
            out.format("nodesep=.5;%n");
            out.format("ranksep=1.5;%n");
            out.format("pencolor=transparent;%n");
            out.format("node [shape=plaintext, fontname=\"DejaVuSans\", fontsize=36, margin=\".2,.2\"];%n");
            out.format("edge [penwidth=4, color=\"#999999\", arrowhead=open, arrowsize=2];%n");

            out.format("subgraph %sse {%n", name.equals("jdk") ? "cluster_" : "");
            descriptors.stream()
                .filter(javaGroup::contains)
                .map(ModuleDescriptor::name)
                .forEach(mn -> out.format("  \"%s\" [fontcolor=\"%s\", group=%s];%n",
                                          mn, ORANGE, "java"));
            out.format("}%n");
            descriptors.stream()
                .filter(jdkGroup::contains)
                .map(ModuleDescriptor::name)
                .forEach(mn -> out.format("  \"%s\" [fontcolor=\"%s\", group=%s];%n",
                                          mn, BLUE, "jdk"));

            // transitive reduction
            Graph<String> graph = gengraph(cf);
            descriptors.forEach(md -> {
                String mn = md.name();
                Set<String> requiresPublic = md.requires().stream()
                        .filter(d -> d.modifiers().contains(PUBLIC))
                        .map(d -> d.name())
                        .collect(Collectors.toSet());

                graph.adjacentNodes(mn).forEach(dn -> {
                    String attr = dn.equals("java.base") ? REQUIRES_BASE
                            : (requiresPublic.contains(dn) ? REEXPORTS : REQUIRES);
                    int w = weightOf(mn, dn);
                    if (w > 1)
                        attr += "weight=" + w;
                    out.format("  \"%s\" -> \"%s\" [%s];%n", mn, dn, attr);
                });
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
    private Graph<String> gengraph(Configuration cf) {
        Graph.Builder<String> builder = new Graph.Builder<>();
        for (ResolvedModule resolvedModule : cf.modules()) {
            String mn = resolvedModule.reference().descriptor().name();
            builder.addNode(mn);
            resolvedModule.reads().stream()
                    .map(ResolvedModule::name)
                    .forEach(target -> builder.addEdge(mn, target));
        }
        Graph<String> rpg = requiresPublicGraph(cf);
        return builder.build().reduce(rpg);
    }

    /**
     * Returns a Graph containing only requires public edges
     * with transitive reduction.
     */
    private Graph<String> requiresPublicGraph(Configuration cf) {
        Graph.Builder<String> builder = new Graph.Builder<>();
        for (ResolvedModule resolvedModule : cf.modules()) {
            ModuleDescriptor descriptor = resolvedModule.reference().descriptor();
            String mn = descriptor.name();
            descriptor.requires().stream()
                    .filter(d -> d.modifiers().contains(PUBLIC))
                    .map(d -> d.name())
                    .forEach(d -> builder.addEdge(mn, d));
        }
        return builder.build().reduce();
    }
}
