/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.org.jline;

import jdk.internal.io.JdkConsoleProvider;
import jdk.internal.org.jline.reader.LineReader;
import jdk.internal.org.jline.reader.LineReaderBuilder;
import jdk.internal.org.jline.terminal.Terminal;
import jdk.internal.org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.Charset;

import jdk.internal.io.JdkConsole;

/**
 * JdkConsole impl
 *
 * @since 20
 */
public class JdkConsoleProviderImpl extends JdkConsoleProvider {

    @Override
    public JdkConsole console(Charset charset, boolean isTTY) {
        return new JdkConsoleImpl(charset);
    }

    public static class JdkConsoleImpl extends JdkConsole {
        /**
         * {@inheritDoc}
         */
        @Override
        public PrintWriter writer() {
            return terminal.writer();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Reader reader() {
            return terminal.reader();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public synchronized JdkConsole format(String fmt, Object ...args) {
            writer().format(fmt, args).flush();
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public JdkConsole printf(String format, Object ... args) {
            return format(format, args);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public synchronized String readLine(String fmt, Object ... args) {
            return jline.readLine(fmt.formatted(args));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String readLine() {
            return readLine("");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public synchronized char[] readPassword(String fmt, Object ... args) {
            return jline.readLine(fmt.formatted(args), '\0').toCharArray();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public char[] readPassword() {
            return readPassword("");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void flush() {
            terminal.flush();
        }

        public Charset charset() {
            return charset;
        }

        private final LineReader jline;
        private final Terminal terminal;
        private final Charset charset;

        public JdkConsoleImpl(Charset cs) {
            charset = cs;
            try {
                terminal = TerminalBuilder.builder().encoding(cs).build();
                jline = LineReaderBuilder.builder().terminal(terminal).build();
            } catch (IOException ioe) {
                throw new InternalError("should not happen, as CHARSET is guaranteed to be a valid charset");
            }
        }
    }
}
