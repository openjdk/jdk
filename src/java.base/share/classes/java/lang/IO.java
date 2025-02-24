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
 * for decoding is XXXTODOXXX. These internal objects are created upon the first call to
 * either of the {@code readln} methods and are stored for subsequent reuse by these methods.
 * The result of interleaving calls to the {@code readln} methods with operations on
 * {@code System.in} is unspecified.
 *
 * @since 25
 */
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

    /**
     * TODO: should output be flushed automatically? Need to probe System.out to
     * see what it's connected to and make a determination based on that.
     */
    private static final boolean AUTOFLUSH = true;

    /**
     * TODO: What should be the encoding of the internal BufferedReader? Need to
     * probe System.in and see what it's connected to, and possibly query some
     * system properties to make a determination. The initialization of this field
     * might be moved into the reader() method.
     */
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    private IO() {
        throw new Error("no instances");
    }

    /**
     * Writes a string representation of the specified object and then
     * terminates the current line on the standard output.
     * standard output.
     *
     * <p> The effect is as if {@link java.io.PrintStream#println(Object) println(obj)}
     * had been called on {@code System.out}.
     *
     * @param obj the object to print, may be {@code null}
     */
    public static void println(Object obj) {
        System.out.println(obj);
        if (AUTOFLUSH) {
            System.out.flush();
        }
    }

    /**
     * Terminates the current line on the standard output.
     *
     * <p> The effect is as if {@link java.io.PrintStream#println() println()}
     * had been called on {@code System.out}.
     */
    public static void println() {
        System.out.println();
        if (AUTOFLUSH) {
            System.out.flush();
        }
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
        if (AUTOFLUSH) {
            System.out.flush();
        }
    }

    /**
     * Reads a single line of text from the standard input.
     * <p>
     * If necessary, this method first creates an internal
     * {@link java.nio.charset.CharsetDecoder CharsetDecoder}
     * to decode the bytes read from the standard input into characters.
     * It is then wrapped within a {@link java.io.Reader Reader} to
     * provide character input. These objects are retained for
     * subsequent use by this method.
     * <p>
     * One line is read as if by
     * {@link java.io.BufferedReader#readLine() BufferedReader.readLine()}
     * and then the result is returned.
     *
     * @return a string containing the line read from the standard input, not
     * including any line-termination characters. Returns {@code null} if an
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
     * including any line-termination characters. Returns {@code null} if an
     * end of stream has been reached without having read any characters.
     *
     * @throws IOError if an I/O error occurs
     */
    public static String readln(String prompt) {
        print(prompt);
        return readln();
    }

    static BufferedReader br;

    /**
     * Returns the internal BufferedReader instance used for reading text from
     * the standard input. The internal BufferedReader is created if necessary
     * and is cached for future use. Subsequent calls to this method return the
     * same BufferedReader instance.
     * <p>
     * The default charset used when creating the internal BufferedReader is UTF-8.
     * A different charset maybe specified calling the {@link #setInputEncoding
     * setInputEncoding} method prior to calling this method or other reading methods
     * on this class.
     * <p>
     * The result of interleaving calls to methods on the internal BufferedReader
     * (including other methods on this class) with operations on {@code System.in}
     * is unspecified.
     *
     * @return the internal BufferedReader instance
     */
    private static synchronized BufferedReader reader() {
        if (br == null) {
            br = new BufferedReader(new InputStreamReader(System.in, CHARSET));
        }
        return br;
    }
}
