/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.launcher;

import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.main.Option;
import com.sun.tools.javac.resources.LauncherProperties.Errors;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents runtime arguments that are relevant to {@code javac}.
 *
 * <p><strong>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</strong></p>
 */
record RelevantJavacOptions(List<String> forProgramCompilation,
                            List<String> forSubsequentCompilations) {

    /**
     * Returns the subset of the runtime arguments that are relevant to {@code javac}.
     * Generally, the relevant options are those for setting paths and for configuring the
     * module system.
     *
     * @param program the program descriptor
     * @param runtimeArgs the runtime arguments
     * @return the subset of the runtime arguments
     */
    static RelevantJavacOptions of(ProgramDescriptor program, String... runtimeArgs) throws Fault {
        var programOptions = new ArrayList<String>();
        var subsequentOptions = new ArrayList<String>();

        String sourceOpt = System.getProperty("jdk.internal.javac.source");
        if (sourceOpt != null) {
            Source source = Source.lookup(sourceOpt);
            if (source == null) {
                throw new Fault(Errors.InvalidValueForSource(sourceOpt));
            }
            programOptions.addAll(List.of("--release", sourceOpt));
            subsequentOptions.addAll(List.of("--release", sourceOpt));
        }

        for (int i = 0; i < runtimeArgs.length; i++) {
            String arg = runtimeArgs[i];
            String opt = arg, value = null;
            if (arg.startsWith("--")) {
                int eq = arg.indexOf('=');
                if (eq > 0) {
                    opt = arg.substring(0, eq);
                    value = arg.substring(eq + 1);
                }
            }

            switch (opt) {
                // The following options all expect a value, either in the following
                // position, or after '=', for options beginning "--".
                case "--class-path", "-classpath", "-cp",
                        "--module-path", "-p",
                        "--add-exports", "--add-modules",
                        "--limit-modules",
                        "--patch-module",
                        "--upgrade-module-path" -> {
                    if (value == null) {
                        if (i == runtimeArgs.length - 1) {
                            // should not happen when invoked from launcher
                            throw new Fault(Errors.NoValueForOption(opt));
                        }
                        value = runtimeArgs[++i];
                    }
                    if (opt.equals("--add-modules")) {
                        var modules = computeListOfAddModules(program, value);
                        if (modules.isEmpty()) {
                            break;
                        }
                        value = String.join(",", modules);
                    }
                    programOptions.add(opt);
                    programOptions.add(value);
                    var javacOption = Option.lookup(opt);
                    if (javacOption != null && javacOption.isInBasicOptionGroup()) {
                        subsequentOptions.add(opt);
                        subsequentOptions.add(value);
                    }
                }
                case "--enable-preview" -> {
                    programOptions.add(opt);
                    subsequentOptions.add(opt);
                    if (sourceOpt == null) {
                        throw new Fault(Errors.EnablePreviewRequiresSource);
                    }
                }
                default -> {
                    if (opt.startsWith("-agentlib:jdwp=") || opt.startsWith("-Xrunjdwp:")) {
                        programOptions.add("-g");
                        subsequentOptions.add("-g");
                    }
                }
                // ignore all other runtime args
            }
        }

        // add implicit options to both lists
        var implicitOptions = """
                -proc:none
                -implicit:none
                -Xprefer:source
                -Xdiags:verbose
                -Xlint:deprecation
                -Xlint:unchecked
                -Xlint:-options
                -XDsourceLauncher
                """;
        implicitOptions.lines()
                .filter(line -> !line.isBlank())
                .forEach(option -> {
                    programOptions.add(option);
                    subsequentOptions.add(option);
                });

        return new RelevantJavacOptions(List.copyOf(programOptions), List.copyOf(subsequentOptions));
    }

    private static List<String> computeListOfAddModules(ProgramDescriptor program, String value) {
        var modules = new ArrayList<>(List.of(value.split(",")));
        // these options are only supported at run time;
        // they are not required or supported at compile time
        modules.remove("ALL-DEFAULT");
        modules.remove("ALL-SYSTEM");

        // ALL-MODULE-PATH can only be used when compiling the
        // unnamed module or when compiling in the context of
        // an automatic module
        if (program.isModular()) {
            modules.remove("ALL-MODULE-PATH");
        }
        return modules;
    }
}
