/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.tools.jjs;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import jdk.internal.jline.NoInterruptUnixTerminal;
import jdk.internal.jline.Terminal;
import jdk.internal.jline.TerminalFactory;
import jdk.internal.jline.TerminalFactory.Flavor;
import jdk.internal.jline.WindowsTerminal;
import jdk.internal.jline.console.ConsoleReader;
import jdk.internal.jline.console.completer.Completer;
import jdk.internal.jline.console.history.FileHistory;

class Console implements AutoCloseable {
    private final ConsoleReader in;
    private final FileHistory history;

    Console(final InputStream cmdin, final PrintStream cmdout, final File historyFile,
            final Completer completer) throws IOException {
        in = new ConsoleReader(cmdin, cmdout);
        TerminalFactory.registerFlavor(Flavor.WINDOWS, JJSWindowsTerminal :: new);
        TerminalFactory.registerFlavor(Flavor.UNIX, JJSUnixTerminal :: new);
        in.setExpandEvents(false);
        in.setHandleUserInterrupt(true);
        in.setBellEnabled(true);
        in.setHistory(history = new FileHistory(historyFile));
        in.addCompleter(completer);
        Runtime.getRuntime().addShutdownHook(new Thread((Runnable)this::saveHistory));
    }

    String readLine(final String prompt) throws IOException {
        return in.readLine(prompt);
    }

    @Override
    public void close() {
        saveHistory();
    }

    private void saveHistory() {
        try {
            getHistory().flush();
        } catch (final IOException exp) {}
    }

    FileHistory getHistory() {
        return (FileHistory) in.getHistory();
    }

    boolean terminalEditorRunning() {
        Terminal terminal = in.getTerminal();
        if (terminal instanceof JJSUnixTerminal) {
            return ((JJSUnixTerminal) terminal).isRaw();
        }
        return false;
    }

    void suspend() {
        try {
            in.getTerminal().restore();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    void resume() {
        try {
            in.getTerminal().init();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    static final class JJSUnixTerminal extends NoInterruptUnixTerminal {
        JJSUnixTerminal() throws Exception {
        }

        boolean isRaw() {
            try {
                return getSettings().get("-a").contains("-icanon");
            } catch (IOException | InterruptedException ex) {
                return false;
            }
        }

        @Override
        public void disableInterruptCharacter() {
        }

        @Override
        public void enableInterruptCharacter() {
        }
    }

    static final class JJSWindowsTerminal extends WindowsTerminal {
        public JJSWindowsTerminal() throws Exception {
        }

        @Override
        public void init() throws Exception {
            super.init();
            setAnsiSupported(false);
        }
    }
}
