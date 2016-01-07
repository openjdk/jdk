/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilterOutputStream;
import java.io.FilterWriter;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.JavaFileManager.Location;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.api.JavacTool;

/**
 * Utility methods and classes for writing jtreg tests for
 * javac, javah, javap, and sjavac. (For javadoc support,
 * see JavadocTester.)
 *
 * <p>There is support for common file operations similar to
 * shell commands like cat, cp, diff, mv, rm, grep.
 *
 * <p>There is also support for invoking various tools, like
 * javac, javah, javap, jar, java and other JDK tools.
 *
 * <p><em>File separators</em>: for convenience, many operations accept strings
 * to represent filenames. On all platforms on which JDK is supported,
 * "/" is a legal filename component separator. In particular, even
 * on Windows, where the official file separator is "\", "/" is a legal
 * alternative. It is therefore recommended that any client code using
 * strings to specify filenames should use "/".
 *
 * @author Vicente Romero (original)
 * @author Jonathan Gibbons (revised)
 */
public class ToolBox {
    /** The platform line separator. */
    public static final String lineSeparator = System.getProperty("line.separator");
    /** The platform OS name. */
    public static final String osName = System.getProperty("os.name");

    /** The location of the class files for this test, or null if not set. */
    public static final String testClasses = System.getProperty("test.classes");
    /** The location of the source files for this test, or null if not set. */
    public static final String testSrc = System.getProperty("test.src");
    /** The location of the test JDK for this test, or null if not set. */
    public static final String testJDK = System.getProperty("test.jdk");

    /** The current directory. */
    public static final Path currDir = Paths.get(".");

    /** The stream used for logging output. */
    public PrintStream out = System.err;

    /**
     * Checks if the host OS is some version of Windows.
     * @return true if the host OS is some version of Windows
     */
    public boolean isWindows() {
        return osName.toLowerCase(Locale.ENGLISH).startsWith("windows");
    }

    /**
     * Splits a string around matches of the given regular expression.
     * If the string is empty, an empty list will be returned.
     * @param text the string to be split
     * @param sep  the delimiting regular expression
     * @return the strings between the separators
     */
    public List<String> split(String text, String sep) {
        if (text.isEmpty())
            return Collections.emptyList();
        return Arrays.asList(text.split(sep));
    }

    /**
     * Checks if two lists of strings are equal.
     * @param l1 the first list of strings to be compared
     * @param l2 the second list of strings to be compared
     * @throws Error if the lists are not equal
     */
    public void checkEqual(List<String> l1, List<String> l2) throws Error {
        if (!Objects.equals(l1, l2)) {
            // l1 and l2 cannot both be null
            if (l1 == null)
                throw new Error("comparison failed: l1 is null");
            if (l2 == null)
                throw new Error("comparison failed: l2 is null");
            // report first difference
            for (int i = 0; i < Math.min(l1.size(), l2.size()); i++) {
                String s1 = l1.get(i);
                String s2 = l1.get(i);
                if (!Objects.equals(s1, s2)) {
                    throw new Error("comparison failed, index " + i +
                            ", (" + s1 + ":" + s2 + ")");
                }
            }
            throw new Error("comparison failed: l1.size=" + l1.size() + ", l2.size=" + l2.size());
        }
    }

    /**
     * Filters a list of strings according to the given regular expression.
     * @param regex the regular expression
     * @param lines the strings to be filtered
     * @return the strings matching the regular expression
     */
    public List<String> grep(String regex, List<String> lines) {
        return grep(Pattern.compile(regex), lines);
    }

    /**
     * Filters a list of strings according to the given regular expression.
     * @param pattern the regular expression
     * @param lines the strings to be filtered
     * @return the strings matching the regular expression
     */
    public List<String> grep(Pattern pattern, List<String> lines) {
        return lines.stream()
                .filter(s -> pattern.matcher(s).find())
                .collect(Collectors.toList());
    }

    /**
     * Copies a file.
     * If the given destination exists and is a directory, the copy is created
     * in that directory.  Otherwise, the copy will be placed at the destination,
     * possibly overwriting any existing file.
     * <p>Similar to the shell "cp" command: {@code cp from to}.
     * @param from the file to be copied
     * @param to where to copy the file
     * @throws IOException if any error occurred while copying the file
     */
    public void copyFile(String from, String to) throws IOException {
        copyFile(Paths.get(from), Paths.get(to));
    }

    /**
     * Copies a file.
     * If the given destination exists and is a directory, the copy is created
     * in that directory.  Otherwise, the copy will be placed at the destination,
     * possibly overwriting any existing file.
     * <p>Similar to the shell "cp" command: {@code cp from to}.
     * @param from the file to be copied
     * @param to where to copy the file
     * @throws IOException if an error occurred while copying the file
     */
    public void copyFile(Path from, Path to) throws IOException {
        if (Files.isDirectory(to)) {
            to = to.resolve(from.getFileName());
        } else {
            Files.createDirectories(to.getParent());
        }
        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Creates one of more directories.
     * For each of the series of paths, a directory will be created,
     * including any necessary parent directories.
     * <p>Similar to the shell command: {@code mkdir -p paths}.
     * @param paths the directories to be created
     * @throws IOException if an error occurred while creating the directories
     */
    public void createDirectories(String... paths) throws IOException {
        if (paths.length == 0)
            throw new IllegalArgumentException("no directories specified");
        for (String p : paths)
            Files.createDirectories(Paths.get(p));
    }

    /**
     * Creates one or more directories.
     * For each of the series of paths, a directory will be created,
     * including any necessary parent directories.
     * <p>Similar to the shell command: {@code mkdir -p paths}.
     * @param paths the directories to be created
     * @throws IOException if an error occurred while creating the directories
     */
    public void createDirectories(Path... paths) throws IOException {
        if (paths.length == 0)
            throw new IllegalArgumentException("no directories specified");
        for (Path p : paths)
            Files.createDirectories(p);
    }

    /**
     * Deletes one or more files.
     * Any directories to be deleted must be empty.
     * <p>Similar to the shell command: {@code rm files}.
     * @param files the files to be deleted
     * @throws IOException if an error occurred while deleting the files
     */
    public void deleteFiles(String... files) throws IOException {
        if (files.length == 0)
            throw new IllegalArgumentException("no files specified");
        for (String file : files)
            Files.delete(Paths.get(file));
    }

    /**
     * Deletes all content of a directory (but not the directory itself).
     * @param root the directory to be cleaned
     */
    public void cleanDirectory(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            throw new IOException(root + " is not a directory");
        }
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes a) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                if (e != null) {
                    throw e;
                }
                if (!dir.equals(root)) {
                    Files.delete(dir);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Moves a file.
     * If the given destination exists and is a directory, the file will be moved
     * to that directory.  Otherwise, the file will be moved to the destination,
     * possibly overwriting any existing file.
     * <p>Similar to the shell "mv" command: {@code mv from to}.
     * @param from the file to be moved
     * @param to where to move the file
     * @throws IOException if an error occurred while moving the file
     */
    public void moveFile(String from, String to) throws IOException {
        moveFile(Paths.get(from), Paths.get(to));
    }

    /**
     * Moves a file.
     * If the given destination exists and is a directory, the file will be moved
     * to that directory.  Otherwise, the file will be moved to the destination,
     * possibly overwriting any existing file.
     * <p>Similar to the shell "mv" command: {@code mv from to}.
     * @param from the file to be moved
     * @param to where to move the file
     * @throws IOException if an error occurred while moving the file
     */
    public void moveFile(Path from, Path to) throws IOException {
        if (Files.isDirectory(to)) {
            to = to.resolve(from.getFileName());
        } else {
            Files.createDirectories(to.getParent());
        }
        Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Reads the lines of a file.
     * The file is read using the default character encoding.
     * @param path the file to be read
     * @return the lines of the file.
     * @throws IOException if an error occurred while reading the file
     */
    public List<String> readAllLines(String path) throws IOException {
        return readAllLines(path, null);
    }

    /**
     * Reads the lines of a file.
     * The file is read using the default character encoding.
     * @param path the file to be read
     * @return the lines of the file.
     * @throws IOException if an error occurred while reading the file
     */
    public List<String> readAllLines(Path path) throws IOException {
        return readAllLines(path, null);
    }

    /**
     * Reads the lines of a file using the given encoding.
     * @param path the file to be read
     * @param encoding the encoding to be used to read the file
     * @return the lines of the file.
     * @throws IOException if an error occurred while reading the file
     */
    public List<String> readAllLines(String path, String encoding) throws IOException {
        return readAllLines(Paths.get(path), encoding);
    }

    /**
     * Reads the lines of a file using the given encoding.
     * @param path the file to be read
     * @param encoding the encoding to be used to read the file
     * @return the lines of the file.
     * @throws IOException if an error occurred while reading the file
     */
    public List<String> readAllLines(Path path, String encoding) throws IOException {
        return Files.readAllLines(path, getCharset(encoding));
    }

    private Charset getCharset(String encoding) {
        return (encoding == null) ? Charset.defaultCharset() : Charset.forName(encoding);
    }

    /**
     * Writes a file containing the given content.
     * Any necessary directories for the file will be created.
     * @param path where to write the file
     * @param content the content for the file
     * @throws IOException if an error occurred while writing the file
     */
    public void writeFile(String path, String content) throws IOException {
        writeFile(Paths.get(path), content);
    }

    /**
     * Writes a file containing the given content.
     * Any necessary directories for the file will be created.
     * @param path where to write the file
     * @param content the content for the file
     * @throws IOException if an error occurred while writing the file
     */
    public void writeFile(Path path, String content) throws IOException {
        Path dir = path.getParent();
        if (dir != null)
            Files.createDirectories(dir);
        try (BufferedWriter w = Files.newBufferedWriter(path)) {
            w.write(content);
        }
    }

    /**
     * Writes one or more files containing Java source code.
     * For each file to be written, the filename will be inferred from the
     * given base directory, the package declaration (if present) and from the
     * the name of the first class, interface or enum declared in the file.
     * <p>For example, if the base directory is /my/dir/ and the content
     * contains "package p; class C { }", the file will be written to
     * /my/dir/p/C.java.
     * <p>Note: the content is analyzed using regular expressions;
     * errors can occur if any contents have initial comments that might trip
     * up the analysis.
     * @param dir the base directory
     * @param contents the contents of the files to be written
     * @throws IOException if an error occurred while writing any of the files.
     */
    public void writeJavaFiles(Path dir, String... contents) throws IOException {
        if (contents.length == 0)
            throw new IllegalArgumentException("no content specified for any files");
        for (String c : contents) {
            new JavaSource(c).write(dir);
        }
    }

    /**
     * Returns the path for the binary of a JDK tool within {@link testJDK}.
     * @param tool the name of the tool
     * @return the path of the tool
     */
    public Path getJDKTool(String tool) {
        return Paths.get(testJDK, "bin", tool);
    }

    /**
     * Returns a string representing the contents of an {@code Iterable} as a list.
     * @param <T> the type parameter of the {@code Iterable}
     * @param items the iterable
     * @return the string
     */
    <T> String toString(Iterable<T> items) {
        return StreamSupport.stream(items.spliterator(), false)
                .map(Objects::toString)
                .collect(Collectors.joining(",", "[", "]"));
    }

    /**
     * The supertype for tasks.
     * Complex operations are modelled by building and running a "Task" object.
     * Tasks are typically configured in a fluent series of calls.
     */
    public interface Task {
        /**
         * Returns the name of the task.
         * @return the name of the task
         */
        String name();

        /**
         * Executes the task as currently configured.
         * @return a Result object containing the results of running the task
         * @throws TaskError if the outcome of the task was not as expected
         */
        Result run() throws TaskError;
    }

    /**
     * Exception thrown by {@code Task.run} when the outcome is not as
     * expected.
     */
    public static class TaskError extends Error {
        /**
         * Creates a TaskError object with the given message.
         * @param message the message
         */
        public TaskError(String message) {
            super(message);
        }
    }

    /**
     * An enum to indicate the mode a task should use it is when executed.
     */
    public enum Mode {
        /**
         * The task should use the interface used by the command
         * line launcher for the task.
         * For example, for javac: com.sun.tools.javac.Main.compile
         */
        CMDLINE,
        /**
         * The task should use a publicly defined API for the task.
         * For example, for javac: javax.tools.JavaCompiler
         */
        API,
        /**
         * The task should use the standard launcher for the task.
         * For example, $JAVA_HOME/bin/javac
         */
        EXEC
    }

    /**
     * An enum to indicate the expected success or failure of executing a task.
     */
    public enum Expect {
        /** It is expected that the task will complete successfully. */
        SUCCESS,
        /** It is expected that the task will not complete successfully. */
        FAIL
    }

    /**
     * An enum to identify the streams that may be written by a {@code Task}.
     */
    public enum OutputKind {
        /** Identifies output written to {@code System.out} or {@code stdout}. */
        STDOUT,
        /** Identifies output written to {@code System.err} or {@code stderr}. */
        STDERR,
        /** Identifies output written to a stream provided directly to the task. */
        DIRECT
    };

    /**
     * The results from running a {@link Task}.
     * The results contain the exit code returned when the tool was invoked,
     * and a map containing the output written to any streams during the
     * execution of the tool.
     * All tools support "stdout" and "stderr".
     * Tools that take an explicit PrintWriter save output written to that
     * stream as "main".
     */
    public class Result {

        final Task task;
        final int exitCode;
        final Map<OutputKind, String> outputMap;

        Result(Task task, int exitCode, Map<OutputKind, String> outputMap) {
            this.task = task;
            this.exitCode = exitCode;
            this.outputMap = outputMap;
        }

        /**
         * Returns the content of a specified stream.
         * @param outputKind the kind of the selected stream
         * @return the content that was written to that stream when the tool
         *  was executed.
         */
        public String getOutput(OutputKind outputKind) {
            return outputMap.get(outputKind);
        }

        /**
         * Returns the content of a named stream as a list of lines.
         * @param outputKind the kind of the selected stream
         * @return the content that was written to that stream when the tool
         *  was executed.
         */
        public List<String> getOutputLines(OutputKind outputKind) {
            return Arrays.asList(outputMap.get(outputKind).split(lineSeparator));
        }

        /**
         * Writes the content of the specified stream to the log.
         * @param kind the kind of the selected stream
         * @return this Result object
         */
        public Result write(OutputKind kind) {
            String text = getOutput(kind);
            if (text == null || text.isEmpty())
                out.println("[" + task.name() + ":" + kind + "]: empty");
            else {
                out.println("[" + task.name() + ":" + kind + "]:");
                out.print(text);
            }
            return this;
        }

        /**
         * Writes the content of all streams with any content to the log.
         * @return this Result object
         */
        public Result writeAll() {
            outputMap.forEach((name, text) -> {
                if (!text.isEmpty()) {
                    out.println("[" + name + "]:");
                    out.print(text);
                }
            });
            return this;
        }
    }

    /**
     * A utility base class to simplify the implementation of tasks.
     * Provides support for running the task in a process and for
     * capturing output written by the task to stdout, stderr and
     * other writers where applicable.
     * @param <T> the implementing subclass
     */
    protected static abstract class AbstractTask<T extends AbstractTask<T>> implements Task {
        protected final Mode mode;
        private final Map<OutputKind, String> redirects = new EnumMap<>(OutputKind.class);
        private final Map<String, String> envVars = new HashMap<>();
        private Expect expect = Expect.SUCCESS;
        int expectedExitCode = 0;

        /**
         * Create a task that will execute in the specified mode.
         * @param mode the mode
         */
        protected AbstractTask(Mode mode) {
            this.mode = mode;
        }

        /**
         * Sets the expected outcome of the task and calls {@code run()}.
         * @param expect the expected outcome
         * @return the result of calling {@code run()}
         */
        public Result run(Expect expect) {
            expect(expect, Integer.MIN_VALUE);
            return run();
        }

        /**
         * Sets the expected outcome of the task and calls {@code run()}.
         * @param expect the expected outcome
         * @param exitCode the expected exit code if the expected outcome
         *      is {@code FAIL}
         * @return the result of calling {@code run()}
         */
        public Result run(Expect expect, int exitCode) {
            expect(expect, exitCode);
            return run();
        }

        /**
         * Sets the expected outcome and expected exit code of the task.
         * The exit code will not be checked if the outcome is
         * {@code Expect.SUCCESS} or if the exit code is set to
         * {@code Integer.MIN_VALUE}.
         * @param expect the expected outcome
         * @param exitCode the expected exit code
         */
        protected void expect(Expect expect, int exitCode) {
            this.expect = expect;
            this.expectedExitCode = exitCode;
        }

        /**
         * Checks the exit code contained in a {@code Result} against the
         * expected outcome and exit value
         * @param result the result object
         * @return the result object
         * @throws TaskError if the exit code stored in the result object
         *      does not match the expected outcome and exit code.
         */
        protected Result checkExit(Result result) throws TaskError {
            switch (expect) {
                case SUCCESS:
                    if (result.exitCode != 0) {
                        result.writeAll();
                        throw new TaskError("Task " + name() + " failed: rc=" + result.exitCode);
                    }
                    break;

                case FAIL:
                    if (result.exitCode == 0) {
                        result.writeAll();
                        throw new TaskError("Task " + name() + " succeeded unexpectedly");
                    }

                    if (expectedExitCode != Integer.MIN_VALUE
                            && result.exitCode != expectedExitCode) {
                        result.writeAll();
                        throw new TaskError("Task " + name() + "failed with unexpected exit code "
                            + result.exitCode + ", expected " + expectedExitCode);
                    }
                    break;
            }
            return result;
        }

        /**
         * Sets an environment variable to be used by this task.
         * @param name the name of the environment variable
         * @param value the value for the environment variable
         * @return this task object
         * @throws IllegalStateException if the task mode is not {@code EXEC}
         */
        protected T envVar(String name, String value) {
            if (mode != Mode.EXEC)
                throw new IllegalStateException();
            envVars.put(name, value);
            return (T) this;
        }

        /**
         * Redirects output from an output stream to a file.
         * @param outputKind the name of the stream to be redirected.
         * @param path the file
         * @return this task object
         * @throws IllegalStateException if the task mode is not {@code EXEC}
         */
        protected T redirect(OutputKind outputKind, String path) {
            if (mode != Mode.EXEC)
                throw new IllegalStateException();
            redirects.put(outputKind, path);
            return (T) this;
        }

        /**
         * Returns a {@code ProcessBuilder} initialized with any
         * redirects and environment variables that have been set.
         * @return a {@code ProcessBuilder}
         */
        protected ProcessBuilder getProcessBuilder() {
            if (mode != Mode.EXEC)
                throw new IllegalStateException();
            ProcessBuilder pb = new ProcessBuilder();
            if (redirects.get(OutputKind.STDOUT) != null)
                pb.redirectOutput(new File(redirects.get(OutputKind.STDOUT)));
            if (redirects.get(OutputKind.STDERR) != null)
                pb.redirectError(new File(redirects.get(OutputKind.STDERR)));
            pb.environment().putAll(envVars);
            return pb;
        }

        /**
         * Collects the output from a process and saves it in a {@code Result}.
         * @param tb the {@code ToolBox} containing the task {@code t}
         * @param t the task initiating the process
         * @param p the process
         * @return a Result object containing the output from the process and its
         *      exit value.
         * @throws InterruptedException if the thread is interrupted
         */
        protected Result runProcess(ToolBox tb, Task t, Process p) throws InterruptedException {
            if (mode != Mode.EXEC)
                throw new IllegalStateException();
            ProcessOutput sysOut = new ProcessOutput(p.getInputStream()).start();
            ProcessOutput sysErr = new ProcessOutput(p.getErrorStream()).start();
            sysOut.waitUntilDone();
            sysErr.waitUntilDone();
            int rc = p.waitFor();
            Map<OutputKind, String> outputMap = new EnumMap<>(OutputKind.class);
            outputMap.put(OutputKind.STDOUT, sysOut.getOutput());
            outputMap.put(OutputKind.STDERR, sysErr.getOutput());
            return checkExit(tb.new Result(t, rc, outputMap));
        }

        /**
         * Thread-friendly class to read the output from a process until the stream
         * is exhausted.
         */
        static class ProcessOutput implements Runnable {
            ProcessOutput(InputStream from) {
                in = new BufferedReader(new InputStreamReader(from));
                out = new StringBuilder();
            }

            ProcessOutput start() {
                new Thread(this).start();
                return this;
            }

            @Override
            public void run() {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        out.append(line).append(lineSeparator);
                    }
                } catch (IOException e) {
                }
                synchronized (this) {
                    done = true;
                    notifyAll();
                }
            }

            synchronized void waitUntilDone() throws InterruptedException {
                boolean interrupted = false;

                // poll interrupted flag, while waiting for copy to complete
                while (!(interrupted = Thread.interrupted()) && !done)
                    wait(1000);

                if (interrupted)
                    throw new InterruptedException();
            }

            String getOutput() {
                return out.toString();
            }

            private BufferedReader in;
            private final StringBuilder out;
            private boolean done;
        }

        /**
         * Utility class to simplify the handling of temporarily setting a
         * new stream for System.out or System.err.
         */
        static class StreamOutput {
            // Functional interface to set a stream.
            // Expected use: System::setOut, System::setErr
            private interface Initializer {
                void set(PrintStream s);
            }

            private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            private final PrintStream ps = new PrintStream(baos);
            private final PrintStream prev;
            private final Initializer init;

            StreamOutput(PrintStream s, Initializer init) {
                prev = s;
                init.set(ps);
                this.init = init;
            }

            /**
             * Closes the stream and returns the contents that were written to it.
             * @return the contents that were written to it.
             */
            String close() {
                init.set(prev);
                ps.close();
                return baos.toString();
            }
        }

        /**
         * Utility class to simplify the handling of creating an in-memory PrintWriter.
         */
        static class WriterOutput {
            private final StringWriter sw = new StringWriter();
            final PrintWriter pw = new PrintWriter(sw);

            /**
             * Closes the stream and returns the contents that were written to it.
             * @return the contents that were written to it.
             */
            String close() {
                pw.close();
                return sw.toString();
            }
        }
    }

    /**
     * A task to configure and run the Java compiler, javac.
     */
    public class JavacTask extends AbstractTask<JavacTask> {
        private boolean includeStandardOptions;
        private List<Path> classpath;
        private List<Path> sourcepath;
        private Path outdir;
        private List<String> options;
        private List<String> classes;
        private List<String> files;
        private List<JavaFileObject> fileObjects;
        private JavaFileManager fileManager;

        private JavaCompiler compiler;
        private StandardJavaFileManager internalFileManager;

        /**
         * Creates a task to execute {@code javac} using API mode.
         */
        public JavacTask() {
            super(Mode.API);
        }

        /**
         * Creates a task to execute {@code javac} in a specified mode.
         * @param mode the mode to be used
         */
        public JavacTask(Mode mode) {
            super(mode);
        }

        /**
         * Sets the classpath.
         * @param classpath the classpath
         * @return this task object
         */
        public JavacTask classpath(String classpath) {
            this.classpath = Stream.of(classpath.split(File.pathSeparator))
                    .filter(s -> !s.isEmpty())
                    .map(s -> Paths.get(s))
                    .collect(Collectors.toList());
            return this;
        }

        /**
         * Sets the classpath.
         * @param classpath the classpath
         * @return this task object
         */
        public JavacTask classpath(Path... classpath) {
            this.classpath = Arrays.asList(classpath);
            return this;
        }

        /**
         * Sets the sourcepath.
         * @param sourcepath the sourcepath
         * @return this task object
         */
        public JavacTask sourcepath(String sourcepath) {
            this.sourcepath = Stream.of(sourcepath.split(File.pathSeparator))
                    .filter(s -> !s.isEmpty())
                    .map(s -> Paths.get(s))
                    .collect(Collectors.toList());
            return this;
        }

        /**
         * Sets the sourcepath.
         * @param classpath the sourcepath
         * @return this task object
         */
        public JavacTask sourcepath(Path... sourcepath) {
            this.sourcepath = Arrays.asList(sourcepath);
            return this;
        }

        /**
         * Sets the output directory.
         * @param outdir the output directory
         * @return this task object
         */
        public JavacTask outdir(String outdir) {
            this.outdir = Paths.get(outdir);
            return this;
        }

        /**
         * Sets the output directory.
         * @param outdir the output directory
         * @return this task object
         */
        public JavacTask outdir(Path outdir) {
            this.outdir = outdir;
            return this;
        }

        /**
         * Sets the options.
         * @param options the options
         * @return this task object
         */
        public JavacTask options(String... options) {
            this.options = Arrays.asList(options);
            return this;
        }

        /**
         * Sets the classes to be analyzed.
         * @param classes the classes
         * @return this task object
         */
        public JavacTask classes(String... classes) {
            this.classes = Arrays.asList(classes);
            return this;
        }

        /**
         * Sets the files to be compiled or analyzed.
         * @param files the files
         * @return this task object
         */
        public JavacTask files(String... files) {
            this.files = Arrays.asList(files);
            return this;
        }

        /**
         * Sets the files to be compiled or analyzed.
         * @param files the files
         * @return this task object
         */
        public JavacTask files(Path... files) {
            this.files = Stream.of(files)
                    .map(Path::toString)
                    .collect(Collectors.toList());
            return this;
        }

        /**
         * Sets the sources to be compiled or analyzed.
         * Each source string is converted into an in-memory object that
         * can be passed directly to the compiler.
         * @param sources the sources
         * @return this task object
         */
        public JavacTask sources(String... sources) {
            fileObjects = Stream.of(sources)
                    .map(s -> new JavaSource(s))
                    .collect(Collectors.toList());
            return this;
        }

        /**
         * Sets the file manager to be used by this task.
         * @param fileManager the file manager
         * @return this task object
         */
        public JavacTask fileManager(JavaFileManager fileManager) {
            this.fileManager = fileManager;
            return this;
        }

        /**
         * {@inheritDoc}
         * @return the name "javac"
         */
        @Override
        public String name() {
            return "javac";
        }

        /**
         * Calls the compiler with the arguments as currently configured.
         * @return a Result object indicating the outcome of the compilation
         * and the content of any output written to stdout, stderr, or the
         * main stream by the compiler.
         * @throws TaskError if the outcome of the task is not as expected.
         */
        @Override
        public Result run() {
            if (mode == Mode.EXEC)
                return runExec();

            WriterOutput direct = new WriterOutput();
            // The following are to catch output to System.out and System.err,
            // in case these are used instead of the primary (main) stream
            StreamOutput sysOut = new StreamOutput(System.out, System::setOut);
            StreamOutput sysErr = new StreamOutput(System.err, System::setErr);
            int rc;
            Map<OutputKind, String> outputMap = new HashMap<>();
            try {
                switch (mode == null ? Mode.API : mode) {
                    case API:
                        rc = runAPI(direct.pw);
                        break;
                    case CMDLINE:
                        rc = runCommand(direct.pw);
                        break;
                    default:
                        throw new IllegalStateException();
                }
            } catch (IOException e) {
                out.println("Exception occurred: " + e);
                rc = 99;
            } finally {
                outputMap.put(OutputKind.STDOUT, sysOut.close());
                outputMap.put(OutputKind.STDERR, sysErr.close());
                outputMap.put(OutputKind.DIRECT, direct.close());
            }
            return checkExit(new Result(this, rc, outputMap));
        }

        private int runAPI(PrintWriter pw) throws IOException {
            try {
//                if (compiler == null) {
                    // TODO: allow this to be set externally
//                    compiler = ToolProvider.getSystemJavaCompiler();
                    compiler = JavacTool.create();
//                }

                if (fileManager == null)
                    fileManager = internalFileManager = compiler.getStandardFileManager(null, null, null);
                if (outdir != null)
                    setLocationFromPaths(StandardLocation.CLASS_OUTPUT, Collections.singletonList(outdir));
                if (classpath != null)
                    setLocationFromPaths(StandardLocation.CLASS_PATH, classpath);
                if (sourcepath != null)
                    setLocationFromPaths(StandardLocation.SOURCE_PATH, sourcepath);
                List<String> allOpts = new ArrayList<>();
                if (options != null)
                    allOpts.addAll(options);

                Iterable<? extends JavaFileObject> allFiles = joinFiles(files, fileObjects);
                JavaCompiler.CompilationTask task = compiler.getTask(pw,
                        fileManager,
                        null,  // diagnostic listener; should optionally collect diags
                        allOpts,
                        classes,
                        allFiles);
                return ((JavacTaskImpl) task).doCall().exitCode;
            } finally {
                if (internalFileManager != null)
                    internalFileManager.close();
            }
        }

        private void setLocationFromPaths(StandardLocation location, List<Path> files) throws IOException {
            if (!(fileManager instanceof StandardJavaFileManager))
                throw new IllegalStateException("not a StandardJavaFileManager");
            ((StandardJavaFileManager) fileManager).setLocationFromPaths(location, files);
        }

        private int runCommand(PrintWriter pw) {
            List<String> args = getAllArgs();
            String[] argsArray = args.toArray(new String[args.size()]);
            return com.sun.tools.javac.Main.compile(argsArray, pw);
        }

        private Result runExec() {
            List<String> args = new ArrayList<>();
            Path javac = getJDKTool("javac");
            args.add(javac.toString());
            if (includeStandardOptions) {
                args.addAll(split(System.getProperty("test.tool.vm.opts"), " +"));
                args.addAll(split(System.getProperty("test.compiler.opts"), " +"));
            }
            args.addAll(getAllArgs());

            String[] argsArray = args.toArray(new String[args.size()]);
            ProcessBuilder pb = getProcessBuilder();
            pb.command(argsArray);
            try {
                return runProcess(ToolBox.this, this, pb.start());
            } catch (IOException | InterruptedException e) {
                throw new Error(e);
            }
        }

        private List<String> getAllArgs() {
            List<String> args = new ArrayList<>();
            if (options != null)
                args.addAll(options);
            if (outdir != null) {
                args.add("-d");
                args.add(outdir.toString());
            }
            if (classpath != null) {
                args.add("-classpath");
                args.add(toSearchPath(classpath));
            }
            if (sourcepath != null) {
                args.add("-sourcepath");
                args.add(toSearchPath(sourcepath));
            }
            if (classes != null)
                args.addAll(classes);
            if (files != null)
                args.addAll(files);

            return args;
        }

        private String toSearchPath(List<Path> files) {
            return files.stream()
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator));
        }

        private Iterable<? extends JavaFileObject> joinFiles(
                List<String> files, List<JavaFileObject> fileObjects) {
            if (files == null)
                return fileObjects;
            if (internalFileManager == null)
                internalFileManager = compiler.getStandardFileManager(null, null, null);
            Iterable<? extends JavaFileObject> filesAsFileObjects =
                    internalFileManager.getJavaFileObjectsFromStrings(files);
            if (fileObjects == null)
                return filesAsFileObjects;
            List<JavaFileObject> combinedList = new ArrayList<>();
            for (JavaFileObject o : filesAsFileObjects)
                combinedList.add(o);
            combinedList.addAll(fileObjects);
            return combinedList;
        }
    }

    /**
     * A task to configure and run the native header tool, javah.
     */
    public class JavahTask extends AbstractTask<JavahTask> {
        private String classpath;
        private List<String> options;
        private List<String> classes;

        /**
         * Create a task to execute {@code javah} using {@code CMDLINE} mode.
         */
        public JavahTask() {
            super(Mode.CMDLINE);
        }

        /**
         * Sets the classpath.
         * @param classpath the classpath
         * @return this task object
         */
        public JavahTask classpath(String classpath) {
            this.classpath = classpath;
            return this;
        }

        /**
         * Sets the options.
         * @param options the options
         * @return this task object
         */
        public JavahTask options(String... options) {
            this.options = Arrays.asList(options);
            return this;
        }

        /**
         * Sets the classes to be analyzed.
         * @param classes the classes
         * @return this task object
         */
        public JavahTask classes(String... classes) {
            this.classes = Arrays.asList(classes);
            return this;
        }

        /**
         * {@inheritDoc}
         * @return the name "javah"
         */
        @Override
        public String name() {
            return "javah";
        }

        /**
         * Calls the javah tool with the arguments as currently configured.
         * @return a Result object indicating the outcome of the task
         * and the content of any output written to stdout, stderr, or the
         * main stream provided to the task.
         * @throws TaskError if the outcome of the task is not as expected.
         */
        @Override
        public Result run() {
            List<String> args = new ArrayList<>();
            if (options != null)
                args.addAll(options);
            if (classpath != null) {
                args.add("-classpath");
                args.add(classpath);
            }
            if (classes != null)
                args.addAll(classes);

            WriterOutput direct = new WriterOutput();
            // These are to catch output to System.out and System.err,
            // in case these are used instead of the primary streams
            StreamOutput sysOut = new StreamOutput(System.out, System::setOut);
            StreamOutput sysErr = new StreamOutput(System.err, System::setErr);
            int rc;
            Map<OutputKind, String> outputMap = new HashMap<>();
            try {
                rc = com.sun.tools.javah.Main.run(args.toArray(new String[args.size()]), direct.pw);
            } finally {
                outputMap.put(OutputKind.STDOUT, sysOut.close());
                outputMap.put(OutputKind.STDERR, sysErr.close());
                outputMap.put(OutputKind.DIRECT, direct.close());
            }
            return checkExit(new Result(this, rc, outputMap));
        }
    }

    /**
     * A task to configure and run the disassembler tool, javap.
     */
    public class JavapTask extends AbstractTask<JavapTask> {
        private String classpath;
        private List<String> options;
        private List<String> classes;

        /**
         * Create a task to execute {@code javap} using {@code CMDLINE} mode.
         */
        public JavapTask() {
            super(Mode.CMDLINE);
        }

        /**
         * Sets the classpath.
         * @param classpath the classpath
         * @return this task object
         */
        public JavapTask classpath(String classpath) {
            this.classpath = classpath;
            return this;
        }

        /**
         * Sets the options.
         * @param options the options
         * @return this task object
         */
        public JavapTask options(String... options) {
            this.options = Arrays.asList(options);
            return this;
        }

        /**
         * Sets the classes to be analyzed.
         * @param classes the classes
         * @return this task object
         */
        public JavapTask classes(String... classes) {
            this.classes = Arrays.asList(classes);
            return this;
        }

        /**
         * {@inheritDoc}
         * @return the name "javap"
         */
        @Override
        public String name() {
            return "javap";
        }

        /**
         * Calls the javap tool with the arguments as currently configured.
         * @return a Result object indicating the outcome of the task
         * and the content of any output written to stdout, stderr, or the
         * main stream.
         * @throws TaskError if the outcome of the task is not as expected.
         */
        @Override
        public Result run() {
            List<String> args = new ArrayList<>();
            if (options != null)
                args.addAll(options);
            if (classpath != null) {
                args.add("-classpath");
                args.add(classpath);
            }
            if (classes != null)
                args.addAll(classes);

            WriterOutput direct = new WriterOutput();
            // These are to catch output to System.out and System.err,
            // in case these are used instead of the primary streams
            StreamOutput sysOut = new StreamOutput(System.out, System::setOut);
            StreamOutput sysErr = new StreamOutput(System.err, System::setErr);

            int rc;
            Map<OutputKind, String> outputMap = new HashMap<>();
            try {
                rc = com.sun.tools.javap.Main.run(args.toArray(new String[args.size()]), direct.pw);
            } finally {
                outputMap.put(OutputKind.STDOUT, sysOut.close());
                outputMap.put(OutputKind.STDERR, sysErr.close());
                outputMap.put(OutputKind.DIRECT, direct.close());
            }
            return checkExit(new Result(this, rc, outputMap));
        }
    }

    /**
     * A task to configure and run the jar file utility.
     */
    public class JarTask extends AbstractTask<JarTask> {
        private Path jar;
        private Manifest manifest;
        private String classpath;
        private String mainClass;
        private Path baseDir;
        private List<Path> paths;
        private Set<FileObject> fileObjects;

        /**
         * Creates a task to write jar files, using API mode.
         */
        public JarTask() {
            super(Mode.API);
            paths = Collections.emptyList();
            fileObjects = new LinkedHashSet<>();
        }

        /**
         * Creates a JarTask for use with a given jar file.
         * @param path the file
         */
        public JarTask(String path) {
            this();
            jar = Paths.get(path);
        }

        /**
         * Creates a JarTask for use with a given jar file.
         * @param path the file
         */
        public JarTask(Path path) {
            this();
            jar = path;
        }

        /**
         * Sets a manifest for the jar file.
         * @param manifest the manifest
         * @return this task object
         */
        public JarTask manifest(Manifest manifest) {
            this.manifest = manifest;
            return this;
        }

        /**
         * Sets a manifest for the jar file.
         * @param manifest a string containing the contents of the manifest
         * @return this task object
         * @throws IOException if there is a problem creating the manifest
         */
        public JarTask manifest(String manifest) throws IOException {
            this.manifest = new Manifest(new ByteArrayInputStream(manifest.getBytes()));
            return this;
        }

        /**
         * Sets the classpath to be written to the {@code Class-Path}
         * entry in the manifest.
         * @param classpath the classpath
         * @return this task object
         */
        public JarTask classpath(String classpath) {
            this.classpath = classpath;
            return this;
        }

        /**
         * Sets the class to be written to the {@code Main-Class}
         * entry in the manifest..
         * @param mainClass the name of the main class
         * @return this task object
         */
        public JarTask mainClass(String mainClass) {
            this.mainClass = mainClass;
            return this;
        }

        /**
         * Sets the base directory for files to be written into the jar file.
         * @param baseDir the base directory
         * @return this task object
         */
        public JarTask baseDir(String baseDir) {
            this.baseDir = Paths.get(baseDir);
            return this;
        }

        /**
         * Sets the base directory for files to be written into the jar file.
         * @param baseDir the base directory
         * @return this task object
         */
        public JarTask baseDir(Path baseDir) {
            this.baseDir = baseDir;
            return this;
        }

        /**
         * Sets the files to be written into the jar file.
         * @param files the files
         * @return this task object
         */
        public JarTask files(String... files) {
            this.paths = Stream.of(files)
                    .map(file -> Paths.get(file))
                    .collect(Collectors.toList());
            return this;
        }

        /**
         * Adds a set of file objects to be written into the jar file, by copying them
         * from a Location in a JavaFileManager.
         * The file objects to be written are specified by a series of paths;
         * each path can be in one of the following forms:
         * <ul>
         * <li>The name of a class. For example, java.lang.Object.
         * In this case, the corresponding .class file will be written to the jar file.
         * <li>the name of a package followed by {@code .*}. For example, {@code java.lang.*}.
         * In this case, all the class files in the specified package will be written to
         * the jar file.
         * <li>the name of a package followed by {@code .**}. For example, {@code java.lang.**}.
         * In this case, all the class files in the specified package, and any subpackages
         * will be written to the jar file.
         * </ul>
         *
         * @param fm the file manager in which to find the file objects
         * @param l  the location in which to find the file objects
         * @param paths the paths specifying the file objects to be copied
         * @return this task object
         * @throws IOException if errors occur while determining the set of file objects
         */
        public JarTask files(JavaFileManager fm, Location l, String... paths)
                throws IOException {
            for (String p : paths) {
                if (p.endsWith(".**"))
                    addPackage(fm, l, p.substring(0, p.length() - 3), true);
                else if (p.endsWith(".*"))
                    addPackage(fm, l, p.substring(0, p.length() - 2), false);
                else
                    addFile(fm, l, p);
            }
            return this;
        }

        private void addPackage(JavaFileManager fm, Location l, String pkg, boolean recurse)
                throws IOException {
            for (JavaFileObject fo : fm.list(l, pkg, EnumSet.allOf(JavaFileObject.Kind.class), recurse)) {
                fileObjects.add(fo);
            }
        }

        private void addFile(JavaFileManager fm, Location l, String path) throws IOException {
            JavaFileObject fo = fm.getJavaFileForInput(l, path, Kind.CLASS);
            fileObjects.add(fo);
        }

        /**
         * Provides limited jar command-like functionality.
         * The supported commands are:
         * <ul>
         * <li> jar cf jarfile -C dir files...
         * <li> jar cfm jarfile manifestfile -C dir files...
         * </ul>
         * Any values specified by other configuration methods will be ignored.
         * @param args arguments in the style of those for the jar command
         * @return a Result object containing the results of running the task
         */
        public Result run(String... args) {
            if (args.length < 2)
                throw new IllegalArgumentException();

            ListIterator<String> iter = Arrays.asList(args).listIterator();
            String first = iter.next();
            switch (first) {
                case "cf":
                    jar = Paths.get(iter.next());
                    break;
                case "cfm":
                    jar = Paths.get(iter.next());
                    try (InputStream in = Files.newInputStream(Paths.get(iter.next()))) {
                        manifest = new Manifest(in);
                    } catch (IOException e) {
                        throw new IOError(e);
                    }
                    break;
            }

            if (iter.hasNext()) {
                if (iter.next().equals("-C"))
                    baseDir = Paths.get(iter.next());
                else
                    iter.previous();
            }

            paths = new ArrayList<>();
            while (iter.hasNext())
                paths.add(Paths.get(iter.next()));

            return run();
        }

        /**
         * {@inheritDoc}
         * @return the name "jar"
         */
        @Override
        public String name() {
            return "jar";
        }

        /**
         * Creates a jar file with the arguments as currently configured.
         * @return a Result object indicating the outcome of the compilation
         * and the content of any output written to stdout, stderr, or the
         * main stream by the compiler.
         * @throws TaskError if the outcome of the task is not as expected.
         */
        @Override
        public Result run() {
            Manifest m = (manifest == null) ? new Manifest() : manifest;
            Attributes mainAttrs = m.getMainAttributes();
            if (mainClass != null)
                mainAttrs.put(Attributes.Name.MAIN_CLASS, mainClass);
            if (classpath != null)
                mainAttrs.put(Attributes.Name.CLASS_PATH, classpath);

            StreamOutput sysOut = new StreamOutput(System.out, System::setOut);
            StreamOutput sysErr = new StreamOutput(System.err, System::setErr);

            Map<OutputKind, String> outputMap = new HashMap<>();

            try (OutputStream os = Files.newOutputStream(jar);
                    JarOutputStream jos = openJar(os, m)) {
                writeFiles(jos);
                writeFileObjects(jos);
            } catch (IOException e) {
                error("Exception while opening " + jar, e);
            } finally {
                outputMap.put(OutputKind.STDOUT, sysOut.close());
                outputMap.put(OutputKind.STDERR, sysErr.close());
            }
            return checkExit(new Result(this, (errors == 0) ? 0 : 1, outputMap));
        }

        private JarOutputStream openJar(OutputStream os, Manifest m) throws IOException {
            if (m == null || m.getMainAttributes().isEmpty() && m.getEntries().isEmpty()) {
                return new JarOutputStream(os);
            } else {
                if (m.getMainAttributes().get(Attributes.Name.MANIFEST_VERSION) == null)
                    m.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
                return new JarOutputStream(os, m);
            }
        }

        private void writeFiles(JarOutputStream jos) throws IOException {
                Path base = (baseDir == null) ? currDir : baseDir;
                for (Path path : paths) {
                    Files.walkFileTree(base.resolve(path), new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            try {
                            String p = base.relativize(file)
                                    .normalize()
                                    .toString()
                                    .replace(File.separatorChar, '/');
                            JarEntry e = new JarEntry(p);
                                jos.putNextEntry(e);
                            try {
                                jos.write(Files.readAllBytes(file));
                            } finally {
                                jos.closeEntry();
                            }
                                return FileVisitResult.CONTINUE;
                            } catch (IOException e) {
                            error("Exception while adding " + file + " to jar file", e);
                                return FileVisitResult.TERMINATE;
                            }
                        }
                    });
                }
        }

        private void writeFileObjects(JarOutputStream jos) throws IOException {
            for (FileObject fo : fileObjects) {
                String p = guessPath(fo);
                JarEntry e = new JarEntry(p);
                jos.putNextEntry(e);
                try {
                    byte[] buf = new byte[1024];
                    try (BufferedInputStream in = new BufferedInputStream(fo.openInputStream())) {
                        int n;
                        while ((n = in.read(buf)) > 0)
                            jos.write(buf, 0, n);
                    } catch (IOException ex) {
                        error("Exception while adding " + fo.getName() + " to jar file", ex);
                    }
            } finally {
                    jos.closeEntry();
            }
            }
        }

        /*
         * A jar: URL is of the form  jar:URL!/<entry>  where URL is a URL for the .jar file itself.
         * In Symbol files (i.e. ct.sym) the underlying entry is prefixed META-INF/sym/<base>.
         */
        private final Pattern jarEntry = Pattern.compile(".*!/(?:META-INF/sym/[^/]+/)?(.*)");

        /*
         * A jrt: URL is of the form  jrt:/modules/<module>/<package>/<file>
         */
        private final Pattern jrtEntry = Pattern.compile("/modules/([^/]+)/(.*)");

        /*
         * A file: URL is of the form  file:/path/to/modules/<module>/<package>/<file>
         */
        private final Pattern fileEntry = Pattern.compile(".*/modules/([^/]+)/(.*)");

        private String guessPath(FileObject fo) {
            URI u = fo.toUri();
            switch (u.getScheme()) {
                case "jar": {
                    Matcher m = jarEntry.matcher(u.getSchemeSpecificPart());
                    if (m.matches()) {
                        return m.group(1);
                    }
                    break;
                }
                case "jrt": {
                    Matcher m = jrtEntry.matcher(u.getSchemeSpecificPart());
                    if (m.matches()) {
                        return m.group(2);
                    }
                    break;
                }
                case "file": {
                    Matcher m = fileEntry.matcher(u.getSchemeSpecificPart());
                    if (m.matches()) {
                        return m.group(2);
                    }
                    break;
                }
            }
            throw new IllegalArgumentException(fo.getName() + "--" + fo.toUri());
        }

        private void error(String message, Throwable t) {
            out.println("Error: " + message + ": " + t);
            errors++;
        }

        private int errors;
    }

    /**
     * A task to configure and run the Java launcher.
     */
    public class JavaTask extends AbstractTask<JavaTask> {
        boolean includeStandardOptions = true;
        private String classpath;
        private List<String> vmOptions;
        private String className;
        private List<String> classArgs;

        /**
         * Create a task to run the Java launcher, using {@code EXEC} mode.
         */
        public JavaTask() {
            super(Mode.EXEC);
        }

        /**
         * Sets the classpath.
         * @param classpath the classpath
         * @return this task object
         */
        public JavaTask classpath(String classpath) {
            this.classpath = classpath;
            return this;
        }

        /**
         * Sets the VM options.
         * @param vmOptions the options
         * @return this task object
         */
        public JavaTask vmOptions(String... vmOptions) {
            this.vmOptions = Arrays.asList(vmOptions);
            return this;
        }

        /**
         * Sets the name of the class to be executed.
         * @param className the name of the class
         * @return this task object
         */
        public JavaTask className(String className) {
            this.className = className;
            return this;
        }

        /**
         * Sets the arguments for the class to be executed.
         * @param classArgs the arguments
         * @return this task object
         */
        public JavaTask classArgs(String... classArgs) {
            this.classArgs = Arrays.asList(classArgs);
            return this;
        }

        /**
         * Sets whether or not the standard VM and java options for the test should be passed
         * to the new VM instance. If this method is not called, the default behavior is that
         * the options will be passed to the new VM instance.
         *
         * @param includeStandardOptions whether or not the standard VM and java options for
         *                               the test should be passed to the new VM instance.
         * @return this task object
         */
        public JavaTask includeStandardOptions(boolean includeStandardOptions) {
            this.includeStandardOptions = includeStandardOptions;
            return this;
        }

        /**
         * {@inheritDoc}
         * @return the name "java"
         */
        @Override
        public String name() {
            return "java";
        }

        /**
         * Calls the Java launcher with the arguments as currently configured.
         * @return a Result object indicating the outcome of the task
         * and the content of any output written to stdout or stderr.
         * @throws TaskError if the outcome of the task is not as expected.
         */
        @Override
        public Result run() {
            List<String> args = new ArrayList<>();
            args.add(getJDKTool("java").toString());
            if (includeStandardOptions) {
                args.addAll(split(System.getProperty("test.vm.opts"), " +"));
                args.addAll(split(System.getProperty("test.java.opts"), " +"));
            }
            if (classpath != null) {
                args.add("-classpath");
                args.add(classpath);
            }
            if (vmOptions != null)
                args.addAll(vmOptions);
            if (className != null)
                args.add(className);
            if (classArgs != null)
                args.addAll(classArgs);
            ProcessBuilder pb = getProcessBuilder();
            pb.command(args);
            try {
                return runProcess(ToolBox.this, this, pb.start());
            } catch (IOException | InterruptedException e) {
                throw new Error(e);
            }
        }
    }

    /**
     * A task to configure and run a general command.
     */
    public class ExecTask extends AbstractTask<ExecTask> {
        private final String command;
        private List<String> args;

        /**
         * Create a task to execute a given command, to be run using {@code EXEC} mode.
         * @param command the command to be executed
         */
        public ExecTask(String command) {
            super(Mode.EXEC);
            this.command = command;
        }

        /**
         * Create a task to execute a given command, to be run using {@code EXEC} mode.
         * @param command the command to be executed
         */
        public ExecTask(Path command) {
            super(Mode.EXEC);
            this.command = command.toString();
        }

        /**
         * Sets the arguments for the command to be executed
         * @param args the arguments
         * @return this task object
         */
        public ExecTask args(String... args) {
            this.args = Arrays.asList(args);
            return this;
        }

        /**
         * {@inheritDoc}
         * @return the name "exec"
         */
        @Override
        public String name() {
            return "exec";
        }

        /**
         * Calls the command with the arguments as currently configured.
         * @return a Result object indicating the outcome of the task
         * and the content of any output written to stdout or stderr.
         * @throws TaskError if the outcome of the task is not as expected.
         */
        @Override
        public Result run() {
            List<String> cmdArgs = new ArrayList<>();
            cmdArgs.add(command);
            if (args != null)
                cmdArgs.addAll(args);
            ProcessBuilder pb = getProcessBuilder();
            pb.command(cmdArgs);
            try {
                return runProcess(ToolBox.this, this, pb.start());
            } catch (IOException | InterruptedException e) {
                throw new Error(e);
            }
        }
    }

    /**
     * An in-memory Java source file.
     * It is able to extract the file name from simple source text using
     * regular expressions.
     */
    public static class JavaSource extends SimpleJavaFileObject {
        private final String source;

        /**
         * Creates a in-memory file object for Java source code.
         * @param className the name of the class
         * @param source the source text
         */
        public JavaSource(String className, String source) {
            super(URI.create(className), JavaFileObject.Kind.SOURCE);
            this.source = source;
        }

        /**
         * Creates a in-memory file object for Java source code.
         * The name of the class will be inferred from the source code.
         * @param source the source text
         */
        public JavaSource(String source) {
            super(URI.create(getJavaFileNameFromSource(source)),
                    JavaFileObject.Kind.SOURCE);
            this.source = source;
        }

        /**
         * Writes the source code to a file in the current directory.
         * @throws IOException if there is a problem writing the file
         */
        public void write() throws IOException {
            write(currDir);
        }

        /**
         * Writes the source code to a file in a specified directory.
         * @param dir the directory
         * @throws IOException if there is a problem writing the file
         */
        public void write(Path dir) throws IOException {
            Path file = dir.resolve(getJavaFileNameFromSource(source));
            Files.createDirectories(file.getParent());
            try (BufferedWriter out = Files.newBufferedWriter(file)) {
                out.write(source.replace("\n", lineSeparator));
            }
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }

        private static Pattern packagePattern =
                Pattern.compile("package\\s+(((?:\\w+\\.)*)(?:\\w+))");
        private static Pattern classPattern =
                Pattern.compile("(?:public\\s+)?(?:class|enum|interface)\\s+(\\w+)");

        /**
         * Extracts the Java file name from the class declaration.
         * This method is intended for simple files and uses regular expressions,
         * so comments matching the pattern can make the method fail.
         */
        static String getJavaFileNameFromSource(String source) {
            String packageName = null;

            Matcher matcher = packagePattern.matcher(source);
            if (matcher.find())
                packageName = matcher.group(1).replace(".", "/");

            matcher = classPattern.matcher(source);
            if (matcher.find()) {
                String className = matcher.group(1) + ".java";
                return (packageName == null) ? className : packageName + "/" + className;
            } else {
                throw new Error("Could not extract the java class " +
                        "name from the provided source");
            }
        }
    }

    /**
     * Extracts the Java file name from the class declaration.
     * This method is intended for simple files and uses regular expressions,
     * so comments matching the pattern can make the method fail.
     * @deprecated This is a legacy method for compatibility with ToolBox v1.
     *      Use {@link JavaSource#getName JavaSource.getName} instead.
     * @param source the source text
     * @return the Java file name inferred from the source
     */
    @Deprecated
    public static String getJavaFileNameFromSource(String source) {
        return JavaSource.getJavaFileNameFromSource(source);
    }

    /**
     * A memory file manager, for saving generated files in memory.
     * The file manager delegates to a separate file manager for listing and
     * reading input files.
     */
    public static class MemoryFileManager extends ForwardingJavaFileManager {
        private interface Content {
            byte[] getBytes();
            String getString();
        }

        /**
         * Maps binary class names to generated content.
         */
        final Map<Location, Map<String, Content>> files;

        /**
         * Construct a memory file manager which stores output files in memory,
         * and delegates to a default file manager for input files.
         */
        public MemoryFileManager() {
            this(JavacTool.create().getStandardFileManager(null, null, null));
        }

        /**
         * Construct a memory file manager which stores output files in memory,
         * and delegates to a specified file manager for input files.
         * @param fileManager the file manager to be used for input files
         */
        public MemoryFileManager(JavaFileManager fileManager) {
            super(fileManager);
            files = new HashMap<>();
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location,
                                                   String name,
                                                   JavaFileObject.Kind kind,
                                                   FileObject sibling)
        {
            return new MemoryFileObject(location, name, kind);
        }

        /**
         * Returns the content written to a file in a given location,
         * or null if no such file has been written.
         * @param location the location
         * @param name the name of the file
         * @return the content as an array of bytes
         */
        public byte[] getFileBytes(Location location, String name) {
            Content content = getFile(location, name);
            return (content == null) ? null : content.getBytes();
        }

        /**
         * Returns the content written to a file in a given location,
         * or null if no such file has been written.
         * @param location the location
         * @param name the name of the file
         * @return the content as a string
         */
        public String getFileString(Location location, String name) {
            Content content = getFile(location, name);
            return (content == null) ? null : content.getString();
        }

        private Content getFile(Location location, String name) {
            Map<String, Content> filesForLocation = files.get(location);
            return (filesForLocation == null) ? null : filesForLocation.get(name);
        }

        private void save(Location location, String name, Content content) {
            Map<String, Content> filesForLocation = files.get(location);
            if (filesForLocation == null)
                files.put(location, filesForLocation = new HashMap<>());
            filesForLocation.put(name, content);
        }

        /**
         * A writable file object stored in memory.
         */
        private class MemoryFileObject extends SimpleJavaFileObject {
            private final Location location;
            private final String name;

            /**
             * Constructs a memory file object.
             * @param name binary name of the class to be stored in this file object
             */
            MemoryFileObject(Location location, String name, JavaFileObject.Kind kind) {
                super(URI.create("mfm:///" + name.replace('.','/') + kind.extension),
                      Kind.CLASS);
                this.location = location;
                this.name = name;
            }

            @Override
            public OutputStream openOutputStream() {
                return new FilterOutputStream(new ByteArrayOutputStream()) {
                    @Override
                    public void close() throws IOException {
                        out.close();
                        byte[] bytes = ((ByteArrayOutputStream) out).toByteArray();
                        save(location, name, new Content() {
                            @Override
                            public byte[] getBytes() {
                                return bytes;
                            }
                            @Override
                            public String getString() {
                                return new String(bytes);
                            }

                        });
                    }
                };
            }

            @Override
            public Writer openWriter() {
                return new FilterWriter(new StringWriter()) {
                    @Override
                    public void close() throws IOException {
                        out.close();
                        String text = ((StringWriter) out).toString();
                        save(location, name, new Content() {
                            @Override
                            public byte[] getBytes() {
                                return text.getBytes();
                            }
                            @Override
                            public String getString() {
                                return text;
                            }

                        });
                    }
                };
            }
        }

    }

}
