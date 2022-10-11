/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.spi.ToolProvider;

import toolbox.ToolBox;

/**
 * Utility methods for use by tests in the `Paths` directory.
 */
class Util {

    ToolBox tb = new ToolBox();
    PrintStream out = tb.out;

    Path javaHome = Path.of(System.getProperty("java.home"));
    String PS = File.pathSeparator;
    Path curDir = Path.of(".");

    static final String JAR = "jar";
    static final String JAVA = "java";
    static final String JAVAC = "javac";
    static final String JIMAGE = "jimage";

    /** The number of test-case failures. */
    int failCount = 0;
    /** The number of test-case passes. */
    int passCount = 0;
    /** A map recording how often each tool is executed in a separate process. */
    Map<String, Integer> execCounts = new TreeMap<>();
    /** A map recording how often each tool is invoked via its ToolProvider API. */
    Map<String, Integer> toolCounts = new TreeMap<>();

    /**
     * Reports a summary of the overall test statistics, and throws an exception
     * if any test cases failed.
     *
     * @throws Exception if any test cases failed
     */
    void bottomLine() throws Exception {
        out.println();
        out.println("-- Summary --");
        out.println("Passed: " + passCount);
        out.println("Failed: " + failCount);
        out.println("exec: " + execCounts);
        out.println("tool: " + toolCounts);

        if (failCount > 0) {
            throw new Exception(failCount + " tests failed");
        }
    }

    /**
     * The result of executing a tool, either in a separate process, or via its ToolProvider API.
     *
     * @param exitCode the exit code from the tool: 0 for success
     * @param out the output from the tool
     */
    record Result(int exitCode, String out) { }

    /**
     * Executes a tool with given arguments and verifies that it passes.
     *
     * @param command the name of a JDK tool: java, javac or jar
     * @param args a string containing whitespace separated arguments
     * @throws Exception if there was an issue trying to execute the tool
     * @see #passCount
     * @see #failCount
     * @see #splitArgs(String)
     */
    void expectPass(String command, String args) throws Exception {
        expectPass(null, null, command, splitArgs(args));
    }

    /**
     * Executes a tool in a specific directory with given arguments and verifies that it passes.
     * In order to set the directory, the tool will be executed in a separate process.
     *
     * @param dir the directory
     * @param command the name of a JDK tool: java, javac or jar
     * @param args a string containing whitespace separated arguments
     * @throws Exception if there was an issue trying to execute the tool
     * @see #passCount
     * @see #failCount
     * @see #splitArgs(String)
     */
    void expectPass(Path dir, String command, String args) throws Exception {
        expectPass(dir, null, command, splitArgs(args));
    }

    /**
     * Executes a tool with additional env variables with given arguments and verifies that it passes.
     * In order to set the env variables, the tool will be executed in a separate process.
     * Note that any value of {@code CLASSPATH} inherited from this process will always be removed.
     *
     * @param env the additional env variables
     * @param command the name of a JDK tool: java, javac or jar
     * @param args a string containing whitespace separated arguments
     * @throws Exception if there was an issue trying to execute the tool
     * @see #passCount
     * @see #failCount
     * @see #splitArgs(String)
     */
    void expectPass(Map<String, String> env, String command, String args) throws Exception {
        expectPass(null, env, command, splitArgs(args));
    }

    /**
     * Executes a tool in a given directory with additional env variables with given arguments
     * and verifies that it passes.
     * In order to set any directory and env variables, the tool will be executed in a separate process.
     * Note that any value of {@code CLASSPATH} inherited from this process will always be removed.
     *
     * @param dir the directory, or {@code null}
     * @param env the additional env variables, or {@code null}
     * @param command the name of a JDK tool: java, javac or jar
     * @param args the arguments
     * @throws Exception if there was an issue trying to execute the tool
     * @see #passCount
     * @see #failCount
     */
    void expectPass(Path dir, Map<String, String> env, String command, String... args) throws Exception {
        Result result = switch (command) {
            case JAR -> jar(args);
            case JAVAC -> javac(dir, env, args);
            case JAVA -> java(dir, env, args);
            default -> throw new Exception("unknown command: " + command);
        };

        if (result.exitCode == 0) {
            out.println("PASS: test passed as expected");
            passCount++;
        } else {
            out.println("FAIL: test failed unexpectedly");
            failCount++;
        }
    }

    /**
     * Executes a tool with given arguments and verifies that it fails.
     *
     * @param command the name of a JDK tool: java, javac or jar
     * @param args a string containing whitespace separated arguments
     * @throws Exception if there was an issue trying to execute the tool
     * @see #passCount
     * @see #failCount
     * @see #splitArgs(String)
     */
    void expectFail(String command, String args) throws Exception {
        expectFail(null, null, command, splitArgs(args));
    }

    /**
     * Executes a tool in a specific directory with given arguments and verifies that it fails.
     * In order to set the directory, the tool will be executed in a separate process.
     *
     * @param dir the directory
     * @param command the name of a JDK tool: java, javac or jar
     * @param args a string containing whitespace separated arguments
     * @throws Exception if there was an issue trying to execute the tool
     * @see #passCount
     * @see #failCount
     * @see #splitArgs(String)
     */
    void expectFail(Path dir, String command, String args) throws Exception {
        expectFail(dir, null, command, splitArgs(args));
    }

    /**
     * Executes a tool with additional env variables with given arguments and verifies that it passes.
     * In order to set the env variables, the tool will be executed in a separate process.
     * Note that any value of {@code CLASSPATH} inherited from this process will always be removed.
     *
     * @param env the additional env variables
     * @param command the name of a JDK tool: java, javac or jar
     * @param args a string containing whitespace separated arguments
     * @throws Exception if there was an issue trying to execute the tool
     * @see #passCount
     * @see #failCount
     * @see #splitArgs(String)
     */
    void expectFail(Map<String, String> env, String command, String args) throws Exception {
        expectFail(null, env, command, splitArgs(args));
    }

    /**
     * Executes a tool in a given directory with additional env variables with given arguments
     * and verifies that it passes.
     * In order to set any directory and env variables, the tool will be executed in a separate process.
     * Note that any value of {@code CLASSPATH} inherited from this process will always be removed.
     *
     * @param dir the directory, or {@code null}
     * @param env the additional env variables, or {@code null}
     * @param command the name of a JDK tool: java, javac or jar
     * @param args the arguments
     * @throws Exception if there was an issue trying to execute the tool
     * @see #passCount
     * @see #failCount
     */
    void expectFail(Path dir, Map<String, String> env, String command, String... args) throws Exception {
        Result result = switch (command) {
            case JAR -> jar(args);
            case JAVAC -> javac(dir, env, args);
            case JAVA -> java(dir, env, args);
            default -> throw new Exception("unknown command: " + command);
        };

        if (result.exitCode == 0) {
            out.println("FAIL: test passed unexpectedly");
            failCount++;
        } else {
            out.println("PASS: failed as expected");
            passCount++;
        }
    }

    /**
     * Splits a string into a list of strings that were separated by whitespace.
     * Leading and trailing whitespace is removed.
     * The character sequence {@code ${PS}} is replaced by the platform path separator.
     * Note, quotes are not supported, and so there is no support for embedded whitespace
     * or empty strings in the output.
     *
     * @param args a string of tokens separated by whitespace
     * @return an array of the tokens that were separated by whitespace
     */
    String[] splitArgs(String args) {
        return args.trim()
                .replace("${PS}", PS)
                .split("\\s+");
    }

    /**
     * Executes {@code javac} using its ToolProvider API.
     *
     * @param args the arguments
     * @return an object containing the output and exit code from the tool
     * @throws Exception if there is an issue executing the tool
     */
    Result javac(String... args) throws Exception {
        return runTool(JAVAC, args);
    }

    /**
     * Executes {@code javac} in either a separate process or using its ToolProvider API.
     * The ToolProvider API is used if the directory and env parameters are {@code null},
     * and if the arguments definitely do not use "classpath wildcards", which are
     * only supported when the tool is invoked by the launcher.
     *
     * @param dir the directory, or {@code null}
     * @param env any additional environment variables, or {@code null}
     * @param args the arguments
     * @return an object containing the output and exit code from the tool
     * @throws Exception if there is an issue executing the tool
     */
    Result javac(Path dir, Map<String, String> env, String... args) throws Exception {
        return (env != null || dir != null || hasWildcardClassPath(args))
                ? execTool(dir, env, JAVAC, args)
                : runTool(JAVAC, args);
    }

    /**
     * {@return true if the arguments may contain a classpath option using a "classpath wildcard"}
     *
     * The result is {@code true} if there is any form of a classpath option whose value contains {@code *}.
     * Note: this may include "false positives", where the {@code *} is not at the end of
     * any element in the path, such as when the character is part of the filename.
     * However, in context, the approximation is safe, and just means that we may sometimes
     * execute javac in a separate process when it would be sufficient to use its ToolProvider API.
     *
     * A more refined implementation could split apart the path elements and looking for
     * an element that is {@code *} or which ends in {@code *}.
     *
     * @param args the arguments to be checked
     */
    private boolean hasWildcardClassPath(String... args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-classpath", "--class-path", "-cp" -> {
                    if (i + 1 < args.length && args[i + 1].contains("*")) {
                        return true;
                    }
                }
                default -> {
                    if (arg.startsWith("--class-path=") && arg.contains("*")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Executes {@code jar} using its ToolProvider API.
     *
     * @param args the arguments
     * @return an object containing the output and exit code from the tool
     * @throws Exception if there is an issue executing the tool
     */
    Result jar(String... args) throws Exception {
        return runTool(JAR, args);
    }

    /**
     * Executes {@code jimage} using its ToolProvider API.
     *
     * @param args the arguments
     * @return an object containing the output and exit code from the tool
     * @throws Exception if there is an issue executing the tool
     */
    Result jimage(String... args) throws Exception {
        return execTool(null, null, JIMAGE, args);
    }

    /**
     * Executes {@code java} in a separate process.
     *
     * @param dir the directory, or {@code null}
     * @param env any additional environment variables, or {@code null}
     * @param args the arguments
     * @return an object containing the output and exit code from the launcher
     * @throws Exception if there is an issue executing the tool
     */
    Result java(Path dir, Map<String, String> env, String... args) throws Exception {
        return execTool(dir, env, JAVA, args);
    }

    /**
     * Runs a tool using its ToolProvider API.
     *
     * @param args the arguments
     * @return an object containing the output and exit code from the launcher
     * @throws Exception if there is an issue executing the tool
     */
    Result runTool(String name, String... args) throws Exception {
        out.println(name + ": " + String.join(" ", args));
        var tool = ToolProvider.findFirst(name)
                .orElseThrow(() -> new Exception("cannot find " + name));
        try (StringWriter sw = new StringWriter();
             PrintWriter pw = new PrintWriter(sw)) {
            int rc = tool.run(pw, pw, args);
            pw.flush();
            String output = sw.toString();
            output.lines()
                    .forEach(l -> out.println(name + ": " + l));
            if (rc != 0) {
                out.println(name + ": exit code " + rc);
            }
            toolCounts.put(name, toolCounts.computeIfAbsent(name, n -> 0) + 1);
            return new Result(rc, output);
        }
    }

    /**
     * Executes a tool in a separate process.
     *
     * Note that any value of {@code CLASSPATH} inherited from this process will always be removed.
     *
     * @param dir the directory, or {@code null}
     * @param env any additional environment variables, or {@code null}
     * @param args the arguments
     * @return an object containing the output and exit code from the launcher
     * @throws Exception if there is an issue executing the tool
     */
    Result execTool(Path dir, Map<String, String> env, String name, String... args) throws Exception {
        out.print(name + ":");
        if (env != null) {
            out.print(" " + env);
        }
        if (dir != null) {
            out.print(" (" + dir + ")");
        }
        out.println(" " + String.join(" ", args));

        Path tool = javaHome.resolve("bin").resolve(name + (ToolBox.isWindows() ? ".exe" : ""));
        if (!Files.exists(tool)) {
            throw new Exception("cannot find " + name);
        }
        var cmd = new ArrayList<String>();
        cmd.add(tool.toString());
        cmd.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(cmd)
                .redirectErrorStream(true);
        pb.environment().remove("CLASSPATH"); // always remove default value set by jtreg
        if (env != null) {
            pb.environment().putAll(env);
        }
        if (dir != null) {
            pb.directory(dir.toFile());
        }
        Process p = pb.start();
        StringBuilder sb = new StringBuilder();
        try (var in = p.inputReader()) {
            in.lines().forEach(l -> {
                sb.append(l).append("\n");
                out.println(name + ": " + l);
            });
        }
        p.waitFor();
        int rc = p.exitValue();
        if (rc != 0) {
            out.println(name + ": exit code " + rc);
        }
        execCounts.put(name, execCounts.computeIfAbsent(name, n -> 0) + 1);
        return new Result(rc, sb.toString());
    }

    /**
     * Checks that a series of files exist and are readable.
     *
     * @param paths the files
     * @throws Exception if any of the files are not found or are not readable
     */
    void checkFiles(String... paths) throws Exception {
        for (String p : paths) {
            Path path = Path.of(p);
            if (!Files.isReadable(path) ) {
                throw new Exception("file not found: " + path);
            }
        }
    }

    /**
     * List the files in a directory that match a "glob" pattern.
     *
     * @param dir the directory
     * @param glob the pattern
     * @return the list of files
     * @throws IOException if there is a problem listing the contents of the directory
     */
    List<Path> listFiles(Path dir, String glob) throws IOException {
        var files = new ArrayList<Path>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, glob)) {
            for (Path p : ds) {
                files.add(p);
            }
        }
        return files;
    }

    /**
     * Deletes a series of files.
     * The files are deleted using {@link ToolBox#cleanDirectory(Path)} and
     * {@code ToolBox#deleteFiles}, which together try hard to delete the files,
     * even on Windows.
     *
     * @param paths the paths
     * @throws IOException if there is a problem deleting any of the files
     * @see #deleteFiles(List)
     */
    void deleteFiles(String... paths) throws IOException {
        deleteFiles(Arrays.stream(paths)
                        .map(Path::of)
                        .toList());
    }

    /**
     * Deletes a series of files.
     * The files are deleted using {@link ToolBox#cleanDirectory(Path)} and
     * {@code ToolBox#deleteFiles}, which together try hard to delete the files,
     * even on Windows.
     *
     * @param paths the paths
     * @throws IOException if there is a problem deleting any of the files
     */
    void deleteFiles(List<Path> paths) throws IOException {
        for (Path path : paths) {
            if (Files.exists(path)) {
                if (Files.isDirectory(path)) {
                    tb.cleanDirectory(path);
                }
                tb.deleteFiles(path);
            }
        }
    }

    /**
     * Moves a series of files into a given directory.
     *
     * @param files the files
     * @param dir the target directory
     * @throws IOException if there is a problem moving any of the files
     */
    void moveFiles(List<Path> files, Path dir) throws IOException {
        for (Path p : files) {
            tb.moveFile(p, dir);
        }
    }

    /**
     * Moves a series of files into a given directory.
     *
     * @param files the files
     * @param dir the target directory
     * @throws IOException if there is a problem moving any of the files
     */
    void moveFiles(List<String> files, String dir) throws IOException {
        for (String p : files) {
            tb.moveFile(p, dir);
        }
    }

    /**
     * {@return a map containing a setting for the {@code CLASSPATH} env variable}
     *
     * @param classpath the value for the env variable
     */
    Map<String, String> classpath(String classpath) {
        return Map.of("CLASSPATH", classpath.replace("${PS}", PS));
    }

    /**
     * Writes a file called {@code MANIFEST.MF} containing a given value for
     * the {@code Class-Path} entry.
     *
     * @param path the value for the {@code Class-Path} entry
     * @throws IOException if there is a problem writing the file
     */
    void makeManifestWithClassPath(String path) throws IOException {
        Files.writeString(Path.of("MANIFEST.MF"),
                "Manifest-Version: 1.0\n"
                    + "Class-Path: " + path + "\n");
    }

}
