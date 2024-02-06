/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Elementary I/O.
 *
 * @since 23
 */
public final class SimpleIO {

    private SimpleIO() {
        throw new Error("no instances");
    }

    /**
     * Prints its argument followed by the platform line terminator
     * to the standard output.
     *
     * <p> This method works as if by passing its argument to
     * {@link System#out System.out}{@code .}{@link PrintStream#println(Object)
     * println(Object)}
     *
     * @param obj what it is to print
     *
     * @see #print(Object)
     */
    public static void println(Object obj) {
        System.out.println(obj);
    }

    /**
     * Prints its argument to the standard output.
     *
     * <p> The method works as if by passing its argument to
     * {@link System#out System.out}{@code .}{@link PrintStream#print(Object)
     * print(Object)}
     *
     * @param obj what it is to print
     *
     * @see #println(Object)
     */
    public static void print(Object obj) {
        System.out.print(obj);
    }

    /**
     * Reads a string from the standard input.
     *
     * <p> If the standard input is interactive, the specified prompt is
     * issued; otherwise, an attempt is made not to issue the prompt.
     *
     * <p> If this method encounters end-of-stream, it returns {@code null}.
     *
     * @param prompt a non-{@code null} but possibly empty string to display
     *               as prompt
     * @return a string read from the standard input; {@code null},
     *         only if end-of-stream is encountered
     *
     * @throws NullPointerException if {@code prompt} is {@code null}
     * @throws UncheckedIOException if
     *
     * @apiNote Standard input is interactive if it's connected to a
     * (pseudo-)terminal. Standard input is non-interactive if it's
     * redirected, for example, like this:
     * {@snippet :
     *    java MyClass.java < file.txt
     *}
     *
     * A user inputs a string or signals end-of-stream in a system-dependant
     * manner.
     *
     * To input a string, a user typically presses the "enter" key;
     * alternatively, on Unix, the user presses Control-J (^J) or
     * Control-M (^M) key combinations. To signal and end-of-stream,
     * a user can press Control-D (^D) on Unix or Ctrl+Z on Windows
     * key combination.
     *
     * <p> A programmer can use this method to input an integer number,
     * for example, as follows:
     * {@snippet :
     *    int i = ...;
     *    for (String s; (s = input("int?")) != null;) {
     *        try {
     *            i = Integer.parseInt(s);
     *        } catch (NumberFormatException _) {}
     *    }
     *    println(i);
     *}
     * A programmer can take a similar approach to input other primitives or
     * arbitrary big numbers, such as {@link java.math.BigInteger#BigInteger(String)}
     * and {@link java.math.BigDecimal#BigDecimal(String)}.
     *
     * <p> A programmer should take care not to mix different way of reading
     * from the standard input. That is, a programmer should choose between
     * {@link System#in}, {@link System#console}, this method, or some other
     * access to standard input and should stick to that choice in the
     * context of a program.
     */
    public static String input(String prompt) throws UncheckedIOException {
        if (console != null) {
            return console.readLine(prompt);
        } else {
            // read from System.in, but don't prompt on System.out
            try {
                return stdinReader.readLine();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static final Console console;
    private static final BufferedReader stdinReader;

    static {
        var con = System.console();
        // if a console is not connected to a terminal, we don't use it
        console = con != null && con.isTerminal() ? con : null;
        if (console == null) {
            Charset charset;
            try {
                var enc = System.getProperty("stdout.encoding");
                charset = Charset.forName(enc);
            } catch (IllegalArgumentException e) {
                throw new IOError(e);
            }
            stdinReader = new BufferedReader(new InputStreamReader(
                    System.in, charset));
        } else {
            stdinReader = null;
        }
        assert console == null ^ stdinReader == null;
    }
}
