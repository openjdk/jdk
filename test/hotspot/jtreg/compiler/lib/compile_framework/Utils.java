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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.io.IOException;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class, with many helper methods for the Compile Framework.
 */
class Utils {
    private static final boolean VERBOSE = Boolean.getBoolean("CompileFrameworkVerbose");
    private static final int COMPILE_TIMEOUT = 60;

    /**
     * Verbose printing, enabled with {@code -DCompileFrameworkVerbose=true}.
     */
    public static void printlnVerbose(String s) {
        if (VERBOSE) {
            System.out.println(s);
        }
    }

    /**
     * Create a temporary directory with a unique name to avoid collisions
     * with multi-threading. Used to create the sources and classes directories. Since they
     * are unique even across threads, the Compile Framework is multi-threading safe, i.e.
     * it does not have collisions if two instances generate classes with the same name.
     */
    public static Path makeUniqueDir(String prefix) {
        try {
            return Files.createTempDirectory(Paths.get("."), prefix);
        } catch (Exception e) {
            throw new InternalCompileFrameworkException("Could not set up temporary directory", e);
        }
    }

    /**
     * Get all paths in the classpath.
     */
    public static String[] getClassPaths() {
        String separator = File.pathSeparator;
        return System.getProperty("java.class.path").split(separator);
    }

    /**
     * Return the classpath, appended with the {@code classesDir}.
     */
    public static String getEscapedClassPathAndClassesDir(Path classesDir) {
        String cp = System.getProperty("java.class.path") +
                    File.pathSeparator +
                    classesDir.toAbsolutePath();
        // Escape the backslash for Windows paths. We are using the path in the
        // command-line and Java code, so we always want it to be escaped.
        return cp.replace("\\", "\\\\");
    }

    /**
     * Get the path of asmtools, which is shipped with JTREG.
     */
    public static String getAsmToolsPath() {
        for (String path : getClassPaths()) {
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
        printlnVerbose("File: " + path);

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
     * Write sources to file.
     */
    public static List<Path> writeSourcesToFile(List<SourceCode> sources, Path sourceDir) {
        List<Path> storedFiles = new ArrayList<Path>();
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
    public static void executeCompileCommand(List<String> command) {
        printlnVerbose("Compile command: " + String.join(" ", command));

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
