/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Locale;
import jdk.internal.javac.PreviewFeature;

/**
 * A collection of static methods that provide convenience access to
 * {@linkplain System#in} and {@linkplain System#out} for line-based
 * input and output.
 *
 * TODO:
 *
 * Encoding of System.out is either default charset or stdout.encoding.
 * Which is it?
 *
 * What should be the encoding of the internal BufferedReader? It should ideally
 * be consistent with stdout, but unclear whether that's the default charset
 * or stdout.encoding. There is no stdin.encoding property.
 *
 * There probably needs to be a way to control the encoding of the internal
 * BufferedReader. This can either be an explicit API or some way to specify
 * it using system properties.
 *
 * Do we want printf as well? And its locale overload?
 *
 *
 * @since 25
 */
@PreviewFeature(feature = PreviewFeature.Feature.IMPLICIT_CLASSES)
public final class IO {

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
    }

    /**
     * Terminates the current line on the standard output.
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
    }

    /**
     * Formats the arguments according to the provided format string and locale,
     * and then writes the result to the standard output. The effect is as if
     * {@code System.out.printf(l, format, args)} were called.
     *
     * @param l the locale to use for formatting, may be null
     * @param format format string as described in
     *        <a href="../util/Formatter.html#syntax">Format string syntax</a>
     * @param args the arguments for formatting
     */
    public static void printf(Locale l, String format, Object ... args) {
        System.out.printf(l, format, args);
    }

    /**
     * Formats the arguments according to the provided format string,
     * and then writes the result to the standard output. The effect is as if
     * {@code System.out.printf(format, args)} were called.
     *
     * @param format format string as described in
     *        <a href="../util/Formatter.html#syntax">Format string syntax</a>
     * @param args the arguments for formatting
     */
    public static void printf(String format, Object ... args) {
        System.out.printf(format, args);
    }

    /**
     * Reads a single line of text from the standard input.
     *
     * <p>Returns the value obtained as if by {@code reader().readLine()}.
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
     * Writes a prompt as if by calling {@code print}, flushes output as if by
     * calling {@link java.io.PrintStream#flush flush()} on {@code System.out},
     * and then reads a single line of text as if by calling {@link readln readln()}.
     *
     * <p> TBD synchronized?
     *
     * <p> TBD should argument be Object?
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
        System.out.flush();
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
    public static synchronized BufferedReader reader() {
        if (br == null) {
            br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        }
        return br;
    }

    /**
     * Sets the charset to be used for reading text from standard input.
     * This method must be called prior to any of the the reader(), readln(), or readln(String)
     * methods. If one if these methods has already been called, calls to this method throw
     * IllegalStateException.
     *
     * @param cs the charset to be used for reading
     * @throws IllegalStateException if one of the read methods has already been called
     */
    public static synchronized void setInputEncoding(Charset cs) {
        if (br == null) {
            br = new BufferedReader(new InputStreamReader(System.in, cs));
        } else {
            throw new IllegalStateException("input reader has already been created");
        }
    }
}
