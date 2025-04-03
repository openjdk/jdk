/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOError;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import jdk.internal.javac.PreviewFeature;

/**
 * A collection of static methods that provide convenient access to {@link System#in}
 * and {@link System#out} for line-oriented input and output.
 * <p>
 * The {@link #readln()} and {@link #readln(String)} methods in this class use internal
 * objects that decode bytes read from {@code System.in} into characters. The charset used
 * for decoding is specified by the {@link System#getProperties stdin.encoding} property.
 * If this property is not present, or if the charset it names cannot be loaded, then
 * UTF-8 is used instead. These internal objects are created upon the first call to
 * either of the {@code readln} methods and are stored for subsequent reuse by these
 * methods.
 * <p>
 * The internal objects used by the {@code readln} methods may buffer additional bytes
 * beyond those that have been decoded to characters returned to the application. The
 * result of interleaving calls to the {@code readln} methods with other operations
 * on {@code System.in} is unspecified.
 *
 * @since 25
 */
@PreviewFeature(feature = PreviewFeature.Feature.IMPLICIT_CLASSES)
public final class IO {

    /*
     * We are deliberately not including printf, at least not initially, for
     * the following reasons. First, it introduces a rather cryptic and arcane
     * formatting language that isn't really suited to beginners. Second, it
     * is inherently localizable, which drags in a whole bunch of issues about
     * what locale should be used for formatting, the possible inclusion of
     * an overload with an explicit Locale parameter, and so forth. These issues
     * are best avoided for the time being. Third, when string templates come
     * along, they might offer a better alternative to printf-style formatting,
     * so it's best not be saddled with this unnecessarily.
     */

    /*
     * Notes on flushing. We want flushing to occur after every call to println
     * and print, so that the user can see output immediately. This could be
     * important if the user calls print() to issue a prompt before calling
     * readln() instead of the readln(prompt) overload. It's also important to
     * flush after print() in case the user is relying on print() to emit output
     * as sort of a progress indicator.
     *
     * We rely on System.out to have autoflush enabled, which flushes after every
     * println() call, so we needn't flush again. We flush unconditionally after
     * calls to print(). Since System.out is doing a lot of flushing anyway, there
     * isn't much point trying to make this conditional, for example, only if
     * stdout is connected to a terminal.
     */

    private IO() {
        throw new Error("no instances");
    }

    /**
     * Writes a string representation of the specified object and then writes
     * a line separator to the standard output.
     *
     * <p> The effect is as if {@link java.io.PrintStream#println(Object) println(obj)}
     * had been called on {@code System.out}.
     *
     * @param obj the object to print, may be {@code null}
     */
    public static void println(Object obj) {
        System.out.println(obj);
    }

    /**
     * Writes a line separator to the standard output.
     *
     * <p> The effect is as if {@link java.io.PrintStream#println() println()}
     * had been called on {@code System.out}.
     */
    public static void println() {
        System.out.println();
    }

    /**
     * Writes a string representation of the specified object to the
     * standard output.
     *
     * <p> The effect is as if {@link java.io.PrintStream#print(Object) print(obj)}
     * had been called on {@code System.out}.
     *
     * @param obj the object to print, may be {@code null}
     */
    public static void print(Object obj) {
        System.out.print(obj);
        System.out.flush();
    }

    /**
     * Reads a single line of text from the standard input.
     * <p>
     * If necessary, this method first creates internal objects that decode
     * the bytes read from the standard input into characters. These objects
     * are retained for subsequent reuse by this method.
     * <p>
     * One line is read from the decoded input as if by
     * {@link java.io.BufferedReader#readLine() BufferedReader.readLine()}
     * and then the result is returned.
     *
     * @return a string containing the line read from the standard input, not
     * including any line separator characters. Returns {@code null} if an
     * end of stream has been reached without having read any characters.
     *
     * @throws IOError if an I/O error occurs
     */
    public static String readln() {
        try {
            return reader().readLine();
        } catch (IOException ioe) {
            throw new IOError(ioe);
        }
    }

    /**
     * Writes a prompt and then reads a line of input.
     *
     * <p>
     * Writes a prompt as if by calling {@code print}, and then reads a single
     * line of text as if by calling {@link readln readln()}.
     *
     * @param prompt the prompt string, may be {@code null}
     *
     * @return a string containing the line read from the standard input, not
     * including any line separator characters. Returns {@code null} if an
     * end of stream has been reached without having read any characters.
     *
     * @throws IOError if an I/O error occurs
     */
    public static String readln(String prompt) {
        print(prompt);
        return readln();
    }

    /**
     * The BufferedReader used by readln(). Initialized under a class lock by
     * the reader() method.
     */
    static BufferedReader br;

    /**
     * On the first call, creates an InputStreamReader to decode characters from
     * System.in, wraps it in a BufferedReader, and returns the BufferedReader.
     * These objects are cached and returned by subsequent calls.
     *
     * @return the internal BufferedReader instance
     */
    static synchronized BufferedReader reader() {
        if (br == null) {
            String enc = System.getProperty("stdin.encoding", "");
            Charset cs = Charset.forName(enc, StandardCharsets.UTF_8);
            br = new BufferedReader(new InputStreamReader(System.in, cs));
        }
        return br;
    }
}
