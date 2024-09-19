/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package compiler.lib.compile_framework;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for compilation of Java and Jasm {@code SourceCode}.
 */
class Compile {

    /**
     * Compile all sources in {@code javaSources}. First write them to the {@code sourceDir},
     * then compile them to class-files which are stored in {@code classesDir}.
     */
    public static void compileJavaSources(List<SourceCode> javaSources, Path sourceDir, Path classesDir) {
        if (javaSources.isEmpty()) {
            Utils.printlnVerbose("No java sources to compile.");
            return;
        }
        Utils.printlnVerbose("Compiling Java sources: " + javaSources.size());

        List<Path> javaFilePaths = Utils.writeSourcesToFile(javaSources, sourceDir);
        compileJavaFiles(javaFilePaths, classesDir);
        Utils.printlnVerbose("Java sources compiled.");
    }

    /**
     * Compile a list of files (i.e. {@code paths}) using javac and store
     * them in {@code classesDir}.
     */
    private static void compileJavaFiles(List<Path> paths, Path classesDir) {
        List<String> command = new ArrayList<>();

        command.add("%s/bin/javac".formatted(System.getProperty("compile.jdk")));
        command.add("-classpath");
        // Note: the backslashes from windows paths must be escaped!
        command.add(Utils.getEscapedClassPathAndClassesDir(classesDir));
        command.add("-d");
        command.add(classesDir.toString());
        for (Path path : paths) {
            command.add(path.toAbsolutePath().toString());
        }

        Utils.executeCompileCommand(command);
    }

    /**
     * Compile all sources in {@code jasmSources}. First write them to the {@code sourceDir},
     * then compile them to class-files which are stored in {@code classesDir}.
     */
    public static void compileJasmSources(List<SourceCode> jasmSources, Path sourceDir, Path classesDir) {
        if (jasmSources.isEmpty()) {
            Utils.printlnVerbose("No jasm sources to compile.");
            return;
        }
        Utils.printlnVerbose("Compiling jasm sources: " + jasmSources.size());

        List<Path> jasmFilePaths = Utils.writeSourcesToFile(jasmSources, sourceDir);
        compileJasmFiles(jasmFilePaths, classesDir);
        Utils.printlnVerbose("Jasm sources compiled.");
    }

    /**
     * Compile a list of files (i.e. {@code paths}) using asmtools jasm and store
     * them in {@code classesDir}.
     */
    private static void compileJasmFiles(List<Path> paths, Path classesDir) {
        List<String> command = new ArrayList<>();

        command.add("%s/bin/java".formatted(System.getProperty("compile.jdk")));
        command.add("-classpath");
        command.add(Utils.getAsmToolsPath());
        command.add("org.openjdk.asmtools.jasm.Main");
        command.add("-d");
        command.add(classesDir.toString());
        for (Path path : paths) {
            command.add(path.toAbsolutePath().toString());
        }

        Utils.executeCompileCommand(command);
    }
}
