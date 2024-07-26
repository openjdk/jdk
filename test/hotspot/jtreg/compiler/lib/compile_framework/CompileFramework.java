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

import compiler.lib.compile_framework.SourceCode;
import compiler.lib.compile_framework.CompileFrameworkException;
import compiler.lib.compile_framework.InternalCompileFrameworkException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import jdk.test.lib.process.ProcessTools;

public class CompileFramework {
    static final int JASM_COMPILE_TIMEOUT = 60;

    private List<SourceCode> sourceCodes = new ArrayList<SourceCode>();
    private URLClassLoader classLoader;

    public void add(SourceCode sourceCode) {
        sourceCodes.add(sourceCode);
    }

    public void printSourceCodes() {
        for (SourceCode sourceCode : sourceCodes) {
            System.out.println("SourceCode: " + sourceCode.className + " " + sourceCode.kind.name().toLowerCase());
            System.out.println(sourceCode.code);
        }
    }

    public void compile() {
        if (classLoader != null) {
            throw new RuntimeException("Cannot compile twice!");
        }

        printSourceCodes();

        List<SourceCode> javaSources = new ArrayList<SourceCode>();
        List<SourceCode> jasmSources = new ArrayList<SourceCode>();
        for (SourceCode sourceCode : sourceCodes) {
            switch (sourceCode.kind) {
                case SourceCode.Kind.JASM -> { jasmSources.add(sourceCode);  }
                case SourceCode.Kind.JAVA -> { javaSources.add(sourceCode);  }
            }
        }

        String sourceDir = getSourceDirName();
        compileJasmSources(sourceDir, jasmSources);
        compileJavaSources(sourceDir, javaSources);
        setUpClassLoader();
    }

    private static String getSourceDirName() {
        // Create temporary directory for jasm source files
        final String sourceDir;
        try {
            sourceDir = "compile-framework-sources-" + ProcessTools.getProcessId();
        } catch (Exception e) {
            throw new InternalCompileFrameworkException("Could not get ProcessID", e);
        }
        System.out.println("Source directory: " + sourceDir);
        return sourceDir;
    }

    private static void compileJasmSources(String sourceDir, List<SourceCode> jasmSources) {
        if (jasmSources.size() == 0) {
            System.out.println("No jasm sources to compile.");
            return;
        }
        System.out.println("Compiling jasm sources: " + jasmSources.size());

        List<String> jasmFileNames = writeSourcesToFile(sourceDir, jasmSources);
        compileJasmFiles(jasmFileNames);
        System.out.println("Jasm sources compiled.");
    }

    private static void compileJasmFiles(List<String> fileNames) {
        // Compile JASM files with asmtools.jar, shipped with jtreg.
        List<String> command = new ArrayList<>();

        command.add("%s/bin/java".formatted(System.getProperty("compile.jdk")));
        command.add("-classpath");
        command.add(getAsmToolsPath());
        command.add("org.openjdk.asmtools.jasm.Main");
        command.add("-d");
        command.add(System.getProperty("test.classes"));
        for (String fileName : fileNames) {
            command.add(new File(fileName).getAbsolutePath());
        }

        executeCompileCommand(command);
    }

    private static String[] getClassPaths() {
        String separator = File.pathSeparator;
        return System.getProperty("java.class.path").split(separator);
    }

    private static String getAsmToolsPath() {
        for (String path : getClassPaths()) {
            System.out.println("jtreg.jar?: " + path);
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

    private static void compileJavaSources(String sourceDir, List<SourceCode> javaSources) {
        if (javaSources.size() == 0) {
            System.out.println("No java sources to compile.");
            return;
        }
        System.out.println("Compiling Java sources: " + javaSources.size());

        List<String> javaFileNames = writeSourcesToFile(sourceDir, javaSources);
        compileJavaFiles(javaFileNames);

        System.out.println("Java sources compiled.");
    }

    private static void compileJavaFiles(List<String> javaFileNames) {
        // Compile JAVA files with javac, in the "compile.jdk".
        List<String> command = new ArrayList<>();

        command.add("%s/bin/javac".formatted(System.getProperty("compile.jdk")));
        command.add("-classpath");
        command.add(System.getProperty("java.class.path"));
        command.add("-d");
        command.add(System.getProperty("test.classes"));
        for (String fileName : javaFileNames) {
            command.add(new File(fileName).getAbsolutePath());
        }

        executeCompileCommand(command);
    }

    private static List<String> writeSourcesToFile(String sourceDir, List<SourceCode> sources) {
        List<String> storedFiles = new ArrayList<String>();
        for (SourceCode sourceCode : sources) {
            String extension = sourceCode.kind.name().toLowerCase();
            String fileName = sourceDir + "/" + sourceCode.className.replace('.','/') + "." + extension;
            writeCodeToFile(sourceCode.code, fileName);
            storedFiles.add(fileName);
        }
        return storedFiles;
    }

    private static void writeCodeToFile(String code, String fileName) {
        System.out.println("File: " + fileName);
        File file = new File(fileName);
        File dir = file.getAbsoluteFile().getParentFile();
        if (!dir.exists()){
            dir.mkdirs();
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(code);
        } catch (Exception e) {
            throw new CompileFrameworkException("Could not write file: " + fileName, e);
        }
    }

    private static void executeCompileCommand(List<String> command) {
        System.out.println("Compile command: " + String.join(" ", command));

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);

        String output;
        int exitCode;
        try {
            Process process = builder.start();
            boolean exited = process.waitFor(JASM_COMPILE_TIMEOUT, TimeUnit.SECONDS);
            if (!exited) {
                process.destroyForcibly();
                throw new InternalCompileFrameworkException("Process timeout: compilation took too long.");
            }
            output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            exitCode = process.exitValue();
        } catch (IOException e) {
            throw new InternalCompileFrameworkException("IOException during compilation", e);
        } catch (InterruptedException e) {
            throw new CompileFrameworkException("InterruptedException during compilation", e);
        }

        if (exitCode != 0 || !output.equals("")) {
            System.out.println("Compilation failed.");
            System.out.println("Exit code: " + exitCode);
            System.out.println("Output: '" + output + "'");
            throw new CompileFrameworkException("Compilation failed.");
        }
    }

    private void setUpClassLoader() {
        ClassLoader sysLoader = ClassLoader.getSystemClassLoader();

        try {
            // Classpath for all included classes (e.g. IR Framework).
            // Get all class paths, convert to urls.
            List<URL> urls = new ArrayList<URL>();
            for (String path : getClassPaths()) {
                urls.add(new File(path).toURI().toURL());
            }
            classLoader = URLClassLoader.newInstance(urls.toArray(URL[]::new), sysLoader);
        } catch (IOException e) {
            throw new CompileFrameworkException("IOException while creating ClassLoader", e);
        }
    }

    public Class getClass(String name) {
        try {
            return Class.forName(name, true, classLoader);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Class not found:", e);
        }
    }
}

