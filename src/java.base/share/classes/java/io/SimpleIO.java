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

package java.io;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class provides easy to use console and file I/O methods for simple applications.
 * <p>
 * The console I/O methods allow interaction with the user through the keyboard and console/screen.
 * The methods {@link #input()} and {@link #input(String)} accept input from the keyboard
 * while allowing the user to edit the input with arrows and backspaces, as well as
 * allowing the user to scroll through previously entered input. The methods
 * {@link #print(Object...)} and {@link #println(Object...)} allow developers to display
 * data directly to the console/screen. For example:
 * {@snippet lang="java":
 * import static java.io.SimpleIO.*;
 *
 * public class Example1 {
 *     public static void main(String[] args) {
 *         String name = input("Enter name>> ");
 *         println("Hello", name);
 *     }
 * }
 * }
 * The file I/O method methods are designed to work with UTF-8 text files. The developer can
 * specify the file by either a simple file name string or by using a {@link Path}.
 * The developer also has the choice of working with the whole content as a string or
 * as a string list representing lines of content.
 * <p>
 * File I/O is futher simplifed by throwing {@link UncheckedIOException} for caught
 * {@link IOException IOExceptions}. This means that the developer does not require
 * wrapping simple I/O invocations in try blocks. For example:
 * {@snippet lang="java":
 * import static java.io.SimpleIO.*;
 *
 * public class Example2 {
 *     public static void main(String[] args) {
 *         String content = read("original.txt");
 *         write("copy.txt", content);
 *     }
 * }
 * }
 * If the developer chooses to handle exceptions they can add the try block to catch
 * the {@link UncheckedIOException}. For example:
 * {@snippet lang="java":
 * import static java.io.SimpleIO.*;
 *
 * public class Example3 {
 *     public static void main(String[] args) {
 *         try {
 *           String content = read("original.txt");
 *           write("copy.txt", content);
 *         } catch (UncheckedIOException ex) {
 *              println("File not copied");
 *         }
 *     }
 * }
 * }
 * @since  21
 */
public final class SimpleIO {
    /**
     * Private constructor.
     */
    private SimpleIO() {
        throw new AssertionError("private constructor");
    }

    /**
     * Fetch jline LineReader lazily.
     */
    private static class LineReader {
        /**
         * MethodHandle to LineReader::readLine.
         */
        private static final MethodHandle READ_LINE_MH;
        /**
         * MethodHandle to LineReader::readLine with mask.
         */
        private static final MethodHandle READ_LINE_MASK_MH;

        /**
         * Instance of LineReader.
         */
        private static final Object LINE_READER;

        static {
            MethodHandle readLineMH = null;
            MethodHandle readLineMaskMH = null;
            Object lineReader = null;

            try {
                Class<?> lrbClass = Class.forName("jdk.internal.org.jline.reader.LineReaderBuilder",
                        false, ClassLoader.getSystemClassLoader());
                Class<?> lrClass = Class.forName("jdk.internal.org.jline.reader.LineReader",
                        false, ClassLoader.getSystemClassLoader());
                Module lrbModule = lrbClass.getModule();
                Module baseModule = Object.class.getModule();
                baseModule.addReads(lrbModule);
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                MethodHandle builderMH = lookup.findStatic(lrbClass, "builder", MethodType.methodType(lrbClass));
                MethodHandle buildMH = lookup.findVirtual(lrbClass, "build", MethodType.methodType(lrClass));
                readLineMH = lookup.findVirtual(lrClass, "readLine",
                        MethodType.methodType(String.class, String.class));
                readLineMaskMH = lookup.findVirtual(lrClass, "readLine",
                        MethodType.methodType(String.class, String.class, Character.class));
                Object builder = builderMH.invoke();
                lineReader = buildMH.invoke(builder);
            } catch (Throwable ex) {
                readLineMH = null;
                readLineMaskMH = null;
                lineReader = null;
            }

            READ_LINE_MH = readLineMH;
            READ_LINE_MASK_MH = readLineMaskMH;
            LINE_READER = lineReader;
        }

        /**
         * {@return true if LineReader is available.}
         */
        private static boolean hasLineReader() {
            return LINE_READER != null;
        }

        /**
         * Invoke LineReader::readLine.
         *
         * @param prompt Read line prompt.
         * @return Line read in.
         */
        private static String readLine(String prompt) {
            try {
                return (String) READ_LINE_MH.invoke(LINE_READER, prompt);
            } catch (Throwable ex) {
                return null;
            }
        }

        /**
         * Invoke LineReader::readLine with mask.
         *
         * @param prompt Read line prompt.
         * @return Line read in.
         */
        private static String readLine(String prompt, Character mask) {
            try {
                return (String) READ_LINE_MASK_MH.invoke(LINE_READER, prompt, mask);
            } catch (Throwable ex) {
                return null;
            }
        }
    }

    /**
     * Return a string of characters from input. Unlike other input methods, this method
     * supports editing and navigation of the input, as well as scrolling back through
     * historic input. For example:
     * {@snippet lang="java":
     * print("Name>> ");
     * var name = input(); // @highlight substring="input"
     * println(name);
     * }
     * will interact with the console as:
     * {@snippet lang="text":
     * Name>> Jean
     * Jean
     * }
     *
     * @return a string of characters read in from input or null if the user aborts input
     */
    public static String input() {
        return input("");
    }

    /**
     * Return a string of characters from input after issuing a prompt. Unlike other
     * input methods, this method supports editing and navigation of the input, as well
     * as scrolling back through historic input. For example:
     * {@snippet lang="java":
     * var name = input("Name>> "); // @highlight substring="input"
     * println(name);
     * }
     * will interact with the console as:
     * {@snippet lang="text":
     * Name>> Jean
     * Jean
     * }
     *
     * @param prompt string contain prompt for input
     * @return a string of characters read in from input or null if the user aborts input
     * @throws NullPointerException if prompt is null
     */
    public static String input(String prompt) {
        Objects.requireNonNull(prompt, "prompt must not be null");

        if (LineReader.hasLineReader()) {
            String input = LineReader.readLine(prompt);
            return input != null ? input : "";
        } else {
            System.out.print(prompt);
            Scanner scanner = new Scanner(System.in);
            return scanner.hasNext() ? scanner.nextLine() : "";
        }
    }

    private static final Object[] NULL_ARRAY = new Object[1];

    /**
     * Single space print values to the output stream.
     * For example:
     * {@snippet lang="java":
     * print("A", "B"); // @highlight substring="print"
     * print("C", "D"); // @highlight substring="print"
     * }
     * will print on the output stream as:
     * {@snippet lang="text":
     * A BC D
     * }
     *
     * @param values values to be printed.
     */
    public static void print(Object... values) {
        System.out.print(Stream.of(values == null ? NULL_ARRAY : values)
                .map(String::valueOf)
                .collect(Collectors.joining(" ")));
    }

    /**
     * Single space print values to the output stream followed by the
     * platform line terminator.
     * For example:
     * {@snippet lang="java":
     * println("A", "B"); // @highlight substring="println"
     * println("C", "D"); // @highlight substring="println"
     * }
     * will print on the output stream as:
     * {@snippet lang="text":
     * A B
     * C D
     *
     * }
     *
     * @param values values to be printed.
     */
    public static void println(Object... values) {
        System.out.println(Stream.of(values == null ? NULL_ARRAY : values)
                .map(String::valueOf)
                .collect(Collectors.joining(" ")));
    }

    /**
     * Print list of lines to output stream. Each line will be printed and
     * followed by a line terminator.
     * For example:
     * {@snippet lang="java":
     * var fruit = List.of("apple", "pear", "orange");
     * printLines(fruit); // @highlight substring="printLines"
     * }
     * will print on the output stream as:
     * {@snippet lang="text":
     * apple
     * pear
     * orange
     *
     * }
     *
     * @param lines list of strings
     * @throws NullPointerException if lines is null
     */
    public static void printLines(List<String> lines) {
        Objects.requireNonNull(lines, "lines must not be null");
        lines.forEach(System.out::println);
    }

    /**
     * The contents of the file {@code filename} are read as a string.
     * For example:
     * {@snippet lang="java":
     * var text = """
     *    "Hope" is the thing with feathers
     *    That perches in the soul
     *    And sings the tune without the words
     *    And never stops - at all
     *    """;
     * write("data.txt", text);
     * var data = read("data.txt"); // @highlight substring="read"
     * print(data);
     * }
     * will print on the output stream as:
     * {@snippet lang="text":
     * "Hope" is the thing with feathers
     * That perches in the soul
     * And sings the tune without the words
     * And never stops - at all
     *
     * }
     *
     * @param filename  file name of file to be read
     * @return Content read from a file
     * @throws UncheckedIOException wrapping an IOException if an io error occurs.
     *
     * @implSpec Line terminators are normalized to '\n'.
     */
    public static String read(String filename) {
        Objects.requireNonNull(filename, "filename must not be null");
        return read(Path.of(filename));
    }

    /**
     * The contents of the file at {@code path} are read as a string.
     * For example:
     * {@snippet lang="java":
     * var text = """
     *    "Hope" is the thing with feathers
     *    That perches in the soul
     *    And sings the tune without the words
     *    And never stops - at all
     *    """;
     * var path = Path.of("data.txt");
     * write(path, text);
     * var data = read(path); // @highlight substring="read"
     * print(data);
     * }
     * will print on the output stream as:
     * {@snippet lang="text":
     * "Hope" is the thing with feathers
     * That perches in the soul
     * And sings the tune without the words
     * And never stops - at all
     *
     * }
     *
     * @param path  path of file to be read
     * @return Content read from a file
     * @throws UncheckedIOException wrapping an IOException if an io error occurs.
     *
     * @implSpec Line terminators are normalized to '\n'.
     */
    public static String read(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        return readLines(path).stream()
                .collect(Collectors.joining("\n", "", "\n"));
    }

    /**
     * The contents of the file {@code filename} are read as a list of strings.
     * For example:
     * {@snippet lang="java":
     * var fruit = List.of("apple", "pear", "orange");
     * var path = Path.of("data.txt");
     * writelines("data.txt", fruit);
     * var lines = readLines("data.txt"); // @highlight substring="readLines"
     * printlines(lines);
     * }
     * will print on the output stream as:
     * {@snippet lang="text":
     * apple
     * pear
     * orange
     *
     * }
     *
     * @param filename  file name of file to be read
     * @return list of lines read from a file
     * @throws UncheckedIOException wrapping an IOException if an io error occurs.
     *
     * @implNote The result represents the content split at line terminators.
     */
    public static List<String> readLines(String filename) {
        Objects.requireNonNull(filename, "filename must not be null");
        return readLines(Path.of(filename));
    }

    /**
     * The contents of the file at {@code path} are read as a list of strings.
     * For example:
     * {@snippet lang="java":
     * var path = Path.of("data.txt");
     * var supply = List.of("apple", "pear", "orange");
     * writelines(path, supply);
     * var lines = readLines(path); // @highlight substring="readLines"
     * printlines(lines);
     * }
     * will print on the output stream as:
     * {@snippet lang="text":
     * apple
     * pear
     * orange
     *
     * }
     *
     * @param path  path of file to be read
     * @return list of lines read from a file
     * @throws UncheckedIOException wrapping an IOException if an io error occurs.
     *
     * @implNote The result represents the content split at line terminators.
     */
    public static List<String> readLines(Path path) {
        Objects.requireNonNull(path, "filename must not be null");
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
    /**
     * The contents of the file at {@code path} are read as a string.
     * For example:
     * {@snippet lang="java":
     * var text = """
     *    "Hope" is the thing with feathers
     *    That perches in the soul
     *    And sings the tune without the words
     *    And never stops - at all
     *    """;
     * write("data.txt", text); // @highlight substring="write"
     * var data = read("data.txt");
     * print(data);
     * }
     * will print on the output stream as:
     * {@snippet lang="text":
     * "Hope" is the thing with feathers
     * That perches in the soul
     * And sings the tune without the words
     * And never stops - at all
     *
     * }
     *
     * @implSpec Line terminators are normalized the platform line separator.
     *
     * @param filename  file name string of file to be written
     * @param content  string content of the file
     * @throws UncheckedIOException wrapping an IOException if an io error occurs.
     */
    public static void write(String filename, String content) {
        Objects.requireNonNull(filename, "filename must not be null");
        Objects.requireNonNull(content, "content must not be null");
        write(Path.of(filename), content);
    }

    /**
     * The contents of the file at {@code path} are read as a string.
     * For example:
     * {@snippet lang="java":
     * var text = """
     *    "Hope" is the thing with feathers
     *    That perches in the soul
     *    And sings the tune without the words
     *    And never stops - at all
     *    """;
     * var path = Path.of("data.txt");
     * write(path, text); // @highlight substring="write"
     * var data = read(path);
     * print(data);
     * }
     * will print on the output stream as:
     * {@snippet lang="text":
     * "Hope" is the thing with feathers
     * That perches in the soul
     * And sings the tune without the words
     * And never stops - at all
     *
     * }
     *
     * @implSpec Line terminators are normalized the platform line separator.
     *
     * @param path  path of file to be written
     * @param content  string content of the file
     * @throws UncheckedIOException wrapping an IOException if an io error occurs.
     */
    public static void write(Path path, String content) {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(content, "content must not be null");
        try (PrintWriter out = new PrintWriter(path.toFile())) {
            content.lines().forEach(out::println);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Write a list of strings as the content of the file {@code filename}.
     * For example:
     * {@snippet lang="java":
     * var supply = List.of("apple", "pear", "orange");
     * writeLines("data.txt", supply); // @highlight substring="writeLines"
     * var lines = readLines("data.txt");
     * printlines(lines);
     * }
     * will print on the output stream as:
     * {@snippet lang="text":
     * apple
     * pear
     * orange
     *
     * }
     *
     * @implSpec Each line will be followed by the platform line separator.
     *
     * @param filename  file name string of file to be written
     * @param lines list of lines to be written to the file
     * @throws UncheckedIOException wrapping an IOException if an io error occurs.
     */
    public static void writeLines(String filename, List<String> lines) {
        Objects.requireNonNull(filename, "filename must not be null");
        Objects.requireNonNull(lines, "lines must not be null");
        writeLines(Path.of(filename), lines);
    }

    /**
     * Write a list of strings as the content of the file at {@code path}.
     * For example:
     * {@snippet lang="java":
     * var supply = List.of("apple", "pear", "orange");
     * var path = Path.of("data.txt");
     * writeLines(path, supply); // @highlight substring="writeLines"
     * var lines = readLines(path);
     * printlines(lines);
     * }
     * will print on the output stream as:
     * {@snippet lang="text":
     * apple
     * pear
     * orange
     *
     * }
     *
     * @implSpec Each line will be followed by the platform line separator.
     *
     * @param path  file name string of file to be written
     * @param lines  list of lines to be written to the file
     * @throws UncheckedIOException wrapping an IOException if an io error occurs.
     */
    public static void writeLines(Path path, List<String> lines) {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(lines, "lines must not be null");
        try (PrintWriter out = new PrintWriter(path.toFile())) {
            lines.forEach(out::println);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

}
