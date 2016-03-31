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
package build.tools.module;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A build tool to extend the module-info.java in the source tree for
 * platform-specific exports, uses, and provides and write to the specified
 * output file. Injecting platform-specific requires is not supported.
 *
 * The extra exports, uses, provides can be specified in module-info.java.extra
 * files and GenModuleInfoSource will be invoked for each module that has
 * module-info.java.extra in the source directory.
 */
public class GenModuleInfoSource {
    private final static String USAGE =
        "Usage: GenModuleInfoSource [option] -o <output file> <module-info-java>\n" +
        "Options are:\n" +
        "  -exports  <package-name>\n" +
        "  -exports  <package-name>/<module-name>\n" +
        "  -uses     <service>\n" +
        "  -provides <service>/<provider-impl-classname>\n";

    public static void main(String... args) throws Exception {
        Path outfile = null;
        Path moduleInfoJava = null;
        GenModuleInfoSource genModuleInfo = new GenModuleInfoSource();

        // validate input arguments
        for (int i = 0; i < args.length; i++){
            String option = args[i];
            if (option.startsWith("-")) {
                String arg = args[++i];
                if (option.equals("-exports")) {
                    int index = arg.indexOf('/');
                        if (index > 0) {
                            String pn = arg.substring(0, index);
                            String mn = arg.substring(index + 1, arg.length());
                            genModuleInfo.exportTo(pn, mn);
                        } else {
                            genModuleInfo.export(arg);
                        }
                } else if (option.equals("-uses")) {
                    genModuleInfo.use(arg);
                } else if (option.equals("-provides")) {
                        int index = arg.indexOf('/');
                        if (index <= 0) {
                            throw new IllegalArgumentException("invalid -provide argument: " + arg);
                        }
                        String service = arg.substring(0, index);
                        String impl = arg.substring(index + 1, arg.length());
                        genModuleInfo.provide(service, impl);
                } else if (option.equals("-o")) {
                    outfile = Paths.get(arg);
                } else {
                    throw new IllegalArgumentException("invalid option: " + option);
                }
            } else if (moduleInfoJava != null) {
                throw new IllegalArgumentException("more than one module-info.java");
            } else {
                moduleInfoJava = Paths.get(option);
                if (Files.notExists(moduleInfoJava)) {
                    throw new IllegalArgumentException(option + " not exist");
                }
            }
        }

        if (moduleInfoJava == null || outfile == null) {
            System.err.println(USAGE);
            System.exit(-1);
        }

        // generate new module-info.java
        genModuleInfo.generate(moduleInfoJava, outfile);
    }

    private final Set<String> exports = new HashSet<>();
    private final Map<String, Set<String>> exportsTo = new HashMap<>();
    private final Set<String> uses = new HashSet<>();
    private final Map<String, Set<String>> provides = new HashMap<>();
    GenModuleInfoSource() {
    }

    private void export(String p) {
        Objects.requireNonNull(p);
        if (exports.contains(p) || exportsTo.containsKey(p)) {
            throw new RuntimeException("duplicated exports: " + p);
        }
        exports.add(p);
    }
    private void exportTo(String p, String mn) {
        Objects.requireNonNull(p);
        Objects.requireNonNull(mn);
        if (exports.contains(p)) {
            throw new RuntimeException("unqualified exports already exists: " + p);
        }
        exportsTo.computeIfAbsent(p, _k -> new HashSet<>()).add(mn);
    }

    private void use(String service) {
        uses.add(service);
    }

    private void provide(String s, String impl) {
        provides.computeIfAbsent(s, _k -> new HashSet<>()).add(impl);
    }

    private void generate(Path sourcefile, Path outfile) throws IOException {
        Path parent = outfile.getParent();
        if (parent != null)
            Files.createDirectories(parent);

        List<String> lines = Files.readAllLines(sourcefile);
        try (BufferedWriter bw = Files.newBufferedWriter(outfile);
             PrintWriter writer = new PrintWriter(bw)) {
            int lineNumber = 0;
            for (String l : lines) {
                lineNumber++;
                String[] s = l.trim().split("\\s+");
                String keyword = s[0].trim();
                int nextIndex = keyword.length();
                String exp = null;
                int n = l.length();
                switch (keyword) {
                    case "exports":
                        boolean inExportsTo = false;
                        // assume package name immediately after exports
                        exp = s[1].trim();
                        if (s.length >= 3) {
                            nextIndex = l.indexOf(exp, nextIndex) + exp.length();
                            if (s[2].trim().equals("to")) {
                                inExportsTo = true;
                                n = l.indexOf("to", nextIndex) + "to".length();
                            } else {
                                throw new RuntimeException(sourcefile + ", line " +
                                    lineNumber + ", is malformed: " + s[2]);
                            }
                        }

                        // inject the extra targets after "to"
                        if (inExportsTo) {
                            writer.println(injectExportTargets(exp, l, n));
                        } else {
                            writer.println(l);
                        }
                        break;
                    case "to":
                        if (exp == null) {
                            throw new RuntimeException(sourcefile + ", line " +
                                lineNumber + ", is malformed");
                        }
                        n = l.indexOf("to", nextIndex) + "to".length();
                        writer.println(injectExportTargets(exp, l, n));
                        break;
                    case "}":
                        doAugments(writer);
                        // fall through
                    default:
                        writer.println(l);
                        // reset exports
                        exp = null;
                }
            }
        }
    }

    private String injectExportTargets(String pn, String exp, int pos) {
        Set<String> targets = exportsTo.remove(pn);
        if (targets != null) {
            StringBuilder sb = new StringBuilder();
            // inject the extra targets after the given pos
            sb.append(exp.substring(0, pos))
              .append("\n\t")
              .append(targets.stream()
                             .collect(Collectors.joining(",", "", ",")))
              .append(" /* injected */");
            if (pos < exp.length()) {
                // print the remaining statement followed "to"
                sb.append("\n\t")
                  .append(exp.substring(pos+1, exp.length()));
            }
            return sb.toString();
        } else {
            return exp;
        }
    }

    private void doAugments(PrintWriter writer) {
        if ((exports.size() + exportsTo.size() + uses.size() + provides.size()) == 0)
            return;

        writer.println("    // augmented from module-info.java.extra");
        exports.stream()
            .sorted()
            .forEach(e -> writer.format("    exports %s;%n", e));
        // remaining injected qualified exports
        exportsTo.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> String.format("    exports %s to%n%s;", e.getKey(),
                                    e.getValue().stream().sorted()
                                        .map(mn -> String.format("        %s", mn))
                                        .collect(Collectors.joining(",\n"))))
            .forEach(writer::println);
        uses.stream().sorted()
            .forEach(s -> writer.format("    uses %s;%n", s));
        provides.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .flatMap(e -> e.getValue().stream().sorted()
                           .map(impl -> String.format("    provides %s with %s;",
                                                      e.getKey(), impl)))
            .forEach(writer::println);
    }
}
