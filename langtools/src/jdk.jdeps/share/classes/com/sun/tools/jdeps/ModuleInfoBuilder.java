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

import static com.sun.tools.jdeps.JdepsTask.*;
import static com.sun.tools.jdeps.Analyzer.NOT_FOUND;
import static com.sun.tools.jdeps.JdepsFilter.DEFAULT_FILTER;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Exports;
import java.lang.module.ModuleDescriptor.Provides;
import java.lang.module.ModuleDescriptor.Requires;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class ModuleInfoBuilder {
    final JdepsConfiguration configuration;
    final Path outputdir;

    final DependencyFinder dependencyFinder;
    final Analyzer analyzer;
    final Map<Module, Module> strictModules;
    public ModuleInfoBuilder(JdepsConfiguration configuration,
                             List<String> args,
                             Path outputdir) {
        this.configuration = configuration;
        this.outputdir = outputdir;

        this.dependencyFinder = new DependencyFinder(configuration, DEFAULT_FILTER);
        this.analyzer = new Analyzer(configuration, Analyzer.Type.CLASS, DEFAULT_FILTER);

        // add targets to modulepath if it has module-info.class
        List<Path> paths = args.stream()
            .map(fn -> Paths.get(fn))
            .collect(Collectors.toList());

        // automatic module to convert to strict module
        this.strictModules = ModuleFinder.of(paths.toArray(new Path[0]))
                .findAll().stream()
                .map(configuration::toModule)
                .collect(Collectors.toMap(Function.identity(), Function.identity()));

        Optional<Module> om = strictModules.keySet().stream()
                                    .filter(m -> !m.descriptor().isAutomatic())
                                    .findAny();
        if (om.isPresent()) {
            throw new UncheckedBadArgs(new BadArgs("err.genmoduleinfo.not.jarfile",
                                                   om.get().getPathName()));
        }
        if (strictModules.isEmpty()) {
            throw new UncheckedBadArgs(new BadArgs("err.invalid.path", args));
        }
    }

    public boolean run() throws IOException {
        try {
            // pass 1: find API dependencies
            Map<Archive, Set<Archive>> requiresPublic = computeRequiresPublic();

            // pass 2: analyze all class dependences
            dependencyFinder.parse(automaticModules().stream());

            analyzer.run(automaticModules(), dependencyFinder.locationToArchive());

            // computes requires and requires public
            automaticModules().forEach(m -> {
                Map<String, Boolean> requires;
                if (requiresPublic.containsKey(m)) {
                    requires = requiresPublic.get(m).stream()
                        .map(Archive::getModule)
                        .collect(Collectors.toMap(Module::name, (v) -> Boolean.TRUE));
                } else {
                    requires = new HashMap<>();
                }
                analyzer.requires(m)
                    .map(Archive::getModule)
                    .forEach(d -> requires.putIfAbsent(d.name(), Boolean.FALSE));

                strictModules.put(m, m.toStrictModule(requires));
            });

            // generate module-info.java
            descriptors().forEach(md -> writeModuleInfo(outputdir, md));

            // done parsing
            for (Module m : automaticModules()) {
                m.close();
            }

            // find any missing dependences
            return automaticModules().stream()
                        .flatMap(analyzer::requires)
                        .allMatch(m -> !m.equals(NOT_FOUND));
        } finally {
            dependencyFinder.shutdown();
        }
    }

    /**
     * Returns the stream of resulting modules
     */
    Stream<Module> modules() {
        return strictModules.values().stream();
    }

    /**
     * Returns the stream of resulting ModuleDescriptors
     */
    public Stream<ModuleDescriptor> descriptors() {
        return strictModules.values().stream().map(Module::descriptor);
    }

    void visitMissingDeps(Analyzer.Visitor visitor) {
        automaticModules().stream()
            .filter(m -> analyzer.requires(m).anyMatch(d -> d.equals(NOT_FOUND)))
            .forEach(m -> {
                analyzer.visitDependences(m, visitor, Analyzer.Type.VERBOSE);
            });
    }
    void writeModuleInfo(Path dir, ModuleDescriptor descriptor) {
        String mn = descriptor.name();
        Path srcFile = dir.resolve(mn).resolve("module-info.java");
        try {
            Files.createDirectories(srcFile.getParent());
            System.out.println("writing to " + srcFile);
            try (PrintWriter pw = new PrintWriter(Files.newOutputStream(srcFile))) {
                printModuleInfo(pw, descriptor);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void printModuleInfo(PrintWriter writer, ModuleDescriptor descriptor) {
        writer.format("module %s {%n", descriptor.name());

        Map<String, Module> modules = configuration.getModules();
        // first print the JDK modules
        descriptor.requires().stream()
                  .filter(req -> !req.name().equals("java.base"))   // implicit requires
                  .sorted(Comparator.comparing(Requires::name))
                  .forEach(req -> writer.format("    requires %s;%n", req));

        descriptor.exports().stream()
                  .peek(exp -> {
                      if (exp.targets().size() > 0)
                          throw new InternalError(descriptor.name() + " qualified exports: " + exp);
                  })
                  .sorted(Comparator.comparing(Exports::source))
                  .forEach(exp -> writer.format("    exports %s;%n", exp.source()));

        descriptor.provides().values().stream()
                    .sorted(Comparator.comparing(Provides::service))
                    .forEach(p -> p.providers().stream()
                        .sorted()
                        .forEach(impl -> writer.format("    provides %s with %s;%n", p.service(), impl)));

        writer.println("}");
    }


    private Set<Module> automaticModules() {
        return strictModules.keySet();
    }

    /**
     * Compute 'requires public' dependences by analyzing API dependencies
     */
    private Map<Archive, Set<Archive>> computeRequiresPublic() throws IOException {
        // parse the input modules
        dependencyFinder.parseExportedAPIs(automaticModules().stream());

        return dependencyFinder.dependences();
    }
}
