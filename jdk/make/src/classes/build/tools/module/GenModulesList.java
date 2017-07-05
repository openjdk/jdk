/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.module;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * $ java build.tools.module.GenModulesList \
 *        -o modules.list \
 *        top/modules.xml ...
 */
public final class GenModulesList {
    private final static String USAGE =
        "Usage: GenModulesList -o <output file> path-to-modules-xml";

    private Set<Module> modules = new HashSet<>();
    private HashMap<String,Module> nameToModule = new HashMap<>();

    public static void main(String[] args) throws Exception {
        GenModulesList gen = new GenModulesList();
        gen.run(args);
    }

    void run(String[] args) throws Exception {
        Path outfile = null;
        int i = 0;
        while (i < args.length) {
            String arg = args[i];
            if (arg.equals("-o")) {
                outfile = Paths.get(args[i+1]);
                i = i+2;
            } else {
                break;
            }
        }
        if (outfile == null || i >= args.length) {
            System.err.println(USAGE);
            System.exit(-1);
        }

        for (; i < args.length; i++) {
            Path p = Paths.get(args[i]);
            modules.addAll(ModulesXmlReader.readModules(p));
        }

        modules.stream()
               .forEach(m -> nameToModule.put(m.name(), m));

        Path parent = outfile.getParent();
        if (parent != null)
            Files.createDirectories(parent);

        Iterable<Module> sortedModules = (new TopoSorter(modules)).result();
        try (PrintWriter writer = new PrintWriter(outfile.toFile())) {
            for (Module m : sortedModules) {
                if (isNotAggregator(m)) {
                    String deps = getModuleDependences(m).stream()
                            .filter(GenModulesList::isNotAggregator)
                            .map(Module::name)
                            .collect(Collectors.joining(" "));
                    writer.format("%s: %s%n", m.name(), deps);
                }
            }
        }
    }

    private Module nameToModule(String name) {
        return nameToModule.get(name);
    }

    private Set<Module> getModuleDependences(Module m) {
        return m.requires().stream()
                .map(d -> d.name())
                .map(this::nameToModule)
                .collect(Collectors.toSet());
    }

    static boolean isNotAggregator(Module m) {
        return isNotAggregator(m.name());
    }

    static boolean isNotAggregator(String name) {
        return AGGREGATORS.contains(name) ? false : true;
    }

    static final List<String> AGGREGATORS = Arrays.asList(new String[] {
            "java.se", "java.compact1", "java.compact2", "java.compact3"});

    class TopoSorter {
        final Deque<Module> result = new LinkedList<>();
        final Deque<Module> nodes = new LinkedList<>();

        TopoSorter(Collection<Module> nodes) {
            nodes.stream()
                 .forEach(m -> this.nodes.add(m));

            sort();
        }

        public Iterable<Module> result() {
            return result;
        }

        private void sort() {
            Deque<Module> visited = new LinkedList<>();
            Deque<Module> done = new LinkedList<>();
            Module node;
            while ((node = nodes.poll()) != null) {
                if (!visited.contains(node)) {
                    visit(node, visited, done);
                }
            }
        }

        private void visit(Module m, Deque<Module> visited, Deque<Module> done) {
            if (visited.contains(m)) {
                if (!done.contains(m)) {
                    throw new IllegalArgumentException("Cyclic detected: " +
                            m + " " + getModuleDependences(m));
                }
                return;
            }
            visited.add(m);
            getModuleDependences(m).stream()
                                   .forEach(x -> visit(x, visited, done));
            done.add(m);
            result.addLast(m);
        }
    }
}
