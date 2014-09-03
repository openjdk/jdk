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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Utils class for compiling , creating jar file and executing a java command
 *
 * @author Raghu Nair
 */

public class JavaToolUtils {

    public static final long DEFAULT_WAIT_TIME = 10000;

    private JavaToolUtils() {
    }

    /**
     * Takes a list of files and compile these files into the working directory.
     *
     * @param files
     * @throws IOException
     */
    public static void compileFiles(List<File> files) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fileManager = compiler.
                getStandardFileManager(null, null, null)) {
            Iterable<? extends JavaFileObject> compilationUnit
                    = fileManager.getJavaFileObjectsFromFiles(files);
            compiler.getTask(null, fileManager, null, null, null,
                    compilationUnit).call();
        }
    }

    /**
     * Create a jar file using the list of files provided.
     *
     * @param jar
     * @param files
     * @throws IOException
     */
    public static void createJar(File jar, List<File> files)
            throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION,
                "1.0");
        try (JarOutputStream target = new JarOutputStream(
                new FileOutputStream(jar), manifest)) {
            for (File file : files) {
                add(file, target);
            }
        }
    }

    private static void add(File source, JarOutputStream target)
            throws IOException {
        Objects.requireNonNull(source, "source cannot be null");
        Objects.requireNonNull(target, "target cannot be null");
        // not tested against directories and from different path.
        String name = source.getName();
        if (source.isDirectory()) {
            if (!name.isEmpty()) {
                if (!name.endsWith("/")) {
                    name += "/";
                }
                JarEntry entry = new JarEntry(name);
                entry.setTime(source.lastModified());
                target.putNextEntry(entry);
                target.closeEntry();
            }
            for (File nestedFile : source.listFiles()) {
                add(nestedFile, target);
            }
            return;
        }
        System.out.println("Adding entry " + name);
        JarEntry entry = new JarEntry(name);
        entry.setTime(source.lastModified());
        target.putNextEntry(entry);
        Files.copy(source.toPath(), target);
        target.closeEntry();
    }

    /**
     * Runs java command with provided arguments. Caller should not pass java
     * command in the argument list.
     *
     * @param commands
     * @param waitTime time to wait for the command to exit in milli seconds
     * @return
     * @throws Exception
     */
    public static int runJava(List<String> commands,long waitTime)
            throws Exception {
        String java = System.getProperty("java.home") + "/bin/java";
        commands.add(0, java);
        String command = commands.toString().replace(",", " ");
        System.out.println("Executing the following command \n" + command);
        ProcessBuilder processBuilder = new ProcessBuilder(commands);
        final Process process = processBuilder.start();
        BufferedReader errorStream = new BufferedReader(
                new InputStreamReader(process.getErrorStream()));
        BufferedReader outStream = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
        String errorLine;
        StringBuilder errors = new StringBuilder();
        String outLines;
        while ((errorLine = errorStream.readLine()) != null) {
            errors.append(errorLine).append("\n");
        }
        while ((outLines = outStream.readLine()) != null) {
            System.out.println(outLines);
        }
        errorLine = errors.toString();
        System.err.println(errorLine);
        process.waitFor(waitTime, TimeUnit.MILLISECONDS);
        int exitStatus = process.exitValue();
        if (exitStatus != 0 && errorLine != null && errorLine.isEmpty()) {
            throw new RuntimeException(errorLine);
        }
        return exitStatus;
    }

    /**
     * Runs java command with provided arguments. Caller should not pass java
     * command in the argument list.
     *
     * @param commands
     * @return
     * @throws Exception
     */
    public static int runJava(List<String> commands) throws Exception {
        return runJava(commands, DEFAULT_WAIT_TIME);
    }

    /**
     * Run any command
     * @param commands
     * @return
     * @throws Exception
     */
    public static int runCommand(List<String> commands) throws Exception {
        String command = commands.toString().replace(",", " ");
        System.out.println("Executing the following command \n" + command);
        ProcessBuilder processBuilder = new ProcessBuilder(commands);
        final Process process = processBuilder.start();
        BufferedReader errorStream = new BufferedReader(
                new InputStreamReader(process.getErrorStream()));
        BufferedReader outStream = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
        String errorLine;
        StringBuilder errors = new StringBuilder();
        String outLines;
        while ((errorLine = errorStream.readLine()) != null) {
            errors.append(errorLine).append("\n");
        }
        while ((outLines = outStream.readLine()) != null) {
            System.out.println(outLines);
        }
        errorLine = errors.toString();
        System.err.println(errorLine);
        int exitStatus = process.exitValue();
        if (exitStatus != 0 && errorLine != null && errorLine.isEmpty()) {
            throw new RuntimeException(errorLine);
        }
        return exitStatus;
    }


}
