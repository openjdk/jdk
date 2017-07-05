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

import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import jdk.internal.jline.NoInterruptUnixTerminal;
import jdk.internal.jline.Terminal;
import jdk.internal.jline.TerminalFactory;
import jdk.internal.jline.TerminalFactory.Flavor;
import jdk.internal.jline.WindowsTerminal;
import jdk.internal.jline.console.ConsoleReader;
import jdk.internal.jline.console.KeyMap;
import jdk.internal.jline.console.completer.Completer;
import jdk.internal.jline.console.history.FileHistory;

class Console implements AutoCloseable {
    private static final String DOCUMENTATION_SHORTCUT = "\033\133\132"; //Shift-TAB
    private final ConsoleReader in;
    private final FileHistory history;

    Console(final InputStream cmdin, final PrintStream cmdout, final File historyFile,
            final Completer completer, final Function<String, String> docHelper) throws IOException {
        TerminalFactory.registerFlavor(Flavor.WINDOWS, isCygwin()? JJSUnixTerminal::new : JJSWindowsTerminal::new);
        TerminalFactory.registerFlavor(Flavor.UNIX, JJSUnixTerminal::new);
        in = new ConsoleReader(cmdin, cmdout);
        in.setExpandEvents(false);
        in.setHandleUserInterrupt(true);
        in.setBellEnabled(true);
        in.setHistory(history = new FileHistory(historyFile));
        in.addCompleter(completer);
        Runtime.getRuntime().addShutdownHook(new Thread((Runnable)this::saveHistory));
        bind(DOCUMENTATION_SHORTCUT, (ActionListener)evt -> showDocumentation(docHelper));
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

    private static boolean isCygwin() {
        return System.getenv("SHELL") != null;
    }

    private void bind(String shortcut, Object action) {
        KeyMap km = in.getKeys();
        for (int i = 0; i < shortcut.length(); i++) {
            final Object value = km.getBound(Character.toString(shortcut.charAt(i)));
            if (value instanceof KeyMap) {
                km = (KeyMap) value;
            } else {
                km.bind(shortcut.substring(i), action);
            }
        }
    }

    private void showDocumentation(final Function<String, String> docHelper) {
        final String buffer = in.getCursorBuffer().buffer.toString();
        final int cursor = in.getCursorBuffer().cursor;
        final String doc = docHelper.apply(buffer.substring(0, cursor));
        try {
            if (doc != null) {
                in.println();
                in.println(doc);
                in.redrawLine();
                in.flush();
            } else {
                in.beep();
            }
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
