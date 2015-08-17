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

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import jdk.internal.jline.console.ConsoleReader;
import jdk.internal.jline.console.completer.Completer;
import jdk.internal.jline.console.history.History.Entry;
import jdk.internal.jline.console.history.MemoryHistory;

class Console implements AutoCloseable {
    private final ConsoleReader in;
    private final PersistentHistory history;

    Console(final InputStream cmdin, final PrintStream cmdout, final Preferences prefs,
            final Completer completer) throws IOException {
        in = new ConsoleReader(cmdin, cmdout);
        in.setExpandEvents(false);
        in.setHandleUserInterrupt(true);
        in.setBellEnabled(true);
        in.setHistory(history = new PersistentHistory(prefs));
        in.addCompleter(completer);
        Runtime.getRuntime().addShutdownHook(new Thread(()->close()));
    }

    String readLine(final String prompt) throws IOException {
        return in.readLine(prompt);
    }


    @Override
    public void close() {
        history.save();
    }

    public static class PersistentHistory extends MemoryHistory {

        private final Preferences prefs;

        protected PersistentHistory(final Preferences prefs) {
            this.prefs = prefs;
            load();
        }

        private static final String HISTORY_LINE_PREFIX = "HISTORY_LINE_";

        public final void load() {
            try {
                final List<String> keys = new ArrayList<>(Arrays.asList(prefs.keys()));
                Collections.sort(keys);
                for (String key : keys) {
                    if (!key.startsWith(HISTORY_LINE_PREFIX))
                        continue;
                    CharSequence line = prefs.get(key, "");
                    add(line);
                }
            } catch (BackingStoreException ex) {
                throw new IllegalStateException(ex);
            }
        }

        public void save() {
            Iterator<Entry> entries = iterator();
            if (entries.hasNext()) {
                int len = (int) Math.ceil(Math.log10(size()+1));
                String format = HISTORY_LINE_PREFIX + "%0" + len + "d";
                while (entries.hasNext()) {
                    Entry entry = entries.next();
                    prefs.put(String.format(format, entry.index()), entry.value().toString());
                }
            }
        }

    }
}
