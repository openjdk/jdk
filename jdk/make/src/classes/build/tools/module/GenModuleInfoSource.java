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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A build tool to extend the module-info.java in the source tree
 * for platform-specific exports, uses, and provides and write
 * to the specified output file.
 *
 * GenModulesList build tool currently generates the modules.list from
 * the module-info.java from the source tree that will be used for
 * the make target and dependences.
 *
 * The build currently invokes gensrc-$MODULE.gmk after modules.list
 * is generated.  Hence, platform-specific requires is not supported.
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
        Map<String, Set<String>> options = new HashMap<>();

        // validate input arguments
        for (int i = 0; i < args.length; i++){
            String option = args[i];
            if (option.startsWith("-")) {
                String arg = args[++i];
                if (option.equals("-exports") ||
                        option.equals("-uses") ||
                        option.equals("-provides")) {
                    options.computeIfAbsent(option, _k -> new HashSet<>()).add(arg);
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
        // read module-info.java
        Module.Builder builder = ModuleInfoReader.builder(moduleInfoJava);
        augment(builder, options);

        // generate new module-info.java
        Module module = builder.build();
        Path parent = outfile.getParent();
        if (parent != null)
            Files.createDirectories(parent);

        try (BufferedWriter writer = Files.newBufferedWriter(outfile)) {
            writer.write(module.toString());
        }
    }

    private static void augment(Module.Builder builder, Map<String, Set<String>> options) {
        for (String opt : options.keySet()) {
            if (opt.equals("-exports")) {
                for (String arg : options.get(opt)) {
                    int index = arg.indexOf('/');
                    if (index > 0) {
                        String pn = arg.substring(0, index);
                        String mn = arg.substring(index + 1, arg.length());
                        builder.exportTo(pn, mn);
                    } else {
                        builder.export(arg);
                    }
                }
            } else if (opt.equals("-uses")) {
                options.get(opt).stream()
                        .forEach(builder::use);
            } else if (opt.equals("-provides")) {
                for (String arg : options.get(opt)) {
                    int index = arg.indexOf('/');
                    if (index <= 0) {
                        throw new IllegalArgumentException("invalid -provide argument: " + arg);
                    }
                    String service = arg.substring(0, index);
                    String impl = arg.substring(index + 1, arg.length());
                    builder.provide(service, impl);
                }
            }
        }
    }
}
