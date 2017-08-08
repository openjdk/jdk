/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.jline.extra;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Supplier;

import jdk.internal.jline.console.ConsoleReader;
import jdk.internal.jline.console.KeyMap;
import jdk.internal.jline.console.history.History;
import jdk.internal.jline.console.history.History.Entry;
import jdk.internal.jline.console.history.MemoryHistory;

/*Public for tests (HistoryTest).
 */
public abstract class EditingHistory implements History {

    private final History fullHistory;
    private History currentDelegate;

    protected EditingHistory(ConsoleReader in, Iterable<? extends String> originalHistory) {
        MemoryHistory fullHistory = new MemoryHistory();
        fullHistory.setIgnoreDuplicates(false);
        this.fullHistory = fullHistory;
        this.currentDelegate = fullHistory;
        bind(in, CTRL_UP,
             (Runnable) () -> moveHistoryToSnippet(in, ((EditingHistory) in.getHistory())::previousSnippet));
        bind(in, CTRL_DOWN,
             (Runnable) () -> moveHistoryToSnippet(in, ((EditingHistory) in.getHistory())::nextSnippet));
        if (originalHistory != null) {
            load(originalHistory);
        }
    }

    private void moveHistoryToSnippet(ConsoleReader in, Supplier<Boolean> action) {
        if (!action.get()) {
            try {
                in.beep();
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        } else {
            try {
                //could use:
                //in.resetPromptLine(in.getPrompt(), in.getHistory().current().toString(), -1);
                //but that would mean more re-writing on the screen, (and prints an additional
                //empty line), so using setBuffer directly:
                Method setBuffer = ConsoleReader.class.getDeclaredMethod("setBuffer", String.class);

                setBuffer.setAccessible(true);
                setBuffer.invoke(in, in.getHistory().current().toString());
                in.flush();
            } catch (ReflectiveOperationException | IOException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    private void bind(ConsoleReader in, String shortcut, Object action) {
        KeyMap km = in.getKeys();
        for (int i = 0; i < shortcut.length(); i++) {
            Object value = km.getBound(Character.toString(shortcut.charAt(i)));
            if (value instanceof KeyMap) {
                km = (KeyMap) value;
            } else {
                km.bind(shortcut.substring(i), action);
            }
        }
    }

    private static final String CTRL_UP = "\033\133\061\073\065\101"; //Ctrl-UP
    private static final String CTRL_DOWN = "\033\133\061\073\065\102"; //Ctrl-DOWN

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
        if (isComplete(complete)) {
            currentLine.span[1] = fullSize; //TODO: +1?
            currentDelegate = fullHistory;
        }
        fullHistory.add(line);
    }

    protected abstract boolean isComplete(CharSequence input);

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
        while (previous()) {
            if (current() instanceof NarrowingHistoryLine) {
                return true;
            }
        }

        return false;
    }

    public boolean nextSnippet() {
        boolean success = false;

        while (next()) {
            success = true;

            if (current() instanceof NarrowingHistoryLine) {
                return true;
            }
        }

        return success;
    }

    public final void load(Iterable<? extends String> originalHistory) {
        NarrowingHistoryLine currentHistoryLine = null;
        boolean start = true;
        int currentLine = 0;
        for (String historyItem : originalHistory) {
            StringBuilder line = new StringBuilder(historyItem);
            int trailingBackSlashes = countTrailintBackslashes(line);
            boolean continuation = trailingBackSlashes % 2 != 0;
            line.delete(line.length() - trailingBackSlashes / 2 - (continuation ? 1 : 0), line.length());
            if (start) {
                class PersistentNarrowingHistoryLine extends NarrowingHistoryLine implements PersistentEntryMarker {
                    public PersistentNarrowingHistoryLine(CharSequence delegate, int start) {
                        super(delegate, start);
                    }
                }
                fullHistory.add(currentHistoryLine = new PersistentNarrowingHistoryLine(line, currentLine));
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
                fullHistory.add(new PersistentLine(line));
            }
            start = !continuation;
            currentHistoryLine.span[1] = currentLine;
            currentLine++;
        }
    }

    public Collection<? extends String> save() {
        Collection<String> result = new ArrayList<>();
        Iterator<Entry> entries = fullHistory.iterator();

        if (entries.hasNext()) {
            Entry entry = entries.next();
            while (entry != null) {
                StringBuilder historyLine = new StringBuilder(entry.value());
                int trailingBackSlashes = countTrailintBackslashes(historyLine);
                for (int i = 0; i < trailingBackSlashes; i++) {
                    historyLine.append("\\");
                }
                entry = entries.hasNext() ? entries.next() : null;
                if (entry != null && !(entry.value() instanceof NarrowingHistoryLine)) {
                    historyLine.append("\\");
                }
                result.add(historyLine.toString());
            }
        }

        return result;
    }

    private int countTrailintBackslashes(CharSequence text) {
        int count = 0;

        for (int i = text.length() - 1; i >= 0; i--) {
            if (text.charAt(i) == '\\') {
                count++;
            } else {
                break;
            }
        }

        return count;
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

    public void fullHistoryReplace(String source) {
        fullHistory.removeLast();
        for (String line : source.split("\\R")) {
            fullHistory.add(line);
        }
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

