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

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.List;
import jdk.test.lib.JDKToolFinder;

/**
 * Helper class for compilation of Java and Jasm {@link SourceCode}.
 */
class Compile {
    private static final int COMPILE_TIMEOUT = 60;

    private static final String JAVA_PATH = JDKToolFinder.getJDKTool("java");
    private static final String JAVAC_PATH = JDKToolFinder.getJDKTool("javac");

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

        List<Path> javaFilePaths = writeSourcesToFiles(javaSources, sourceDir);
        compileJavaFiles(javaFilePaths, classesDir);
        Utils.printlnVerbose("Java sources compiled.");
    }

    /**
     * Compile a list of files (i.e. {@code paths}) using javac and store
     * them in {@code classesDir}.
     */
    private static void compileJavaFiles(List<Path> paths, Path classesDir) {
        List<String> command = new ArrayList<>();

        command.add(JAVAC_PATH);
        command.add("-classpath");
        // Note: the backslashes from windows paths must be escaped!
        command.add(Utils.getEscapedClassPathAndClassesDir(classesDir));
        command.add("-d");
        command.add(classesDir.toString());
        for (Path path : paths) {
            command.add(path.toAbsolutePath().toString());
        }

        executeCompileCommand(command);
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

        List<Path> jasmFilePaths = writeSourcesToFiles(jasmSources, sourceDir);
        compileJasmFiles(jasmFilePaths, classesDir);
        Utils.printlnVerbose("Jasm sources compiled.");
    }

    /**
     * Compile a list of files (i.e. {@code paths}) using asmtools jasm and store
     * them in {@code classesDir}.
     */
    private static void compileJasmFiles(List<Path> paths, Path classesDir) {
        List<String> command = new ArrayList<>();

        command.add(JAVA_PATH);
        command.add("-classpath");
        command.add(getAsmToolsPath());
        command.add("org.openjdk.asmtools.jasm.Main");
        command.add("-d");
        command.add(classesDir.toString());
        for (Path path : paths) {
            command.add(path.toAbsolutePath().toString());
        }

        executeCompileCommand(command);
    }

    /**
     * Get the path of asmtools, which is shipped with JTREG.
     */
    private static String getAsmToolsPath() {
        for (String path : Utils.getClassPaths()) {
            if (path.endsWith("jtreg.jar")) {
                File jtreg = new File(path);
                File dir = jtreg.getAbsoluteFile().getParentFile();
                File asmtools = new File(dir, "asmtools.jar");
                if (!asmtools.exists()) {
                    throw new InternalCompileFrameworkException("Found jtreg.jar in classpath, but could not find asmtools.jar");
                }
                return asmtools.getAbsolutePath();
            }
        }
        throw new InternalCompileFrameworkException("Could not find asmtools because could not find jtreg.jar in classpath");
    }

    private static void writeCodeToFile(String code, Path path) {
        Utils.printlnVerbose("File: " + path);

        // Ensure directory of the file exists.
        Path dir = path.getParent();
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new CompileFrameworkException("Could not create directory: " + dir, e);
        }

        // Write to file.
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write(code);
        } catch (Exception e) {
            throw new CompileFrameworkException("Could not write file: " + path, e);
        }
    }

    /**
     * Write each source in {@code sources} to a file inside {@code sourceDir}.
     */
    private static List<Path> writeSourcesToFiles(List<SourceCode> sources, Path sourceDir) {
        List<Path> storedFiles = new ArrayList<>();
        for (SourceCode sourceCode : sources) {
            Path path = sourceDir.resolve(sourceCode.filePathName());
            writeCodeToFile(sourceCode.code(), path);
            storedFiles.add(path);
        }
        return storedFiles;
    }

    /**
     * Execute a given compilation, given as a {@code command}.
     */
    private static void executeCompileCommand(List<String> command) {
        Utils.printlnVerbose("Compile command: " + String.join(" ", command));

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);

        String output;
        int exitCode;
        try {
            Process process = builder.start();
            boolean exited = process.waitFor(COMPILE_TIMEOUT, TimeUnit.SECONDS);
            if (!exited) {
                process.destroyForcibly();
                System.out.println("Timeout: compile command: " + String.join(" ", command));
                throw new InternalCompileFrameworkException("Process timeout: compilation took too long.");
            }
            output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            exitCode = process.exitValue();
        } catch (IOException e) {
            throw new InternalCompileFrameworkException("IOException during compilation", e);
        } catch (InterruptedException e) {
            throw new CompileFrameworkException("InterruptedException during compilation", e);
        }

        if (exitCode != 0 || !output.isEmpty()) {
            System.err.println("Compilation failed.");
            System.err.println("Exit code: " + exitCode);
            System.err.println("Output: '" + output + "'");
            throw new CompileFrameworkException("Compilation failed.");
        }
    }
}
