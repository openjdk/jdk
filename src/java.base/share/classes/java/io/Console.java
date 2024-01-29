/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;
import java.nio.charset.Charset;
import jdk.internal.access.JavaIOAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.io.JdkConsoleImpl;
import jdk.internal.io.JdkConsoleProvider;
import jdk.internal.util.StaticProperty;
import sun.security.action.GetPropertyAction;

/**
 * Methods to access the character-based console device, if any, associated
 * with the current Java virtual machine.
 *
 * <p> Whether a virtual machine has a console is dependent upon the
 * underlying platform and also upon the manner in which the virtual
 * machine is invoked.  If the virtual machine is started from an
 * interactive command line without redirecting the standard input and
 * output streams then its console will exist and will typically be
 * connected to the keyboard and display from which the virtual machine
 * was launched.  If the virtual machine is started automatically, for
 * example by a background job scheduler, then it may not
 * have a console.
 * <p>
 * If this virtual machine has a console then it is represented by a
 * unique instance of this class which can be obtained by invoking the
 * {@link java.lang.System#console()} method.  If no console device is
 * available then an invocation of that method will return {@code null}.
 * <p>
 * Read and write operations are synchronized to guarantee the atomic
 * completion of critical operations; therefore invoking methods
 * {@link #readLine()}, {@link #readPassword()}, {@link #format format()},
 * {@link #printf printf()} as well as the read, format and write operations
 * on the objects returned by {@link #reader()} and {@link #writer()} may
 * block in multithreaded scenarios.
 * <p>
 * Invoking {@code close()} on the objects returned by the {@link #reader()}
 * and the {@link #writer()} will not close the underlying stream of those
 * objects.
 * <p>
 * The console-read methods return {@code null} when the end of the
 * console input stream is reached, for example by typing control-D on
 * Unix or control-Z on Windows.  Subsequent read operations will succeed
 * if additional characters are later entered on the console's input
 * device.
 * <p>
 * Unless otherwise specified, passing a {@code null} argument to any method
 * in this class will cause a {@link NullPointerException} to be thrown.
 * <p>
 * <b>Security note:</b>
 * If an application needs to read a password or other secure data, it should
 * use {@link #readPassword()} or {@link #readPassword(String, Object...)} and
 * manually zero the returned character array after processing to minimize the
 * lifetime of sensitive data in memory.
 *
 * {@snippet lang=java :
 * Console cons;
 * char[] passwd;
 * if ((cons = System.console()) != null &&
 *     (passwd = cons.readPassword("[%s]", "Password:")) != null) {
 *     code: // @replace substring="code:" replacement="..."
 *     java.util.Arrays.fill(passwd, ' ');
 * }
 * }
 *
 * @author  Xueming Shen
 * @since   1.6
 */
public sealed class Console implements Flushable permits ProxyingConsole {
    /**
     * Package private no-arg constructor.
     */
    Console() {}

    /**
     * Retrieves the unique {@link java.io.PrintWriter PrintWriter} object
     * associated with this console.
     *
     * @return  The printwriter associated with this console
     */
    public PrintWriter writer() {
        throw newUnsupportedOperationException();
    }

    /**
     * Retrieves the unique {@link java.io.Reader Reader} object associated
     * with this console.
     * <p>
     * This method is intended to be used by sophisticated applications, for
     * example, a {@link java.util.Scanner} object which utilizes the rich
     * parsing/scanning functionality provided by the {@code Scanner}:
     * {@snippet lang=java :
     *     Console con = System.console();
     *     if (con != null) {
     *         Scanner sc = new Scanner(con.reader());
     *         code: // @replace substring="code:" replacement="..."
     *     }
     * }
     * <p>
     * For simple applications requiring only line-oriented reading, use
     * {@link #readLine}.
     * <p>
     * The bulk read operations {@link java.io.Reader#read(char[]) read(char[])},
     * {@link java.io.Reader#read(char[], int, int) read(char[], int, int)} and
     * {@link java.io.Reader#read(java.nio.CharBuffer) read(java.nio.CharBuffer)}
     * on the returned object will not read in characters beyond the line
     * bound for each invocation, even if the destination buffer has space for
     * more characters. The {@code Reader}'s {@code read} methods may block if a
     * line bound has not been entered or reached on the console's input device.
     * A line bound is considered to be any one of a line feed ({@code '\n'}),
     * a carriage return ({@code '\r'}), a carriage return followed immediately
     * by a linefeed, or an end of stream.
     *
     * @return  The reader associated with this console
     */
    public Reader reader() {
        throw newUnsupportedOperationException();
    }

    /**
     * Writes a formatted string to this console's output stream using
     * the specified format string and arguments.
     *
     * @param  fmt
     *         A format string as described in <a
     *         href="../util/Formatter.html#syntax">Format string syntax</a>.
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
     * @throws  IllegalFormatException
     *          If a format string contains an illegal syntax, a format
     *          specifier that is incompatible with the given arguments,
     *          insufficient arguments given the format string, or other
     *          illegal conditions.  For specification of all possible
     *          formatting errors, see the <a
     *          href="../util/Formatter.html#detail">Details</a> section
     *          of the formatter class specification.
     *
     * @return  This console
     */
    public Console format(String fmt, Object ...args) {
        throw newUnsupportedOperationException();
    }

    /**
     * A convenience method to write a formatted string to this console's
     * output stream using the specified format string and arguments.
     *
     * <p> An invocation of this method of the form
     * {@code con.printf(format, args)} behaves in exactly the same way
     * as the invocation of
     * {@snippet lang=java :
     *     con.format(format, args)
     * }
     *
     * @param  format
     *         A format string as described in <a
     *         href="../util/Formatter.html#syntax">Format string syntax</a>.
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
     * @throws  IllegalFormatException
     *          If a format string contains an illegal syntax, a format
     *          specifier that is incompatible with the given arguments,
     *          insufficient arguments given the format string, or other
     *          illegal conditions.  For specification of all possible
     *          formatting errors, see the <a
     *          href="../util/Formatter.html#detail">Details</a> section of the
     *          formatter class specification.
     *
     * @return  This console
     */
    public Console printf(String format, Object ... args) {
        throw newUnsupportedOperationException();
    }

    /**
     * Provides a formatted prompt, then reads a single line of text from the
     * console.
     *
     * @param  fmt
     *         A format string as described in <a
     *         href="../util/Formatter.html#syntax">Format string syntax</a>.
     *
     * @param  args
     *         Arguments referenced by the format specifiers in the format
     *         string.  If there are more arguments than format specifiers, the
     *         extra arguments are ignored.  The maximum number of arguments is
     *         limited by the maximum dimension of a Java array as defined by
     *         <cite>The Java Virtual Machine Specification</cite>.
     *
     * @throws  IllegalFormatException
     *          If a format string contains an illegal syntax, a format
     *          specifier that is incompatible with the given arguments,
     *          insufficient arguments given the format string, or other
     *          illegal conditions.  For specification of all possible
     *          formatting errors, see the <a
     *          href="../util/Formatter.html#detail">Details</a> section
     *          of the formatter class specification.
     *
     * @throws IOError
     *         If an I/O error occurs.
     *
     * @return  A string containing the line read from the console, not
     *          including any line-termination characters, or {@code null}
     *          if an end of stream has been reached.
     */
    public String readLine(String fmt, Object ... args) {
        throw newUnsupportedOperationException();
    }

    /**
     * Reads a single line of text from the console.
     *
     * @throws IOError
     *         If an I/O error occurs.
     *
     * @return  A string containing the line read from the console, not
     *          including any line-termination characters, or {@code null}
     *          if an end of stream has been reached.
     */
    public String readLine() {
        throw newUnsupportedOperationException();
    }

    /**
     * Provides a formatted prompt, then reads a password or passphrase from
     * the console with echoing disabled.
     *
     * @param  fmt
     *         A format string as described in <a
     *         href="../util/Formatter.html#syntax">Format string syntax</a>
     *         for the prompt text.
     *
     * @param  args
     *         Arguments referenced by the format specifiers in the format
     *         string.  If there are more arguments than format specifiers, the
     *         extra arguments are ignored.  The maximum number of arguments is
     *         limited by the maximum dimension of a Java array as defined by
     *         <cite>The Java Virtual Machine Specification</cite>.
     *
     * @throws  IllegalFormatException
     *          If a format string contains an illegal syntax, a format
     *          specifier that is incompatible with the given arguments,
     *          insufficient arguments given the format string, or other
     *          illegal conditions.  For specification of all possible
     *          formatting errors, see the <a
     *          href="../util/Formatter.html#detail">Details</a>
     *          section of the formatter class specification.
     *
     * @throws IOError
     *         If an I/O error occurs.
     *
     * @return  A character array containing the password or passphrase read
     *          from the console, not including any line-termination characters,
     *          or {@code null} if an end of stream has been reached.
     */
    public char[] readPassword(String fmt, Object ... args) {
        throw newUnsupportedOperationException();
    }

    /**
     * Reads a password or passphrase from the console with echoing disabled.
     *
     * @throws IOError
     *         If an I/O error occurs.
     *
     * @return  A character array containing the password or passphrase read
     *          from the console, not including any line-termination characters,
     *          or {@code null} if an end of stream has been reached.
     */
    public char[] readPassword() {
        throw newUnsupportedOperationException();
    }

    /**
     * Flushes the console and forces any buffered output to be written
     * immediately.
     */
    public void flush() {
        throw newUnsupportedOperationException();
    }

    /**
     * Returns the {@link java.nio.charset.Charset Charset} object used for
     * the {@code Console}.
     * <p>
     * The returned charset corresponds to the input and output source
     * (e.g., keyboard and/or display) specified by the host environment or user.
     * It may not necessarily be the same as the default charset returned from
     * {@link java.nio.charset.Charset#defaultCharset() Charset.defaultCharset()}.
     *
     * @return a {@link java.nio.charset.Charset Charset} object used for the
     *          {@code Console}
     * @since 17
     */
    public Charset charset() {
        throw newUnsupportedOperationException();
    }

    /**
     * {@return {@code true} if the {@code Console} instance is a terminal}
     * <p>
     * This method returns {@code true} if the console device, associated with the current
     * Java virtual machine, is a terminal, typically an interactive command line
     * connected to a keyboard and display.
     *
     * @implNote The default implementation returns the value equivalent to calling
     * {@code isatty(stdin/stdout)} on POSIX platforms, or whether standard in/out file
     * descriptors are character devices or not on Windows.
     *
     * @since 22
     */
    public boolean isTerminal() {
        return istty;
    }

    private static UnsupportedOperationException newUnsupportedOperationException() {
        return new UnsupportedOperationException(
                "Console class itself does not provide implementation");
    }

    private static native String encoding();
    private static final boolean istty = istty();
    static final Charset CHARSET;
    static {
        Charset cs = null;

        if (istty) {
            String csname = encoding();
            if (csname == null) {
                csname = GetPropertyAction.privilegedGetProperty("stdout.encoding");
            }
            if (csname != null) {
                cs = Charset.forName(csname, null);
            }
        }
        if (cs == null) {
            cs = Charset.forName(StaticProperty.nativeEncoding(),
                    Charset.defaultCharset());
        }

        CHARSET = cs;

        cons = instantiateConsole(istty);

        // Set up JavaIOAccess in SharedSecrets
        SharedSecrets.setJavaIOAccess(new JavaIOAccess() {
            public Console console() {
                return cons;
            }
        });
    }

    @SuppressWarnings("removal")
    private static Console instantiateConsole(boolean istty) {
        Console c;

        try {
            /*
             * The JdkConsole provider used for Console instantiation can be specified
             * with the system property "jdk.console", whose value designates the module
             * name of the implementation, and which defaults to "java.base". If no
             * providers are available, or instantiation failed, java.base built-in
             * Console implementation is used.
             */
            PrivilegedAction<Console> pa = () -> {
                var consModName = System.getProperty("jdk.console",
                        JdkConsoleProvider.DEFAULT_PROVIDER_MODULE_NAME);
                return ServiceLoader.load(ModuleLayer.boot(), JdkConsoleProvider.class).stream()
                        .map(ServiceLoader.Provider::get)
                        .filter(jcp -> consModName.equals(jcp.getClass().getModule().getName()))
                        .map(jcp -> jcp.console(istty, CHARSET))
                        .filter(Objects::nonNull)
                        .findAny()
                        .map(jc -> (Console) new ProxyingConsole(jc))
                        .orElse(null);
            };
            c = AccessController.doPrivileged(pa);
        } catch (ServiceConfigurationError ignore) {
            c = null;
        }

        // If not found, default to built-in Console
        if (istty && c == null) {
            c = new ProxyingConsole(new JdkConsoleImpl(CHARSET));
        }

        return c;
    }

    private static final Console cons;
    private static native boolean istty();
}
