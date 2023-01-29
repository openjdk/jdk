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

import java.nio.charset.Charset;
import java.util.Locale;

/**
 * This interface provides for printing formatted representations of objects.
 * <p>
 * It is unspecified whether flushing is enabled during invoking any of the methods.
 * Methods in this class never throw I/O exceptions.
 * <p>
 * It is required that subclasses always replace malformed and unmappable character sequences with
 * the charset's default replacement string.
 * The {@linkplain java.nio.charset.CharsetEncoder} class should be used when more
 * control over the encoding process is required.
 *
 * @implSpec
 * All implementations of the default {@code print} methods relies on the only abstract
 * method {@link #print(String)}.
 * All implementations of the default {@code println} methods relies on the corresponding
 * {@code print} method and the no-arg {@link #println()} method.
 *
 * @see PrintStream
 * @see PrintWriter
 * @since TBA
 */
public interface PrintOutput extends Appendable {
    /**
     * Appends the specified character.
     *
     * @implSpec This implementation behaves as if:
     * {@snippet :
     *     print(String.valueOf(c));
     *     return this;
     * }
     *
     * @param  c The 16-bit character to append
     *
     * @return {@code this}
     */
    @Override
    default PrintOutput append(char c) {
        print(String.valueOf(c));
        return this;
    }

    /**
     * Appends the specified character sequence.
     *
     * <p> Depending on the specification of {@code toString} for the
     * character sequence {@code csq}, the entire sequence may not be
     * appended.  For instance, invoking then {@code toString} method of a
     * character buffer will return a subsequence whose content depends upon
     * the buffer's position and limit.
     *
     * @implSpec This implementation behaves as if:
     * {@snippet :
     *     print(String.valueOf(csq));
     *     return this;
     * }
     *
     * @param  csq
     *         The character sequence to append.  If {@code csq} is
     *         {@code null}, then the four characters {@code "null"} are
     *         appended to this output stream.
     *
     * @return {@code this}
     */
    @Override
    default PrintOutput append(CharSequence csq) {
        print(String.valueOf(csq));
        return this;
    }

    /**
     * Appends a subsequence of the specified character sequence.
     *
     * @implSpec This implementation behaves as if:
     * {@snippet :
     *     if (csq == null) csq = "null";
     *     return append(csq.subSequence(start, end));
     * }
     * @param  csq
     *         The character sequence from which a subsequence will be
     *         appended.  If {@code csq} is {@code null}, then characters
     *         will be appended as if {@code csq} contained the four
     *         characters {@code "null"}.
     *
     * @param  start
     *         The index of the first character in the subsequence
     *
     * @param  end
     *         The index of the character following the last character in the
     *         subsequence
     *
     * @return {@code this}
     *
     * @throws  IndexOutOfBoundsException
     *          If {@code start} or {@code end} are negative, {@code start}
     *          is greater than {@code end}, or {@code end} is greater than
     *          {@code csq.length()}
     */
    @Override
    default PrintOutput append(CharSequence csq, int start, int end) {
        if (csq == null) csq = "null";
        return append(csq.subSequence(start, end));
    }

    /**
     * Checks its error state.  The stream may be flushed if it is not closed.
     *
     * @implSpec This implementation simply returns {@code false}.  It is recommended
     * to override it.
     *
     * @return {@code true} if an I/O exception has occurred.
     */
    default boolean checkError() {
        return false;
    }

    /**
     * Prints a boolean value.  The string produced by {@link
     * java.lang.String#valueOf(boolean)} is translated into bytes
     * according to the default charset, and these bytes
     * are written to this stream.
     *
     * @implSpec This implementation invokes {@code print(String.valueOf(b))}.
     *
     * @param      b   The {@code boolean} to be printed
     * @see Charset#defaultCharset()
     */
    default void print(boolean b) {
        print(String.valueOf(b));
    }

    /**
     * Prints a character.  The character is translated into one or more bytes
     * according to the character encoding given to the constructor, or the
     * default charset if none specified. These bytes
     * are written to this stream.
     *
     * @implSpec This implementation invokes {@code print(String.valueOf(c))}.
     *
     * @param      c   The {@code char} to be printed
     * @see Charset#defaultCharset()
     */
    default void print(char c) {
        print(String.valueOf(c));
    }

    /**
     * Prints an integer.  The string produced by {@link
     * java.lang.String#valueOf(int)} is translated into bytes
     * according to the default charset, and these bytes
     * are written to this stream.
     *
     * @implSpec This implementation invokes {@code print(String.valueOf(i))}.
     *
     * @param      i   The {@code int} to be printed
     * @see        java.lang.Integer#toString(int)
     * @see Charset#defaultCharset()
     */
    default void print(int i) {
        print(String.valueOf(i));
    }

    /**
     * Prints a long integer.  The string produced by {@link
     * java.lang.String#valueOf(long)} is translated into bytes
     * according to the default charset, and these bytes
     * are written to this stream.
     *
     * @implSpec This implementation invokes {@code print(String.valueOf(l))}.
     *
     * @param      l   The {@code long} to be printed
     * @see        java.lang.Long#toString(long)
     * @see Charset#defaultCharset()
     */
    default void print(long l) {
        print(String.valueOf(l));
    }

    /**
     * Prints a floating-point number.  The string produced by {@link
     * java.lang.String#valueOf(float)} is translated into bytes
     * according to the default charset, and these bytes
     * are written to this stream.
     *
     * @implSpec This implementation invokes {@code print(String.valueOf(f))}.
     *
     * @param      f   The {@code float} to be printed
     * @see        java.lang.Float#toString(float)
     * @see Charset#defaultCharset()
     */
    default void print(float f) {
        print(String.valueOf(f));
    }

    /**
     * Prints a double-precision floating-point number.  The string produced by
     * {@link java.lang.String#valueOf(double)} is translated into
     * bytes according to the default charset, and these
     * bytes are written to this stream.
     *
     * @implSpec This implementation invokes {@code print(String.valueOf(d))}.
     *
     * @param      d   The {@code double} to be printed
     * @see        java.lang.Double#toString(double)
     * @see Charset#defaultCharset()
     */
    default void print(double d) {
        print(String.valueOf(d));
    }

    /**
     * Prints an array of characters.  The characters are converted into bytes
     * according to the character encoding given to the constructor, or the
     * default charset if none specified. These bytes
     * are written to this stream.
     *
     * @implSpec This implementation invokes {@code print(String.valueOf(s))}.
     *
     * @param      s   The array of chars to be printed
     * @see Charset#defaultCharset()
     *
     * @throws  NullPointerException  If {@code s} is {@code null}
     */
    default void print(char[] s) {
        print(String.valueOf(s));
    }

    /**
     * Prints a string.  If the argument is {@code null} then the string
     * {@code "null"} is printed.  Otherwise, the string's characters are
     * converted into bytes according to the character encoding given to the
     * constructor, or the default charset if none
     * specified. These bytes are written to this stream.
     *
     * @param      s   The {@code String} to be printed
     * @see Charset#defaultCharset()
     */
    void print(String s);

    /**
     * Prints an object.  The string produced by the {@link
     * java.lang.String#valueOf(Object)} method is translated into bytes
     * according to the default charset, and these bytes
     * are written to this stream.
     *
     * @implSpec This implementation invokes {@code print(String.valueOf(obj))}.
     *
     * @param      obj   The {@code Object} to be printed
     * @see        java.lang.Object#toString()
     * @see Charset#defaultCharset()
     */
    default void print(Object obj) {
        print(String.valueOf(obj));
    }

    /**
     * Terminates the current line by writing the line separator string.  The
     * line separator string is defined by the system property
     * {@code line.separator}, and is not necessarily a single newline
     * character ({@code '\n'}).
     *
     * @implSpec This implementation invokes {@code print(System.lineSeparator())}.
     */
    default void println() {
        print(System.lineSeparator());
    }

    /**
     * Prints a boolean and then terminates the line.
     *
     * @implSpec This implementation invokes {@link #print(boolean)} and then
     * {@link #println()}.
     *
     * @param x  The {@code boolean} to be printed
     */
    default void println(boolean x) {
        print(x);
        println();
    }

    /**
     * Prints a character and then terminates the line.
     *
     * @implSpec This implementation invokes {@link #print(char)} and then
     * {@link #println()}.
     *
     * @param x  The {@code char} to be printed.
     */
    default void println(char x) {
        print(x);
        println();
    }

    /**
     * Prints an integer and then terminates the line.
     *
     * @implSpec This implementation invokes {@link #print(int)} and then
     * {@link #println()}.
     *
     * @param x  The {@code int} to be printed.
     */
    default void println(int x) {
        print(x);
        println();
    }

    /**
     * Prints a long and then terminates the line.
     *
     * @implSpec This implementation invokes {@link #print(long)} and then
     * {@link #println()}.
     *
     * @param x  a The {@code long} to be printed.
     */
    default void println(long x) {
        print(x);
        println();
    }

    /**
     * Prints a float and then terminates the line.
     *
     * @implSpec This implementation invokes {@link #print(float)} and then
     * {@link #println()}.
     *
     * @param x  The {@code float} to be printed.
     */
    default void println(float x) {
        print(x);
        println();
    }

    /**
     * Prints a double and then terminates the line.
     *
     * @implSpec This implementation invokes {@link #print(double)} and then
     * {@link #println()}.
     *
     * @param x  The {@code double} to be printed.
     */
    default void println(double x) {
        print(x);
        println();
    }

    /**
     * Prints an array of characters and then terminates the line.
     *
     * @implSpec This implementation invokes {@link #print(char[])} and
     * then {@link #println()}.
     *
     * @param x  an array of chars to print.
     */
    default void println(char[] x) {
        print(x);
        println();
    }

    /**
     * Prints a String and then terminates the line.
     *
     * @implSpec This implementation invokes {@link #print(String)} and then
     * {@link #println()}.
     *
     * @param x  The {@code String} to be printed.
     */
    default void println(String x) {
        print(x);
        println();
    }

    /**
     * Prints an Object and then terminates the line.
     *
     * @implSpec This implementation invokes {@link #print(Object)} and then
     * {@link #println()}.
     *
     * @param x  The {@code Object} to be printed.
     */
    default void println(Object x) {
        print(x);
        println();
    }

    /**
     * A convenience method to write a formatted string to this output stream
     * using the specified format string and arguments.
     *
     * @implSpec This implementation invokes {@code format(format, args)}
     *
     * @param  format
     *         A format string as described in <a
     *         href="../util/Formatter.html#syntax">Format string syntax</a>
     *
     * @param  args
     *         Arguments referenced by the format specifiers in the format
     *         string.  If there are more arguments than format specifiers, the
     *         extra arguments are ignored.  The number of arguments is
     *         variable and may be zero.  The maximum number of arguments is
     *         limited by the maximum dimension of a Java array as defined by
     *         <cite>The Java Virtual Machine Specification</cite>.
     *         The behaviour on a
     *         {@code null} argument depends on the <a
     *         href="../util/Formatter.html#syntax">conversion</a>.
     *
     * @throws  java.util.IllegalFormatException
     *          If a format string contains an illegal syntax, a format
     *          specifier that is incompatible with the given arguments,
     *          insufficient arguments given the format string, or other
     *          illegal conditions.  For specification of all possible
     *          formatting errors, see the <a
     *          href="../util/Formatter.html#detail">Details</a> section of the
     *          formatter class specification.
     *
     * @throws  NullPointerException
     *          If the {@code format} is {@code null}
     *
     * @return  {@code this}
     */
    default PrintOutput printf(String format, Object... args) {
        return format(format, args);
    }

    /**
     * A convenience method to write a formatted string to this output stream
     * using the specified format string and arguments.
     *
     * @implSpec This implementation invokes {@code format(l, format, args)}
     *
     * @param  l
     *         The {@linkplain java.util.Locale locale} to apply during
     *         formatting.  If {@code l} is {@code null} then no localization
     *         is applied.
     *
     * @param  format
     *         A format string as described in <a
     *         href="../util/Formatter.html#syntax">Format string syntax</a>
     *
     * @param  args
     *         Arguments referenced by the format specifiers in the format
     *         string.  If there are more arguments than format specifiers, the
     *         extra arguments are ignored.  The number of arguments is
     *         variable and may be zero.  The maximum number of arguments is
     *         limited by the maximum dimension of a Java array as defined by
     *         <cite>The Java Virtual Machine Specification</cite>.
     *         The behaviour on a
     *         {@code null} argument depends on the <a
     *         href="../util/Formatter.html#syntax">conversion</a>.
     *
     * @throws  java.util.IllegalFormatException
     *          If a format string contains an illegal syntax, a format
     *          specifier that is incompatible with the given arguments,
     *          insufficient arguments given the format string, or other
     *          illegal conditions.  For specification of all possible
     *          formatting errors, see the <a
     *          href="../util/Formatter.html#detail">Details</a> section of the
     *          formatter class specification.
     *
     * @throws  NullPointerException
     *          If the {@code format} is {@code null}
     *
     * @return  {@code this}
     */
    default PrintOutput printf(Locale l, String format, Object... args) {
        return format(l, format, args);
    }

    /**
     * Writes a formatted string to this output stream using the specified
     * format string and arguments.
     *
     * @implSpec This implementation invokes {@code print(String.format(l, format, args))}
     *
     * @param  l
     *         The {@linkplain java.util.Locale locale} to apply during
     *         formatting.  If {@code l} is {@code null} then no localization
     *         is applied.
     *
     * @param  format
     *         A format string as described in <a
     *         href="../util/Formatter.html#syntax">Format string syntax</a>
     *
     * @param  args
     *         Arguments referenced by the format specifiers in the format
     *         string.  If there are more arguments than format specifiers, the
     *         extra arguments are ignored.  The number of arguments is
     *         variable and may be zero.  The maximum number of arguments is
     *         limited by the maximum dimension of a Java array as defined by
     *         <cite>The Java Virtual Machine Specification</cite>.
     *         The behaviour on a
     *         {@code null} argument depends on the <a
     *         href="../util/Formatter.html#syntax">conversion</a>.
     *
     * @throws  java.util.IllegalFormatException
     *          If a format string contains an illegal syntax, a format
     *          specifier that is incompatible with the given arguments,
     *          insufficient arguments given the format string, or other
     *          illegal conditions.  For specification of all possible
     *          formatting errors, see the <a
     *          href="../util/Formatter.html#detail">Details</a> section of the
     *          formatter class specification.
     *
     * @throws  NullPointerException
     *          If the {@code format} is {@code null}
     *
     * @return  {@code this}
     */
    default PrintOutput format(Locale l, String format, Object... args) {
        print(String.format(l, format, args));
        return this;
    }

    /**
     * Writes a formatted string to this output stream using the specified
     * format string and arguments.
     *
     * <p> The locale always used is the one returned by {@link
     * java.util.Locale#getDefault(Locale.Category)} with
     * {@link java.util.Locale.Category#FORMAT FORMAT} category specified,
     * regardless of any previous invocations of other formatting methods on
     * this object.
     *
     * @implSpec This implementation invokes {@code print(format.formatted(args))}
     *
     * @param  format
     *         A format string as described in <a
     *         href="../util/Formatter.html#syntax">Format string syntax</a>
     *
     * @param  args
     *         Arguments referenced by the format specifiers in the format
     *         string.  If there are more arguments than format specifiers, the
     *         extra arguments are ignored.  The number of arguments is
     *         variable and may be zero.  The maximum number of arguments is
     *         limited by the maximum dimension of a Java array as defined by
     *         <cite>The Java Virtual Machine Specification</cite>.
     *         The behaviour on a
     *         {@code null} argument depends on the <a
     *         href="../util/Formatter.html#syntax">conversion</a>.
     *
     * @throws  java.util.IllegalFormatException
     *          If a format string contains an illegal syntax, a format
     *          specifier that is incompatible with the given arguments,
     *          insufficient arguments given the format string, or other
     *          illegal conditions.  For specification of all possible
     *          formatting errors, see the <a
     *          href="../util/Formatter.html#detail">Details</a> section of the
     *          formatter class specification.
     *
     * @throws  NullPointerException
     *          If the {@code format} is {@code null}
     *
     * @return  {@code this}
     */
    default PrintOutput format(String format, Object... args) {
        print(format.formatted(args));
        return this;
    }
}
