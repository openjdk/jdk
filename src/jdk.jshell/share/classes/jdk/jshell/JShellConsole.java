/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jshell;

import java.io.IOError;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.Charset;
import jdk.internal.javac.PreviewFeature;

/**
 * An interface providing functionality for {@link java.io.Console} in the user's snippet.
 * <p>
 * When a snippet calls a method on {@code Console}, the corresponding method in this interface will
 * be called.
 *
 * @since 21
 * @see java.io.Console
 */
public interface JShellConsole {

    /**
     * Retrieves the unique {@link java.io.PrintWriter PrintWriter} object
     * associated with this console.
     *
     * @return  The printwriter associated with this console
     * @see java.io.Console#writer()
     */
    public PrintWriter writer();

    /**
     * Retrieves the unique {@link java.io.Reader Reader} object associated
     * with this console.
     *
     * @return  The reader associated with this console
     * @see java.io.Console#reader()
     */
    public Reader reader();

    /**
     * Provides a prompt, then reads a single line of text from the
     * console.
     *
     * @param  prompt
     *         A prompt.
     *
     * @throws IOError
     *         If an I/O error occurs.
     *
     * @return  A string containing the line read from the console, not
     *          including any line-termination characters, or {@code null}
     *          if an end of stream has been reached.
     * @see java.io.Console#readLine()
     */
    public String readLine(String prompt) throws IOError;

    /**
     * Provides a prompt, then reads a password or passphrase from
     * the console with echoing disabled.
     *
     * @param  prompt
     *         A prompt.
     *
     * @throws IOError
     *         If an I/O error occurs.
     *
     * @return  A character array containing the password or passphrase read
     *          from the console, not including any line-termination characters,
     *          or {@code null} if an end of stream has been reached.
     * @see java.io.Console#readPassword()
     */
    public char[] readPassword(String prompt) throws IOError;

    /**
     * Flushes the console and forces any buffered output to be written
     * immediately.
     *
     * @see java.io.Console#flush()
     */
    public void flush();

    /**
     * Returns the {@link java.nio.charset.Charset Charset} object used for
     * the {@code Console}.
     *
     * @return a {@link java.nio.charset.Charset Charset} object used for the
     *          {@code Console}
     *
     * @see java.io.Console#charset()
     */
    public Charset charset();

}
