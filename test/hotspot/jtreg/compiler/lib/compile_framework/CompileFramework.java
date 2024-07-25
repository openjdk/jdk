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

import compiler.lib.compile_framework.SourceFile;
import compiler.lib.compile_framework.CompileFrameworkException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import jdk.test.lib.process.ProcessTools;

public class CompileFramework {
    static final int JASM_COMPILE_TIMEOUT = 60;

    private List<SourceFile> sourceFiles = new ArrayList<SourceFile>();
    private URLClassLoader classLoader;

    public void add(SourceFile sourceFile) {
        sourceFiles.add(sourceFile);
    }

    public void printSourceFiles() {
        for (SourceFile sourceFile : sourceFiles) {
            System.out.println("SourceFile: " + sourceFile.name + " " + sourceFile.kind.name().toLowerCase());
            System.out.println(sourceFile.code);
        }
    }

    public void compile() {
        if (classLoader != null) {
            throw new RuntimeException("Cannot compile twice!");
        }

        printSourceFiles();

        List<JavaSourceFromString> javaFiles = new ArrayList<JavaSourceFromString>();
        List<SourceFile> jasmFiles = new ArrayList<SourceFile>();
        for (SourceFile sourceFile : sourceFiles) {
            switch (sourceFile.kind) {
                case SourceFile.Kind.JAVA -> { javaFiles.add(new JavaSourceFromString(sourceFile.name, sourceFile.code)); }
                case SourceFile.Kind.JASM -> { jasmFiles.add(sourceFile);  }
            }
        }

        compileJasmFiles(jasmFiles);
        compileJavaFiles(javaFiles);
        setUpClassLoader();
    }

    private void compileJasmFiles(List<SourceFile> jasmFiles) {
        if (jasmFiles.size() == 0) {
            System.out.println("No jasm files to compile.");
            return;
        }
        System.out.println("Compiling jasm files: " + jasmFiles.size());

        // Create temporary directory for jasm source files
        final String jasmDirName;
        try {
            jasmDirName = "jasm-files-source-dir-" + ProcessTools.getProcessId();
        } catch (Exception e) {
            throw new CompileFrameworkException("Could not get ProcessID", e);
        }
        System.out.println("Jasm source files in: " + jasmDirName);

        for (SourceFile sourceFile : jasmFiles) {
            String fileName = jasmDirName + "/" + sourceFile.name.replace('.','/') + ".jasm";
            writeCodeToFile(sourceFile.code, fileName);
            compileJasmFile(fileName);
        }
    }

    private static void compileJasmFile(String fileName) {
        // Compile JASM files with asmtools.jar, shipped with jtreg.
        List<String> command = new ArrayList<>();

        command.add("%s/bin/java".formatted(System.getProperty("compile.jdk")));
        command.add("-classpath");
        command.add(getAsmToolsPath());
        command.add("org.openjdk.asmtools.jasm.Main");
        command.add("-d");
        command.add(System.getProperty("test.classes"));
        command.add(new File(fileName).getAbsolutePath());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);

        String output;
        int exitCode;
        try {
            Process process = builder.start();
            boolean exited = process.waitFor(JASM_COMPILE_TIMEOUT, TimeUnit.SECONDS);
            if (!exited) {
                process.destroyForcibly();
                throw new CompileFrameworkException("Process timeout: jasm compilation took too long.");
            }
            output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            exitCode = process.exitValue();
        } catch (IOException e) {
            throw new CompileFrameworkException("IOException during jasm compilation", e);
        } catch (InterruptedException e) {
            throw new CompileFrameworkException("InterruptedException during jasm compilation", e);
        }

        if (exitCode != 0 || !output.equals("")) {
            System.out.println("Jasm compilation failed.");
            System.out.println("Exit code: " + exitCode);
            System.out.println("Output: '" + output + "'");
            throw new CompileFrameworkException("Jasm compilation failed.");
        }
    }

    private static String getAsmToolsPath() {
        for (String path : System.getProperty("java.class.path").split(":")) {
            if (path.endsWith("jtreg.jar")) {
                File jtreg = new File(path);
                File dir = jtreg.getAbsoluteFile().getParentFile();
                File asmtools = new File(dir, "asmtools.jar");
                if (!asmtools.exists()) {
                    throw new CompileFrameworkException("Found jtreg.jar in classpath, but could not find asmtools.jar");
                }
                return asmtools.getAbsolutePath();
            }
        }
        throw new CompileFrameworkException("Could not find asmtools because could not find jtreg.jar in classpath");
    }

    private static void writeCodeToFile(String code, String fileName) {
        System.out.println("File: " + fileName);
        File file = new File(fileName);
        File dir = file.getAbsoluteFile().getParentFile();
        if (!dir.exists()){
            dir.mkdir();
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(code);
        } catch (Exception e) {
            throw new CompileFrameworkException("Could not write file: " + fileName, e);
        }
    }

    private void compileJavaFiles(List<JavaSourceFromString> javaFiles) {
        if (javaFiles.size() == 0) {
            System.out.println("No java files to compile.");
            return;
        }
        System.out.println("Compiling Java files: " + javaFiles.size());

        // Get compiler with diagnostics.
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();

        // Set classpath and compilation destination for new class files.
        List<String> optionList = new ArrayList<String>();
        optionList.add("-classpath");
        optionList.add(System.getProperty("java.class.path"));
        optionList.add("-d");
        optionList.add(System.getProperty("test.classes"));

        // Compile.
        CompilationTask task = compiler.getTask(null, null, diagnostics, optionList, null, javaFiles);
        boolean success = task.call();

        // Print diagnostics.
        for (Diagnostic diagnostic : diagnostics.getDiagnostics()) {
            System.out.println(diagnostic.getCode());
            System.out.println(diagnostic.getKind());
            System.out.println(diagnostic.getPosition());
            System.out.println(diagnostic.getStartPosition());
            System.out.println(diagnostic.getEndPosition());
            System.out.println(diagnostic.getSource());
            System.out.println(diagnostic.getMessage(null));
        }

        if (!success) {
            System.out.println("Compilation failed.");
            throw new CompileFrameworkException("Compilation failed.");
        }
        System.out.println("Java files compiled.");
    }

    private void setUpClassLoader() {
        ClassLoader sysLoader = ClassLoader.getSystemClassLoader();

        try {
            // Classpath for all included classes (e.g. IR Framework).
            URL[] urls = new URL[] { new File("").toURI().toURL(),
                                     new File(System.getProperty("test.classes")).toURI().toURL()};
            classLoader = URLClassLoader.newInstance(urls, sysLoader);
        } catch (IOException e) {
            throw new CompileFrameworkException("IOException while creating java files", e);
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

class JavaSourceFromString extends SimpleJavaFileObject {
    final String code;

    public JavaSourceFromString(String name, String code) {
        super(URI.create("string:///" + name.replace('.','/') + Kind.SOURCE.extension), Kind.SOURCE);
        this.code = code;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return code;
    }
}
