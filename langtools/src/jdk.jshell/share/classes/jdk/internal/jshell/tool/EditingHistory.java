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

package jdk.internal.jshell.tool;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.internal.jline.console.history.History;
import jdk.internal.jline.console.history.History.Entry;
import jdk.internal.jline.console.history.MemoryHistory;
import jdk.jshell.SourceCodeAnalysis.CompletionInfo;

/*Public for tests (HistoryTest).
 */
public abstract class EditingHistory implements History {

    private final Preferences prefs;
    private final History fullHistory;
    private History currentDelegate;

    protected EditingHistory(Preferences prefs) {
        this.prefs = prefs;
        this.fullHistory = new MemoryHistory();
        this.currentDelegate = fullHistory;
        load();
    }

    @Override
    public int size() {
        return currentDelegate.size();
    }

    @Override
    public boolean isEmpty() {
        return currentDelegate.isEmpty();
    }

    @Override
    public int index() {
        return currentDelegate.index();
    }

    @Override
    public void clear() {
        if (currentDelegate != fullHistory)
            throw new IllegalStateException("narrowed");
        currentDelegate.clear();
    }

    @Override
    public CharSequence get(int index) {
        return currentDelegate.get(index);
    }

    @Override
    public void add(CharSequence line) {
        NarrowingHistoryLine currentLine = null;
        int origIndex = fullHistory.index();
        int fullSize;
        try {
            fullHistory.moveToEnd();
            fullSize = fullHistory.index();
            if (currentDelegate == fullHistory) {
                if (origIndex < fullHistory.index()) {
                    for (Entry entry : fullHistory) {
                        if (!(entry.value() instanceof NarrowingHistoryLine))
                            continue;
                        int[] cluster = ((NarrowingHistoryLine) entry.value()).span;
                        if (cluster[0] == origIndex && cluster[1] > cluster[0]) {
                            currentDelegate = new MemoryHistory();
                            for (int i = cluster[0]; i <= cluster[1]; i++) {
                                currentDelegate.add(fullHistory.get(i));
                            }
                        }
                    }
                }
            }
            fullHistory.moveToEnd();
            while (fullHistory.previous()) {
                CharSequence c = fullHistory.current();
                if (c instanceof NarrowingHistoryLine) {
                    currentLine = (NarrowingHistoryLine) c;
                    break;
                }
            }
        } finally {
            fullHistory.moveTo(origIndex);
        }
        if (currentLine == null || currentLine.span[1] != (-1)) {
            line = currentLine = new NarrowingHistoryLine(line, fullSize);
        }
        StringBuilder complete = new StringBuilder();
        for (int i = currentLine.span[0]; i < fullSize; i++) {
            complete.append(fullHistory.get(i));
        }
        complete.append(line);
        if (analyzeCompletion(complete.toString()).completeness.isComplete) {
            currentLine.span[1] = fullSize; //TODO: +1?
            currentDelegate = fullHistory;
        }
        fullHistory.add(line);
    }

    protected abstract CompletionInfo analyzeCompletion(String input);

    @Override
    public void set(int index, CharSequence item) {
        if (currentDelegate != fullHistory)
            throw new IllegalStateException("narrowed");
        currentDelegate.set(index, item);
    }

    @Override
    public CharSequence remove(int i) {
        if (currentDelegate != fullHistory)
            throw new IllegalStateException("narrowed");
        return currentDelegate.remove(i);
    }

    @Override
    public CharSequence removeFirst() {
        if (currentDelegate != fullHistory)
            throw new IllegalStateException("narrowed");
        return currentDelegate.removeFirst();
    }

    @Override
    public CharSequence removeLast() {
        if (currentDelegate != fullHistory)
            throw new IllegalStateException("narrowed");
        return currentDelegate.removeLast();
    }

    @Override
    public void replace(CharSequence item) {
        if (currentDelegate != fullHistory)
            throw new IllegalStateException("narrowed");
        currentDelegate.replace(item);
    }

    @Override
    public ListIterator<Entry> entries(int index) {
        return currentDelegate.entries(index);
    }

    @Override
    public ListIterator<Entry> entries() {
        return currentDelegate.entries();
    }

    @Override
    public Iterator<Entry> iterator() {
        return currentDelegate.iterator();
    }

    @Override
    public CharSequence current() {
        return currentDelegate.current();
    }

    @Override
    public boolean previous() {
        return currentDelegate.previous();
    }

    @Override
    public boolean next() {
        return currentDelegate.next();
    }

    @Override
    public boolean moveToFirst() {
        return currentDelegate.moveToFirst();
    }

    @Override
    public boolean moveToLast() {
        return currentDelegate.moveToLast();
    }

    @Override
    public boolean moveTo(int index) {
        return currentDelegate.moveTo(index);
    }

    @Override
    public void moveToEnd() {
        currentDelegate.moveToEnd();
    }

    public boolean previousSnippet() {
        for (int i = index() - 1; i >= 0; i--) {
            if (get(i) instanceof NarrowingHistoryLine) {
                moveTo(i);
                return true;
            }
        }

        return false;
    }

    public boolean nextSnippet() {
        for (int i = index() + 1; i < size(); i++) {
            if (get(i) instanceof NarrowingHistoryLine) {
                moveTo(i);
                return true;
            }
        }

        if (index() < size()) {
            moveToEnd();
            return true;
        }

        return false;
    }

    private static final String HISTORY_LINE_PREFIX = "HISTORY_LINE_";
    private static final String HISTORY_SNIPPET_START = "HISTORY_SNIPPET";

    public final void load() {
        try {
            Set<Integer> snippetsStart = new HashSet<>();
            for (String start : prefs.get(HISTORY_SNIPPET_START, "").split(";")) {
                if (!start.isEmpty())
                    snippetsStart.add(Integer.parseInt(start));
            }
            List<String> keys = Stream.of(prefs.keys()).sorted().collect(Collectors.toList());
            NarrowingHistoryLine currentHistoryLine = null;
            int currentLine = 0;
            for (String key : keys) {
                if (!key.startsWith(HISTORY_LINE_PREFIX))
                    continue;
                CharSequence line = prefs.get(key, "");
                if (snippetsStart.contains(currentLine)) {
                    class PersistentNarrowingHistoryLine extends NarrowingHistoryLine implements PersistentEntryMarker {
                        public PersistentNarrowingHistoryLine(CharSequence delegate, int start) {
                            super(delegate, start);
                        }
                    }
                    line = currentHistoryLine = new PersistentNarrowingHistoryLine(line, currentLine);
                } else {
                    class PersistentLine implements CharSequence, PersistentEntryMarker {
                        private final CharSequence delegate;
                        public PersistentLine(CharSequence delegate) {
                            this.delegate = delegate;
                        }
                        @Override public int length() {
                            return delegate.length();
                        }
                        @Override public char charAt(int index) {
                            return delegate.charAt(index);
                        }
                        @Override public CharSequence subSequence(int start, int end) {
                            return delegate.subSequence(start, end);
                        }
                        @Override public String toString() {
                            return delegate.toString();
                        }
                    }
                    line = new PersistentLine(line);
                }
                if (currentHistoryLine != null)
                    currentHistoryLine.span[1] = currentLine;
                currentLine++;
                fullHistory.add(line);
            }
            currentLine = 0;
        } catch (BackingStoreException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public void save() {
        try {
            for (String key : prefs.keys()) {
                if (key.startsWith(HISTORY_LINE_PREFIX))
                    prefs.remove(key);
            }
            Iterator<Entry> entries = fullHistory.iterator();
            if (entries.hasNext()) {
                int len = (int) Math.ceil(Math.log10(fullHistory.size()+1));
                String format = HISTORY_LINE_PREFIX + "%0" + len + "d";
                StringBuilder snippetStarts = new StringBuilder();
                String snippetStartDelimiter = "";
                while (entries.hasNext()) {
                    Entry entry = entries.next();
                    prefs.put(String.format(format, entry.index()), entry.value().toString());
                    if (entry.value() instanceof NarrowingHistoryLine) {
                        snippetStarts.append(snippetStartDelimiter);
                        snippetStarts.append(entry.index());
                        snippetStartDelimiter = ";";
                    }
                }
                prefs.put(HISTORY_SNIPPET_START, snippetStarts.toString());
            }
        } catch (BackingStoreException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public List<String> currentSessionEntries() {
        List<String> result = new ArrayList<>();

        for (Entry e : fullHistory) {
            if (!(e.value() instanceof PersistentEntryMarker)) {
                result.add(e.value().toString());
            }
        }

        return result;
    }

    void fullHistoryReplace(String source) {
        fullHistory.replace(source);
    }

    private class NarrowingHistoryLine implements CharSequence {
        private final CharSequence delegate;
        private final int[] span;

        public NarrowingHistoryLine(CharSequence delegate, int start) {
            this.delegate = delegate;
            this.span = new int[] {start, -1};
        }

        @Override
        public int length() {
            return delegate.length();
        }

        @Override
        public char charAt(int index) {
            return delegate.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return delegate.subSequence(start, end);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }

    }

    private interface PersistentEntryMarker {}
}

