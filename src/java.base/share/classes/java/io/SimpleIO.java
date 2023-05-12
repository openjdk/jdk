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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class provides easy to use console and file I/O methods for simple applications.
 * <p>
 * The console I/O methods allow interaction with the user through the keyboard and console/screen.
 * The method {@link #input(String)} accepts input from the keyboard
 * while allowing the user to edit the input with arrows and backspaces, as well as
 * allowing the user to scroll through previously entered input. The methods {@link #inputInt(String)},
 * {@link #inputLong(String)}, {@link #inputFloat(String)} and {@link #inputDouble(String)}
 * can be used to input primitive values. The methods {@link #print(Object...)} and
 * {@link #printLine(Object...)} allow developers to display data directly to the console/screen.
 * For example:
 * {@snippet lang="java":
 * import static java.io.SimpleIO.*;
 *
 * public class Example1 {
 *     public static void main(String[] args) {
 *         String name = input("Enter name>> ");
 *         printLine("Hello", name);
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
 *              printLine("File not copied");
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

    static {
        if (System.getProperty("jdk.console", "").isEmpty()) {
            System.setProperty("jdk.console", "jdk.internal.le");
        }
    }
    /**
     * Return a string of characters from input after issuing a prompt. Unlike other
     * input methods, this method supports editing and navigation of the input, as well
     * as scrolling back through historic input. For example:
     * {@snippet lang="java":
     * var name = input("Name>> "); // @highlight substring="input"
     * printLine(name);
     * }
     * will interact with the console as:
     * {@snippet lang="text":
     * Name>> Jean
     * Jean
     * }
     *
     * @param prompt string contain prompt for input, may be the empty string
     * @return a string of characters read in from input or empty string if user aborts input
     * @throws NullPointerException if prompt is null
     */
    public static String input(String prompt) {
        Objects.requireNonNull(prompt, "prompt must not be null");
        String input = System.console().readLine(prompt);
        return input != null ? input : "";
    }

    /**
     * Return a space delimited token from input after issuing a prompt. The prompt
     * will repeat until a token is input.
     *
     * @param prompt string contain prompt for input, may be the empty string
     * @return a token entered by user
     * @throws NullPointerException if prompt is null
     */
    public static String inputNext(String prompt) {
        Objects.requireNonNull(prompt, "prompt must not be null");
        while (true) {
            String input = input(prompt);
            var scanner = scanner(input);

            if (scanner.hasNext()) {
                return scanner.next();
            }
        }
    }

    /**
     * Return an int from input after issuing a prompt. The prompt will repeat until
     * an int is input.
     *
     * @param prompt string contain prompt for input, may be the empty string
     * @return an int value entered by user
     * @throws NullPointerException if prompt is null
     */
    public static int inputInt(String prompt) {
        Objects.requireNonNull(prompt, "prompt must not be null");
        while (true) {
            String input = input(prompt);
            var scanner = scanner(input);

            if (scanner.hasNextInt()) {
                return scanner.nextInt();
            }
        }
    }

    /**
     * Return a long from input after issuing a prompt. The prompt will repeat until
     * a long is input.
     *
     * @param prompt string contain prompt for input, may be the empty string
     * @return a long value entered by user
     * @throws NullPointerException if prompt is null
     */
    public static long inputLong(String prompt) {
        Objects.requireNonNull(prompt, "prompt must not be null");
        while (true) {
            String input = input(prompt);
            var scanner = scanner(input);

            if (scanner.hasNextLong()) {
                return scanner.nextLong();
            }
        }
    }

    /**
     * Return a float from input after issuing a prompt. The prompt will repeat until
     * a float is input.
     *
     * @param prompt string contain prompt for input, may be the empty string
     * @return a float value entered by user
     * @throws NullPointerException if prompt is null
     */
    public static float inputFloat(String prompt) {
        Objects.requireNonNull(prompt, "prompt must not be null");
        while (true) {
            String input = input(prompt);
            var scanner = scanner(input);

            if (scanner.hasNextFloat()) {
                return scanner.nextFloat();
            }
        }
    }

    /**
     * Return a double from input after issuing a prompt. The prompt will repeat until
     * a double is input.
     *
     * @param prompt string contain prompt for input, may be the empty string
     * @return a double value entered by user
     * @throws NullPointerException if prompt is null
     */
    public static double inputDouble(String prompt) {
        Objects.requireNonNull(prompt, "prompt must not be null");
        while (true) {
            String input = input(prompt);
            var scanner = scanner(input);

            if (scanner.hasNextDouble()) {
                return scanner.nextDouble();
            }
        }
    }

    /**
     * Return a boolean from input after issuing a prompt. The prompt will repeat until
     * a boolean is input.
     *
     * @param prompt string contain prompt for input, may be the empty string
     * @return a boolean value entered by user
     * @throws NullPointerException if prompt is null
     */
    public static boolean inputBoolean(String prompt) {
        Objects.requireNonNull(prompt, "prompt must not be null");
        while (true) {
            String input = input(prompt);
            var scanner = scanner(input);

            if (scanner.hasNextBoolean()) {
                return scanner.nextBoolean();
            }
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
     * printLine("A", "B"); // @highlight substring="printLine"
     * printLine("C", "D"); // @highlight substring="printLine"
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
    public static void printLine(Object... values) {
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
     * @param filename  string representing the name or path of the file to be read
     * @return Content read from a file
     * @throws UncheckedIOException wrapping an IOException if an io error occurs.
     * @throws NullPointerException if filename is null
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
     * @throws NullPointerException if path is null
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
     * @param filename  string representing the name or path of the file to be read
     * @return list of lines read from a file
     * @throws UncheckedIOException wrapping an IOException if an io error occurs.
     * @throws NullPointerException if filename is null
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
     * @throws NullPointerException if path is null
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
     * The string is replaces the content of the file at {@code path}.
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
     * @param filename  string representing the name or path of the file to be written
     * @param content  string content of the file
     * @throws UncheckedIOException wrapping an IOException if an io error occurs.
     * @throws NullPointerException if filename or content is null
     */
    public static void write(String filename, String content) {
        Objects.requireNonNull(filename, "filename must not be null");
        Objects.requireNonNull(content, "content must not be null");
        write(Path.of(filename), content);
    }

    /**
     * The string is replaces the content of the file at {@code path}.
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
     * @throws NullPointerException if path or content is null
     */
    public static void write(Path path, String content) {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(content, "content must not be null");
        writeLines(path, content.lines().toList());
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
     * @param filename  string representing the name or path of the file to be written
     * @param lines list of lines to be written to the file
     * @throws UncheckedIOException wrapping an IOException if an io error occurs.
     * @throws NullPointerException if filename or lines is null
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
     * @throws NullPointerException if path or lines is null
     */
    public static void writeLines(Path path, List<String> lines) {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(lines, "lines must not be null");
        try {
            Files.write(path, lines);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * This method returns a simple string scanner for reading white space delimited
     * tokens as well as reading ints of various radices, longs also of various radices,
     * floats, doubles, booleans and lines delimited by line terminators.
     * For example;
     * {@snippet :
     * var input = input("Enter a name, age and weight>> ");
     * var scanner = scanner(input);
     * var name = scanner.next();
     * var age = scanner.nextInt();
     * var weight = scanner.nextFloat();
     * }
     * @param string string to be scanned
     * @return a {@link StringScanner} object
     * @throws NullPointerException if string is null
     */
    public static StringScanner scanner(String string) {
        Objects.requireNonNull(string, "string must not be null");
        return new StringScanner(string);
    }

    /**
     * This simple string scanner provides methods for reading white space delimited
     * tokens as well as reading ints of various radices, longs also of various radices,
     * floats, doubles, booleans and lines delimited by line terminators.
     * For example;
     * {@snippet :
     * var input = input("Enter a name, age and weight>> ");
     * var scanner = scanner(input);
     * var name = scanner.next();
     * var age = scanner.nextInt();
     * var weight = scanner.nextFloat();
     * }
     */
    public static class StringScanner {
        private final Scanner scanner;

        private StringScanner(String string) {
            this.scanner = new Scanner(string);
            this.scanner.useLocale(Locale.ROOT);
            this.scanner.useRadix(10);
        }

        /**
         * {@return true if and only if this scanner has another token}
         */
        public boolean hasNext() {
            return scanner.hasNext();
        }

        /**
         * Finds and returns the next complete token from this scanner.
         * A complete token is preceded and followed by input that matches
         * the delimiter pattern. This method may block while waiting for input
         * to scan, even if a previous invocation of {@link #hasNext} returned
         * {@code true}.
         *
         * @return the next token
         * @throws NoSuchElementException if no more tokens are available
         */
        public String next() {
            return scanner.next();
        }

        /**
         * Returns true if there is another line in the input of this scanner.
         *
         * @return true if there is a line separator in the remaining input
         * or if the input has other remaining characters
         */
        public boolean hasNextLine() {
            return scanner.hasNextLine();
        }

        /**
         * Advances this scanner past the current line and returns the input
         * that was skipped.
         * <p>
         * This method returns the rest of the current line, excluding any line
         * separator at the end. The position is set to the beginning of the next
         * line.
         *
         * @return the line that was skipped
         * @throws NoSuchElementException if no line was found
         */
        public String nextLine() {
            return scanner.nextLine();
        }

        /**
         * Returns true if the next token in this scanner's input can be
         * interpreted as a boolean value using a case insensitive pattern
         * created from the string "true|false".  The scanner does not
         * advance past the input that matched.
         *
         * @return true if and only if this scanner's next token is a valid
         *         boolean value
         */
        public boolean hasNextBoolean()  {
            return scanner.hasNextBoolean();
        }

        /**
         * Scans the next token of the input into a boolean value and returns
         * that value. This method will throw {@code InputMismatchException}
         * if the next token cannot be translated into a valid boolean value.
         * If the match is successful, the scanner advances past the input that
         * matched.
         *
         * @return the boolean scanned from the input
         * @throws InputMismatchException if the next token is not a valid boolean
         * @throws NoSuchElementException if input is exhausted
         */
        public boolean nextBoolean()  {
            return scanner.nextBoolean();
        }
        /**
         * Returns true if the next token in this scanner's input can be
         * interpreted as an int value in the default radix using the
         * {@link #nextInt} method. The scanner does not advance past any input.
         *
         * @return true if and only if this scanner's next token is a valid
         *         int value
         */
        public boolean hasNextInt() {
            return scanner.hasNextInt();
        }

        /**
         * Returns true if the next token in this scanner's input can be
         * interpreted as an int value in the specified radix using the
         * {@link #nextInt} method. The scanner does not advance past any input.
         *
         * <p>If the radix is less than {@link Character#MIN_RADIX Character.MIN_RADIX}
         * or greater than {@link Character#MAX_RADIX Character.MAX_RADIX}, then an
         * {@code IllegalArgumentException} is thrown.
         *
         * @param radix the radix used to interpret the token as an int value
         * @return true if and only if this scanner's next token is a valid
         *         int value
         * @throws IllegalArgumentException if the radix is out of range
         */
        public boolean hasNextInt(int radix) {
            return scanner.hasNextInt(radix);
        }

        /**
         * Scans the next token of the input as an {@code int}.
         *
         * <p> An invocation of this method of the form
         * {@code nextInt()} behaves in exactly the same way as the
         * invocation {@code nextInt(radix)}, where {@code radix}
         * is the default radix of this scanner.
         *
         * @return the {@code int} scanned from the input
         * @throws InputMismatchException
         *         if the next token does not match the <i>Integer</i>
         *         regular expression, or is out of range
         * @throws NoSuchElementException if input is exhausted
         */
        public int nextInt() {
            return scanner.nextInt();
        }

        /**
         * Scans the next token of the input as an {@code int}.
         * This method will throw {@code InputMismatchException}
         * if the next token cannot be translated into a valid int value as
         * described below. If the translation is successful, the scanner advances
         * past the input that matched.
         *
         * <p> If the next token matches the <a
         * href="#Integer-regex"><i>Integer</i></a> regular expression defined
         * above then the token is converted into an {@code int} value as if by
         * removing all locale specific prefixes, group separators, and locale
         * specific suffixes, then mapping non-ASCII digits into ASCII
         * digits via {@link Character#digit Character.digit}, prepending a
         * negative sign (-) if the locale specific negative prefixes and suffixes
         * were present, and passing the resulting string to
         * {@link Integer#parseInt(String, int) Integer.parseInt} with the
         * specified radix.
         *
         * <p>If the radix is less than {@link Character#MIN_RADIX Character.MIN_RADIX}
         * or greater than {@link Character#MAX_RADIX Character.MAX_RADIX}, then an
         * {@code IllegalArgumentException} is thrown.
         *
         * @param radix the radix used to interpret the token as an int value
         * @return the {@code int} scanned from the input
         * @throws InputMismatchException
         *         if the next token does not match the <i>Integer</i>
         *         regular expression, or is out of range
         * @throws NoSuchElementException if input is exhausted
         * @throws IllegalArgumentException if the radix is out of range
         */
        public int nextInt(int radix) {
            return scanner.nextInt(radix);
        }

        /**
         * Returns true if the next token in this scanner's input can be
         * interpreted as a long value in the default radix using the
         * {@link #nextLong} method. The scanner does not advance past any input.
         *
         * @return true if and only if this scanner's next token is a valid
         *         long value
         */
        public boolean hasNextLong() {
            return scanner.hasNextLong();
        }

        /**
         * Returns true if the next token in this scanner's input can be
         * interpreted as a long value in the specified radix using the
         * {@link #nextLong} method. The scanner does not advance past any input.
         *
         * <p>If the radix is less than {@link Character#MIN_RADIX Character.MIN_RADIX}
         * or greater than {@link Character#MAX_RADIX Character.MAX_RADIX}, then an
         * {@code IllegalArgumentException} is thrown.
         *
         * @param radix the radix used to interpret the token as a long value
         * @return true if and only if this scanner's next token is a valid
         *         long value
         * @throws IllegalArgumentException if the radix is out of range
         */
        public boolean hasNextLong(int radix) {
            return scanner.hasNextLong(radix);
        }

        /**
         * Scans the next token of the input as a {@code long}.
         *
         * <p> An invocation of this method of the form
         * {@code nextLong()} behaves in exactly the same way as the
         * invocation {@code nextLong(radix)}, where {@code radix}
         * is the default radix of this scanner.
         *
         * @return the {@code long} scanned from the input
         * @throws InputMismatchException
         *         if the next token does not match the <i>Integer</i>
         *         regular expression, or is out of range
         * @throws NoSuchElementException if input is exhausted
         */
        public long nextLong() {
            return scanner.nextLong();
        }

        /**
         * Scans the next token of the input as a {@code long}.
         * This method will throw {@code InputMismatchException}
         * if the next token cannot be translated into a valid long value as
         * described below. If the translation is successful, the scanner advances
         * past the input that matched.
         *
         * <p> If the next token matches the <a
         * href="#Integer-regex"><i>Integer</i></a> regular expression defined
         * above then the token is converted into a {@code long} value as if by
         * removing all locale specific prefixes, group separators, and locale
         * specific suffixes, then mapping non-ASCII digits into ASCII
         * digits via {@link Character#digit Character.digit}, prepending a
         * negative sign (-) if the locale specific negative prefixes and suffixes
         * were present, and passing the resulting string to
         * {@link Long#parseLong(String, int) Long.parseLong} with the
         * specified radix.
         *
         * <p>If the radix is less than {@link Character#MIN_RADIX Character.MIN_RADIX}
         * or greater than {@link Character#MAX_RADIX Character.MAX_RADIX}, then an
         * {@code IllegalArgumentException} is thrown.
         *
         * @param radix the radix used to interpret the token as an int value
         * @return the {@code long} scanned from the input
         * @throws InputMismatchException
         *         if the next token does not match the <i>Integer</i>
         *         regular expression, or is out of range
         * @throws NoSuchElementException if input is exhausted
         * @throws IllegalArgumentException if the radix is out of range
         */
        public long nextLong(int radix) {
            return scanner.nextLong();
        }

        /**
         * Returns true if the next token in this scanner's input can be
         * interpreted as a float value using the {@link #nextFloat}
         * method. The scanner does not advance past any input.
         *
         * @return true if and only if this scanner's next token is a valid
         *         float value
         */
        public boolean hasNextFloat() {
            return scanner.hasNextFloat();
        }

        /**
         * Scans the next token of the input as a {@code float}.
         * This method will throw {@code InputMismatchException}
         * if the next token cannot be translated into a valid float value as
         * described below. If the translation is successful, the scanner advances
         * past the input that matched.
         *
         * <p> If the next token matches the <a
         * href="#Float-regex"><i>Float</i></a> regular expression defined above
         * then the token is converted into a {@code float} value as if by
         * removing all locale specific prefixes, group separators, and locale
         * specific suffixes, then mapping non-ASCII digits into ASCII
         * digits via {@link Character#digit Character.digit}, prepending a
         * negative sign (-) if the locale specific negative prefixes and suffixes
         * were present, and passing the resulting string to
         * {@link Float#parseFloat Float.parseFloat}. If the token matches
         * the localized NaN or infinity strings, then either "Nan" or "Infinity"
         * is passed to {@link Float#parseFloat(String) Float.parseFloat} as
         * appropriate.
         *
         * @return the {@code float} scanned from the input
         * @throws InputMismatchException
         *         if the next token does not match the <i>Float</i>
         *         regular expression, or is out of range
         * @throws NoSuchElementException if input is exhausted
         */
        public float nextFloat() {
            return scanner.nextFloat();
        }

        /**
         * Returns true if the next token in this scanner's input can be
         * interpreted as a double value using the {@link #nextDouble}
         * method. The scanner does not advance past any input.
         *
         * @return true if and only if this scanner's next token is a valid
         *         double value
         */
        public boolean hasNextDouble() {
            return scanner.hasNextDouble();
        }

        /**
         * Scans the next token of the input as a {@code double}.
         * This method will throw {@code InputMismatchException}
         * if the next token cannot be translated into a valid double value.
         * If the translation is successful, the scanner advances past the input
         * that matched.
         *
         * <p> If the next token matches the <a
         * href="#Float-regex"><i>Float</i></a> regular expression defined above
         * then the token is converted into a {@code double} value as if by
         * removing all locale specific prefixes, group separators, and locale
         * specific suffixes, then mapping non-ASCII digits into ASCII
         * digits via {@link Character#digit Character.digit}, prepending a
         * negative sign (-) if the locale specific negative prefixes and suffixes
         * were present, and passing the resulting string to
         * {@link Double#parseDouble Double.parseDouble}. If the token matches
         * the localized NaN or infinity strings, then either "Nan" or "Infinity"
         * is passed to {@link Double#parseDouble(String) Double.parseDouble} as
         * appropriate.
         *
         * @return the {@code double} scanned from the input
         * @throws InputMismatchException
         *         if the next token does not match the <i>Float</i>
         *         regular expression, or is out of range
         * @throws NoSuchElementException if the input is exhausted
         */
        public double nextDouble() {
            return scanner.nextDouble();
        }
    }

}
