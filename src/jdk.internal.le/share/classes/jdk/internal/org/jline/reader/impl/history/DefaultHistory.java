/*
 * Copyright (c) 2002-2018, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package jdk.internal.org.jline.reader.impl.history;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

import jdk.internal.org.jline.reader.History;
import jdk.internal.org.jline.reader.LineReader;
import jdk.internal.org.jline.utils.Log;

import static jdk.internal.org.jline.reader.LineReader.HISTORY_IGNORE;
import static jdk.internal.org.jline.reader.impl.ReaderUtils.*;

/**
 * {@link History} using a file for persistent backing.
 * <p>
 * Implementers should install shutdown hook to call {@link DefaultHistory#save}
 * to save history to disk.
 * </p>
 */
public class DefaultHistory implements History {

    public static final int DEFAULT_HISTORY_SIZE = 500;
    public static final int DEFAULT_HISTORY_FILE_SIZE = 10000;

    private final LinkedList<Entry> items = new LinkedList<>();

    private LineReader reader;

    private int lastLoaded = 0;
    private int nbEntriesInFile = 0;
    private int offset = 0;
    private int index = 0;

    public DefaultHistory() {
    }

    public DefaultHistory(LineReader reader) {
        attach(reader);
    }

    private Path getPath() {
        Object obj = reader != null ? reader.getVariables().get(LineReader.HISTORY_FILE) : null;
        if (obj instanceof Path) {
            return (Path) obj;
        } else if (obj instanceof File) {
            return ((File) obj).toPath();
        } else if (obj != null) {
            return Paths.get(obj.toString());
        } else {
            return null;
        }
    }

    @Override
    public void attach(LineReader reader) {
        if (this.reader != reader) {
            this.reader = reader;
            try {
                load();
            }
            catch (IOException e) {
                Log.warn("Failed to load history", e);
            }
        }
    }

    @Override
    public void load() throws IOException {
        Path path = getPath();
        if (path != null) {
            try {
                if (Files.exists(path)) {
                    Log.trace("Loading history from: ", path);
                    try (BufferedReader reader = Files.newBufferedReader(path)) {
                        internalClear();
                        reader.lines().forEach(line -> addHistoryLine(path, line));
                        lastLoaded = items.size();
                        nbEntriesInFile = lastLoaded;
                        maybeResize();
                    }
                }
            } catch (IOException e) {
                Log.debug("Failed to load history; clearing", e);
                internalClear();
                throw e;
            }
        }
    }

    protected void addHistoryLine(Path path, String line) {
        if (reader.isSet(LineReader.Option.HISTORY_TIMESTAMPED)) {
            int idx = line.indexOf(':');
            if (idx < 0) {
                throw new IllegalArgumentException("Bad history file syntax! " +
                        "The history file `" + path + "` may be an older history: " +
                        "please remove it or use a different history file.");
            }
            Instant time = Instant.ofEpochMilli(Long.parseLong(line.substring(0, idx)));
            String unescaped = unescape(line.substring(idx + 1));
            internalAdd(time, unescaped);
        }
        else {
            internalAdd(Instant.now(), unescape(line));
        }
    }

    @Override
    public void purge() throws IOException {
        internalClear();
        Path path = getPath();
        if (path != null) {
            Log.trace("Purging history from: ", path);
            Files.deleteIfExists(path);
        }
    }

    @Override
    public void save() throws IOException {
        Path path = getPath();
        if (path != null) {
            Log.trace("Saving history to: ", path);
            Files.createDirectories(path.toAbsolutePath().getParent());
            // Append new items to the history file
            try (BufferedWriter writer = Files.newBufferedWriter(path.toAbsolutePath(),
              StandardOpenOption.WRITE, StandardOpenOption.APPEND, StandardOpenOption.CREATE)) {
                for (Entry entry : items.subList(lastLoaded, items.size())) {
                    if (isPersistable(entry)) {
                        writer.append(format(entry));
                    }
                }
            }
            nbEntriesInFile += items.size() - lastLoaded;
            // If we are over 25% max size, trim history file
            int max = getInt(reader, LineReader.HISTORY_FILE_SIZE, DEFAULT_HISTORY_FILE_SIZE);
            if (nbEntriesInFile > max + max / 4) {
                trimHistory(path, max);
            }
        }
        lastLoaded = items.size();
    }

    protected void trimHistory(Path path, int max) throws IOException {
        Log.trace("Trimming history path: ", path);
        // Load all history entries
        LinkedList<Entry> allItems = new LinkedList<>();
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            reader.lines().forEach(l -> {
                int idx = l.indexOf(':');
                Instant time = Instant.ofEpochMilli(Long.parseLong(l.substring(0, idx)));
                String line = unescape(l.substring(idx + 1));
                allItems.add(createEntry(allItems.size(), time, line));
            });
        }
        // Remove duplicates
        doTrimHistory(allItems, max);
        // Write history
        Path temp = Files.createTempFile(path.toAbsolutePath().getParent(), path.getFileName().toString(), ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardOpenOption.WRITE)) {
            for (Entry entry : allItems) {
                writer.append(format(entry));
            }
        }
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        // Keep items in memory
        internalClear();
        offset = allItems.get(0).index();
        items.addAll(allItems);
        lastLoaded = items.size();
        nbEntriesInFile = items.size();
        maybeResize();
    }

    /**
     * Create a history entry. Subclasses may override to use their own entry implementations.
     * @param index index of history entry
     * @param time entry creation time
     * @param line the entry text
     * @return entry object
     */
    protected EntryImpl createEntry(int index, Instant time, String line) {
        return new EntryImpl(index, time, line);
    }

    private void internalClear() {
        offset = 0;
        index = 0;
        lastLoaded = 0;
        nbEntriesInFile = 0;
        items.clear();
    }

    static void doTrimHistory(List<Entry> allItems, int max) {
        int idx = 0;
        while (idx < allItems.size()) {
            int ridx = allItems.size() - idx - 1;
            String line = allItems.get(ridx).line().trim();
            ListIterator<Entry> iterator = allItems.listIterator(ridx);
            while (iterator.hasPrevious()) {
                String l = iterator.previous().line();
                if (line.equals(l.trim())) {
                    iterator.remove();
                }
            }
            idx++;
        }
        while (allItems.size() > max) {
            allItems.remove(0);
        }
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int index() {
        return offset + index;
    }

    public int first() {
        return offset;
    }

    public int last() {
        return offset + items.size() - 1;
    }

    private String format(Entry entry) {
        if (reader.isSet(LineReader.Option.HISTORY_TIMESTAMPED)) {
            return Long.toString(entry.time().toEpochMilli()) + ":" + escape(entry.line()) + "\n";
        }
        return escape(entry.line()) + "\n";
    }

    public String get(final int index) {
        return items.get(index - offset).line();
    }

    @Override
    public void add(Instant time, String line) {
        Objects.requireNonNull(time);
        Objects.requireNonNull(line);

        if (getBoolean(reader, LineReader.DISABLE_HISTORY, false)) {
            return;
        }
        if (isSet(reader, LineReader.Option.HISTORY_IGNORE_SPACE) && line.startsWith(" ")) {
            return;
        }
        if (isSet(reader, LineReader.Option.HISTORY_REDUCE_BLANKS)) {
            line = line.trim();
        }
        if (isSet(reader, LineReader.Option.HISTORY_IGNORE_DUPS)) {
            if (!items.isEmpty() && line.equals(items.getLast().line())) {
                return;
            }
        }
        if (matchPatterns(getString(reader, HISTORY_IGNORE, ""), line)) {
            return;
        }
        internalAdd(time, line);
        if (isSet(reader, LineReader.Option.HISTORY_INCREMENTAL)) {
            try {
                save();
            }
            catch (IOException e) {
                Log.warn("Failed to save history", e);
            }
        }
    }

    protected boolean matchPatterns(String patterns, String line) {
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < patterns.length(); i++) {
            char ch = patterns.charAt(i);
            if (ch == '\\') {
                ch = patterns.charAt(++i);
                sb.append(ch);
            } else if (ch == ':') {
                sb.append('|');
            } else if (ch == '*') {
                sb.append('.').append('*');
            }
        }
        return line.matches(sb.toString());
    }

    protected void internalAdd(Instant time, String line) {
        Entry entry = new EntryImpl(offset + items.size(), time, line);
        items.add(entry);
        maybeResize();
    }

    private void maybeResize() {
        while (size() > getInt(reader, LineReader.HISTORY_SIZE, DEFAULT_HISTORY_SIZE)) {
            items.removeFirst();
            lastLoaded--;
            offset++;
        }
        index = size();
    }

    public ListIterator<Entry> iterator(int index) {
        return items.listIterator(index - offset);
    }

    @Override
    public Spliterator<Entry> spliterator() {
        return items.spliterator();
    }

    protected static class EntryImpl implements Entry {

        private final int index;
        private final Instant time;
        private final String line;

        public EntryImpl(int index, Instant time, String line) {
            this.index = index;
            this.time = time;
            this.line = line;
        }

        public int index() {
            return index;
        }

        public Instant time() {
            return time;
        }

        public String line() {
            return line;
        }

        @Override
        public String toString() {
            return String.format("%d: %s", index, line);
        }
    }

    //
    // Navigation
    //

    /**
     * This moves the history to the last entry. This entry is one position
     * before the moveToEnd() position.
     *
     * @return Returns false if there were no history iterator or the history
     * index was already at the last entry.
     */
    public boolean moveToLast() {
        int lastEntry = size() - 1;
        if (lastEntry >= 0 && lastEntry != index) {
            index = size() - 1;
            return true;
        }

        return false;
    }

    /**
     * Move to the specified index in the history
     */
    public boolean moveTo(int index) {
        index -= offset;
        if (index >= 0 && index < size()) {
            this.index = index;
            return true;
        }
        return false;
    }

    /**
     * Moves the history index to the first entry.
     *
     * @return Return false if there are no iterator in the history or if the
     * history is already at the beginning.
     */
    public boolean moveToFirst() {
        if (size() > 0 && index != 0) {
            index = 0;
            return true;
        }
        return false;
    }

    /**
     * Move to the end of the history buffer. This will be a blank entry, after
     * all of the other iterator.
     */
    public void moveToEnd() {
        index = size();
    }

    /**
     * Return the content of the current buffer.
     */
    public String current() {
        if (index >= size()) {
            return "";
        }
        return items.get(index).line();
    }

    /**
     * Move the pointer to the previous element in the buffer.
     *
     * @return true if we successfully went to the previous element
     */
    public boolean previous() {
        if (index <= 0) {
            return false;
        }
        index--;
        return true;
    }

    /**
     * Move the pointer to the next element in the buffer.
     *
     * @return true if we successfully went to the next element
     */
    public boolean next() {
        if (index >= size()) {
            return false;
        }
        index++;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Entry e : this) {
            sb.append(e.toString()).append("\n");
        }
        return sb.toString();
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\n':
                    sb.append('\\');
                    sb.append('n');
                    break;
                case '\r':
                    sb.append('\\');
                    sb.append('r');
                    break;
                case '\\':
                    sb.append('\\');
                    sb.append('\\');
                    break;
                default:
                    sb.append(ch);
                    break;
            }
        }
        return sb.toString();
    }

    static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\\':
                    ch = s.charAt(++i);
                    if (ch == 'n') {
                        sb.append('\n');
                    } else if (ch == 'r') {
                        sb.append('\r');
                    } else {
                        sb.append(ch);
                    }
                    break;
                default:
                    sb.append(ch);
                    break;
            }
        }
        return sb.toString();
    }

}

