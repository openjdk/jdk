/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.jdeps.ModuleDotGraph;

import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Generate the DOT file for a module graph for each module in the JDK
 * after transitive reduction.
 */
public class GenGraphs {

    public static void main(String[] args) throws Exception {
        Path dir = null;
        boolean spec = false;
        Properties props = null;
        for (int i=0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--spec")) {
                spec = true;
            } else if (arg.equals("--dot-attributes")) {
                if (i++ == args.length) {
                    throw new IllegalArgumentException("Missing argument: --dot-attributes option");
                }
                props = new Properties();
                props.load(Files.newInputStream(Paths.get(args[i])));
            } else if (arg.equals("--output")) {
                dir = ++i < args.length ? Paths.get(args[i]) : null;
            } else if (arg.startsWith("-")) {
                throw new IllegalArgumentException("Invalid option: " + arg);
            }
        }

        if (dir == null) {
            System.err.println("ERROR: must specify --output argument");
            System.exit(1);
        }

        Files.createDirectories(dir);
        ModuleGraphAttributes attributes;
        if (props != null) {
            attributes = new ModuleGraphAttributes(props);
        } else {
            attributes = new ModuleGraphAttributes();
        }
        GenGraphs genGraphs = new GenGraphs(dir, spec, attributes);

        // print dot file for each module
        Map<String, Configuration> configurations = new HashMap<>();
        Set<String> modules = new HashSet<>();
        ModuleFinder finder = ModuleFinder.ofSystem();
        for (ModuleReference mref : finder.findAll()) {
            String name = (mref.descriptor().name());
            modules.add(name);
            if (genGraphs.accept(name, mref.descriptor())) {
                configurations.put(name, Configuration.empty()
                                                      .resolve(finder,
                                                               ModuleFinder.of(),
                                                               Set.of(name)));
            }
        }

        if (genGraphs.accept("jdk", null)) {
            // print a graph of all JDK modules
            configurations.put("jdk", Configuration.empty()
                                                   .resolve(finder,
                                                            ModuleFinder.of(),
                                                            modules));
        }

        genGraphs.genDotFiles(configurations);
    }

    /**
     * Custom dot file attributes.
     */
    static class ModuleGraphAttributes extends ModuleDotGraph.DotGraphAttributes {
        final Properties attrs;
        final Map<String, Integer> weights;

        ModuleGraphAttributes() {
            this(new Properties());
        };
        ModuleGraphAttributes(Properties props) {
            this.attrs = props;
            this.weights = initWeights(props);
        }

        @Override
        public double nodeSep() {
            String v = attrs.getProperty("nodesep");
            return v != null ? Double.valueOf(v) : super.nodeSep();
        }

        @Override
        public double rankSep() {
            String v = attrs.getProperty("ranksep");
            return v != null ? Double.valueOf(v) : super.rankSep();
        }

        @Override
        public int fontSize() {
            String v = attrs.getProperty("fontsize");
            return v != null ? Integer.valueOf(v) : super.fontSize();
        }

        @Override
        public String fontName() {
            String v = attrs.getProperty("fontname");
            return v != null ? v : super.fontName();
        }

        @Override
        public String fontColor() {
            String v = attrs.getProperty("fontcolor");
            return v != null ? v : super.fontColor();
        }

        @Override
        public int arrowSize() {
            String v = attrs.getProperty("arrowsize");
            return v != null ? Integer.valueOf(v) : super.arrowSize();
        }

        @Override
        public int arrowWidth() {
            String v = attrs.getProperty("arrowwidth");
            return v != null ? Integer.valueOf(v) : super.arrowWidth();
        }

        @Override
        public String arrowColor() {
            String v = attrs.getProperty("arrowcolor");
            return v != null ? v : super.arrowColor();
        }

        @Override
        public List<Set<String>> ranks() {
            return attrs.stringPropertyNames().stream()
                        .filter(k -> k.startsWith("ranks."))
                        .sorted()
                        .map(k -> Arrays.stream(attrs.getProperty(k).split(","))
                                        .collect(Collectors.toSet()))
                        .toList();
        }

        @Override
        public String requiresMandatedColor() {
            String v = attrs.getProperty("requiresMandatedColor");
            return v != null ? v : super.requiresMandatedColor();
        }

        @Override
        public String javaSubgraphColor() {
            String v = attrs.getProperty("javaSubgraphColor");
            return v != null ? v : super.javaSubgraphColor();
        }

        @Override
        public String jdkSubgraphColor() {
            String v = attrs.getProperty("jdkSubgraphColor");
            return v != null ? v : super.jdkSubgraphColor();
        }

        @Override
        public String nodeMargin() {
            String v = attrs.getProperty("node-margin");
            return v != null ? v : super.nodeMargin();
        }

        @Override
        public String requiresStyle() {
            String v = attrs.getProperty("requiresStyle");
            return v != null ? v : super.requiresStyle();
        };

        @Override
        public String requiresTransitiveStyle() {
            String v = attrs.getProperty("requiresTransitiveStyle");
            return v != null ? v : super.requiresTransitiveStyle();
        };

        @Override
        public int weightOf(String s, String t) {
            int w = weights.getOrDefault(s + ":" + t, 1);
            if (w != 1)
                return w;
            if (s.startsWith("java.") && t.startsWith("java."))
                return 10;
            return 1;
        }

        /*
         * Create a map of <mn>:<dep> with a weight trying to line up
         * the modules in the weights property in the specified order.
         */
        public static Map<String, Integer> initWeights(Properties props) {
            String[] modules = props.getProperty("weights", "").split(",");
            int len = modules.length;
            if (len == 0) return Map.of();

            Map<String, Integer> weights = new HashMap<>();
            String mn = modules[0];
            int w = 10000;
            for (int i = 1; i < len; i++) {
                String dep = modules[i];
                weights.put(mn + ":" + dep, w);
                mn = dep;
            }
            weights.put(mn + ":java.base", w);
            return weights;
        }
    }

    private final Path dir;
    private final boolean spec;
    private final ModuleGraphAttributes attributes;
    GenGraphs(Path dir, boolean spec, ModuleGraphAttributes attributes) {
        this.dir = dir;
        this.spec = spec;
        this.attributes = attributes;
    }

    void genDotFiles(Map<String, Configuration> configurations) throws IOException {
        ModuleDotGraph dotGraph = new ModuleDotGraph(configurations, spec);
        dotGraph.genDotFiles(dir, attributes);
    }

    /**
     * Returns true for any name if generating graph for non-spec;
     * otherwise, returns true except "jdk" and name with "jdk.internal." prefix
     */
    boolean accept(String name, ModuleDescriptor descriptor) {
        if (!spec)
            return true;

        return !name.equals("jdk") && !name.startsWith("jdk.internal.");
    }
}
