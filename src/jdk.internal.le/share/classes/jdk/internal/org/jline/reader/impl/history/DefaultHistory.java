/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.reader.impl.history;

import java.io.*;
import java.nio.file.*;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.*;

import jdk.internal.org.jline.reader.History;
import jdk.internal.org.jline.reader.LineReader;
import jdk.internal.org.jline.utils.Log;

import static jdk.internal.org.jline.reader.LineReader.HISTORY_IGNORE;
import static jdk.internal.org.jline.reader.impl.ReaderUtils.*;

/**
 * Default implementation of {@link History} with file-based persistent storage.
 * <p>
 * This class provides a complete implementation of the History interface with the following features:
 * <ul>
 *   <li>In-memory storage of history entries with configurable size limits</li>
 *   <li>Persistent storage in a text file with configurable location and size limits</li>
 *   <li>Support for timestamped history entries</li>
 *   <li>Filtering of entries based on patterns defined in the {@link LineReader#HISTORY_IGNORE} variable</li>
 *   <li>Options to ignore duplicates, reduce blanks, and ignore commands starting with spaces</li>
 *   <li>Incremental saving of history entries</li>
 *   <li>History navigation (previous/next, first/last, etc.)</li>
 * </ul>
 * <p>
 * The history file format is either plain text with one command per line, or if
 * {@link LineReader.Option#HISTORY_TIMESTAMPED} is set, each line starts with a timestamp
 * in milliseconds since epoch, followed by a colon and the command text.
 * <p>
 * Applications using this class should install a shutdown hook to call {@link DefaultHistory#save}
 * to ensure history is saved to disk when the application exits.
 * <p>
 * Example usage:
 * <pre>
 * LineReader reader = LineReaderBuilder.builder()
 *     .variable(LineReader.HISTORY_FILE, Paths.get(System.getProperty("user.home"), ".myapp_history"))
 *     .build();
 * // History is automatically attached to the reader
 *
 * // To save history manually:
 * ((DefaultHistory) reader.getHistory()).save();
 * </pre>
 *
 * @see History
 * @see LineReader#HISTORY_FILE
 * @see LineReader#HISTORY_SIZE
 * @see LineReader#HISTORY_FILE_SIZE
 * @see LineReader.Option#HISTORY_TIMESTAMPED
 * @see LineReader.Option#HISTORY_IGNORE_SPACE
 * @see LineReader.Option#HISTORY_IGNORE_DUPS
 * @see LineReader.Option#HISTORY_REDUCE_BLANKS
 */
public class DefaultHistory implements History {

    /**
     * Default maximum number of history entries to keep in memory.
     * This value is used when the {@link LineReader#HISTORY_SIZE} variable is not set.
     */
    public static final int DEFAULT_HISTORY_SIZE = 500;

    /**
     * Default maximum number of history entries to keep in the history file.
     * This value is used when the {@link LineReader#HISTORY_FILE_SIZE} variable is not set.
     */
    public static final int DEFAULT_HISTORY_FILE_SIZE = 10000;

    private final LinkedList<Entry> items = new LinkedList<>();

    private LineReader reader;

    private Map<String, HistoryFileData> historyFiles = new HashMap<>();
    private int offset = 0;
    private int index = 0;

    /**
     * Creates a new DefaultHistory instance.
     */
    public DefaultHistory() {}

    /**
     * Creates a new DefaultHistory instance and attaches it to the specified LineReader.
     *
     * @param reader the LineReader to attach to
     */
    @SuppressWarnings("this-escape")
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

    /**
     * Attaches this history to a LineReader.
     * <p>
     * This method associates the history with a LineReader and loads the history
     * from the file specified by the {@link LineReader#HISTORY_FILE} variable.
     * If the history is already attached to the given reader, this method does nothing.
     *
     * @param reader the LineReader to attach this history to
     */
    @Override
    public void attach(LineReader reader) {
        if (this.reader != reader) {
            this.reader = reader;
            try {
                load();
            } catch (IllegalArgumentException | IOException e) {
                Log.warn("Failed to load history", e);
            }
        }
    }

    /**
     * Loads history from the file specified by the {@link LineReader#HISTORY_FILE} variable.
     * <p>
     * This method clears the current history and loads entries from the history file.
     * If the file doesn't exist or can't be read, the history will be cleared.
     * If individual lines in the history file are malformed, they will be skipped.
     *
     * @throws IOException if an I/O error occurs while reading the history file
     */
    @Override
    public void load() throws IOException {
        Path path = getPath();
        if (path != null) {
            try {
                if (Files.exists(path)) {
                    Log.trace("Loading history from: ", path);
                    internalClear();
                    boolean hasErrors = false;

                    try (BufferedReader reader = Files.newBufferedReader(path)) {
                        List<String> lines = reader.lines().collect(java.util.stream.Collectors.toList());
                        for (String line : lines) {
                            try {
                                addHistoryLine(path, line);
                            } catch (IllegalArgumentException e) {
                                Log.debug("Skipping invalid history line: " + line, e);
                                hasErrors = true;
                            }
                        }
                    }

                    setHistoryFileData(path, new HistoryFileData(items.size(), offset + items.size()));
                    maybeResize();

                    // If we encountered errors, rewrite the history file with valid entries
                    if (hasErrors) {
                        Log.info("History file contained errors, rewriting with valid entries");
                        write(path, false);
                    }
                }
            } catch (IOException e) {
                Log.debug("Failed to load history; clearing", e);
                internalClear();
                throw e;
            }
        }
    }

    /**
     * Reads history entries from the specified file and adds them to the current history.
     * <p>
     * Unlike {@link #load()}, this method does not clear the existing history before
     * adding entries from the file. If the file doesn't exist or can't be read,
     * the history will be cleared. If individual lines in the history file are malformed,
     * they will be skipped.
     *
     * @param file the file to read history from, or null to use the default history file
     * @param checkDuplicates whether to check for and skip duplicate entries
     * @throws IOException if an I/O error occurs while reading the history file
     */
    @Override
    public void read(Path file, boolean checkDuplicates) throws IOException {
        Path path = file != null ? file : getPath();
        if (path != null) {
            try {
                if (Files.exists(path)) {
                    Log.trace("Reading history from: ", path);
                    boolean hasErrors = false;

                    try (BufferedReader reader = Files.newBufferedReader(path)) {
                        List<String> lines = reader.lines().collect(java.util.stream.Collectors.toList());
                        for (String line : lines) {
                            try {
                                addHistoryLine(path, line, checkDuplicates);
                            } catch (IllegalArgumentException e) {
                                Log.debug("Skipping invalid history line: " + line, e);
                                hasErrors = true;
                            }
                        }
                    }

                    setHistoryFileData(path, new HistoryFileData(items.size(), offset + items.size()));
                    maybeResize();

                    // If we encountered errors, rewrite the history file with valid entries
                    if (hasErrors) {
                        Log.info("History file contained errors, rewriting with valid entries");
                        write(path, false);
                    }
                }
            } catch (IOException e) {
                Log.debug("Failed to read history; clearing", e);
                internalClear();
                throw e;
            }
        }
    }

    private String doHistoryFileDataKey(Path path) {
        return path != null ? path.toAbsolutePath().toString() : null;
    }

    private HistoryFileData getHistoryFileData(Path path) {
        String key = doHistoryFileDataKey(path);
        if (!historyFiles.containsKey(key)) {
            historyFiles.put(key, new HistoryFileData());
        }
        return historyFiles.get(key);
    }

    private void setHistoryFileData(Path path, HistoryFileData historyFileData) {
        historyFiles.put(doHistoryFileDataKey(path), historyFileData);
    }

    private boolean isLineReaderHistory(Path path) throws IOException {
        Path lrp = getPath();
        if (lrp == null) {
            return path == null;
        }
        return Files.isSameFile(lrp, path);
    }

    private void setLastLoaded(Path path, int lastloaded) {
        getHistoryFileData(path).setLastLoaded(lastloaded);
    }

    private void setEntriesInFile(Path path, int entriesInFile) {
        getHistoryFileData(path).setEntriesInFile(entriesInFile);
    }

    private void incEntriesInFile(Path path, int amount) {
        getHistoryFileData(path).incEntriesInFile(amount);
    }

    private int getLastLoaded(Path path) {
        return getHistoryFileData(path).getLastLoaded();
    }

    private int getEntriesInFile(Path path) {
        return getHistoryFileData(path).getEntriesInFile();
    }

    /**
     * Adds a history line to the specified history file.
     *
     * @param path the path to the history file
     * @param line the line to add
     */
    protected void addHistoryLine(Path path, String line) {
        addHistoryLine(path, line, false);
    }

    /**
     * Adds a history line to the specified history file with an option to check for duplicates.
     *
     * @param path the path to the history file
     * @param line the line to add
     * @param checkDuplicates whether to check for duplicate entries
     */
    protected void addHistoryLine(Path path, String line, boolean checkDuplicates) {
        if (reader.isSet(LineReader.Option.HISTORY_TIMESTAMPED)) {
            int idx = line.indexOf(':');
            final String badHistoryFileSyntax = "Bad history file syntax! " + "The history file `" + path
                    + "` may be an older history: " + "please remove it or use a different history file.";
            if (idx < 0) {
                throw new IllegalArgumentException(badHistoryFileSyntax);
            }
            Instant time;
            try {
                time = Instant.ofEpochMilli(Long.parseLong(line.substring(0, idx)));
            } catch (DateTimeException | NumberFormatException e) {
                throw new IllegalArgumentException(badHistoryFileSyntax);
            }

            String unescaped = unescape(line.substring(idx + 1));
            internalAdd(time, unescaped, checkDuplicates);
        } else {
            internalAdd(Instant.now(), unescape(line), checkDuplicates);
        }
    }

    /**
     * Clears the history and deletes the history file.
     * <p>
     * This method removes all history entries from memory and deletes the history file
     * if it exists.
     *
     * @throws IOException if an I/O error occurs while deleting the history file
     */
    @Override
    public void purge() throws IOException {
        internalClear();
        Path path = getPath();
        if (path != null) {
            Log.trace("Purging history from: ", path);
            Files.deleteIfExists(path);
        }
    }

    /**
     * Writes the history to the specified file, optionally replacing the existing file.
     * <p>
     * If the file exists, it will be deleted and recreated. If incremental is true,
     * only entries that haven't been saved before will be written.
     *
     * @param file the file to write history to, or null to use the default history file
     * @param incremental whether to write only new entries (true) or all entries (false)
     * @throws IOException if an I/O error occurs while writing the history file
     */
    @Override
    public void write(Path file, boolean incremental) throws IOException {
        Path path = file != null ? file : getPath();
        if (path != null && Files.exists(path)) {
            Files.deleteIfExists(path);
        }
        internalWrite(path, incremental ? getLastLoaded(path) : 0);
    }

    /**
     * Appends history entries to the specified file.
     * <p>
     * Unlike {@link #write(Path, boolean)}, this method does not delete the existing file
     * before writing. If incremental is true, only entries that haven't been saved before
     * will be appended.
     *
     * @param file the file to append history to, or null to use the default history file
     * @param incremental whether to append only new entries (true) or all entries (false)
     * @throws IOException if an I/O error occurs while appending to the history file
     */
    @Override
    public void append(Path file, boolean incremental) throws IOException {
        internalWrite(file != null ? file : getPath(), incremental ? getLastLoaded(file) : 0);
    }

    /**
     * Saves the history to the default history file.
     * <p>
     * This method appends any new history entries (those that haven't been saved before)
     * to the history file. It's typically called when the application exits to ensure
     * that history is preserved.
     *
     * @throws IOException if an I/O error occurs while saving the history file
     */
    @Override
    public void save() throws IOException {
        internalWrite(getPath(), getLastLoaded(getPath()));
    }

    private void internalWrite(Path path, int from) throws IOException {
        if (path != null) {
            Log.trace("Saving history to: ", path);
            Path parent = path.toAbsolutePath().getParent();
            if (!Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            // Append new items to the history file
            try (BufferedWriter writer = Files.newBufferedWriter(
                    path.toAbsolutePath(),
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.CREATE)) {
                for (Entry entry : items.subList(from, items.size())) {
                    if (isPersistable(entry)) {
                        writer.append(format(entry));
                    }
                }
            }
            incEntriesInFile(path, items.size() - from);
            int max = getInt(reader, LineReader.HISTORY_FILE_SIZE, DEFAULT_HISTORY_FILE_SIZE);
            if (getEntriesInFile(path) > max + max / 4) {
                trimHistory(path, max);
            }
        }
        setLastLoaded(path, items.size());
    }

    /**
     * Trims the history file to the specified maximum number of entries.
     *
     * @param path the path to the history file
     * @param max the maximum number of entries to keep
     * @throws IOException if an I/O error occurs
     */
    protected void trimHistory(Path path, int max) throws IOException {
        Log.trace("Trimming history path: ", path);
        // Load all history entries
        LinkedList<Entry> allItems = new LinkedList<>();
        try (BufferedReader historyFileReader = Files.newBufferedReader(path)) {
            List<String> lines = historyFileReader.lines().collect(java.util.stream.Collectors.toList());
            for (String l : lines) {
                try {
                    if (reader.isSet(LineReader.Option.HISTORY_TIMESTAMPED)) {
                        int idx = l.indexOf(':');
                        if (idx < 0) {
                            Log.debug("Skipping invalid history line: " + l);
                            continue;
                        }
                        try {
                            Instant time = Instant.ofEpochMilli(Long.parseLong(l.substring(0, idx)));
                            String line = unescape(l.substring(idx + 1));
                            allItems.add(createEntry(allItems.size(), time, line));
                        } catch (DateTimeException | NumberFormatException e) {
                            Log.debug("Skipping invalid history timestamp: " + l);
                        }
                    } else {
                        allItems.add(createEntry(allItems.size(), Instant.now(), unescape(l)));
                    }
                } catch (Exception e) {
                    Log.debug("Skipping invalid history line: " + l, e);
                }
            }
        }
        // Remove duplicates
        List<Entry> trimmedItems = doTrimHistory(allItems, max);
        // Write history
        Path temp = Files.createTempFile(
                path.toAbsolutePath().getParent(), path.getFileName().toString(), ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardOpenOption.WRITE)) {
            for (Entry entry : trimmedItems) {
                writer.append(format(entry));
            }
        }
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        // Keep items in memory
        if (isLineReaderHistory(path)) {
            internalClear();
            offset = trimmedItems.get(0).index();
            items.addAll(trimmedItems);
            setHistoryFileData(path, new HistoryFileData(items.size(), items.size()));
        } else {
            setEntriesInFile(path, allItems.size());
        }
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
        historyFiles = new HashMap<>();
        items.clear();
    }

    static List<Entry> doTrimHistory(List<Entry> allItems, int max) {
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
        int index = allItems.get(allItems.size() - 1).index() - allItems.size() + 1;
        List<Entry> out = new ArrayList<>();
        for (Entry e : allItems) {
            out.add(new EntryImpl(index++, e.time(), e.line()));
        }
        return out;
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
            return entry.time().toEpochMilli() + ":" + escape(entry.line()) + "\n";
        }
        return escape(entry.line()) + "\n";
    }

    public String get(final int index) {
        int idx = index - offset;
        if (idx >= items.size() || idx < 0) {
            throw new IllegalArgumentException("IndexOutOfBounds: Index:" + idx + ", Size:" + items.size());
        }
        return items.get(idx).line();
    }

    /**
     * Adds a new entry to the history.
     * <p>
     * This method adds a new entry to the history with the specified timestamp and command text.
     * The entry may be filtered based on various criteria such as:
     * <ul>
     *   <li>If history is disabled ({@link LineReader#DISABLE_HISTORY} is true)</li>
     *   <li>If the line starts with a space and {@link LineReader.Option#HISTORY_IGNORE_SPACE} is set</li>
     *   <li>If the line is a duplicate of the previous entry and {@link LineReader.Option#HISTORY_IGNORE_DUPS} is set</li>
     *   <li>If the line matches a pattern in {@link LineReader#HISTORY_IGNORE}</li>
     * </ul>
     * <p>
     * If {@link LineReader.Option#HISTORY_INCREMENTAL} is set, the history will be saved
     * to disk after adding the entry.
     *
     * @param time the timestamp for the new entry
     * @param line the command text for the new entry
     * @throws NullPointerException if time or line is null
     */
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
            } catch (IOException e) {
                Log.warn("Failed to save history", e);
            }
        }
    }

    /**
     * Checks if a line matches any of the specified patterns.
     *
     * @param patterns the patterns to match against, separated by '|'
     * @param line the line to check
     * @return true if the line matches any of the patterns
     */
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
            } else {
                sb.append(ch);
            }
        }
        return line.matches(sb.toString());
    }

    /**
     * Adds a line to the history with the specified timestamp.
     *
     * @param time the timestamp for the history entry
     * @param line the line to add
     */
    protected void internalAdd(Instant time, String line) {
        internalAdd(time, line, false);
    }

    /**
     * Adds a line to the history with the specified timestamp and an option to check for duplicates.
     *
     * @param time the timestamp for the history entry
     * @param line the line to add
     * @param checkDuplicates whether to check for duplicate entries
     */
    protected void internalAdd(Instant time, String line, boolean checkDuplicates) {
        Entry entry = new EntryImpl(offset + items.size(), time, line);
        if (checkDuplicates) {
            for (Entry e : items) {
                if (e.line().trim().equals(line.trim())) {
                    return;
                }
            }
        }
        items.add(entry);
        maybeResize();
    }

    private void maybeResize() {
        while (size() > getInt(reader, LineReader.HISTORY_SIZE, DEFAULT_HISTORY_SIZE)) {
            items.removeFirst();
            for (HistoryFileData hfd : historyFiles.values()) {
                hfd.decLastLoaded();
            }
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

    public void resetIndex() {
        index = Math.min(index, items.size());
    }

    /**
     * Default implementation of the {@link History.Entry} interface.
     * <p>
     * This class represents a single history entry with an index, timestamp, and command text.
     */
    protected static class EntryImpl implements Entry {

        private final int index;
        private final Instant time;
        private final String line;

        /**
         * Creates a new history entry with the specified index, timestamp, and line.
         *
         * @param index the index of the entry in the history
         * @param time the timestamp of the entry
         * @param line the content of the entry
         */
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
     * Moves the history cursor to the last entry.
     * <p>
     * This positions the cursor at the most recent history entry, which is one position
     * before the position set by {@link #moveToEnd()}. This is typically used when
     * starting to navigate backward through history.
     *
     * @return true if the cursor was moved, false if there were no history entries
     *         or the cursor was already at the last entry
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
     * Moves the history cursor to the specified index.
     * <p>
     * This positions the cursor at the history entry with the given index, if it exists.
     * The index is absolute, taking into account the offset of the history buffer.
     *
     * @param index the absolute index to move to
     * @return true if the cursor was moved, false if the index was out of range
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
     * Moves the history cursor to the first entry.
     * <p>
     * This positions the cursor at the oldest history entry in the buffer.
     * This is typically used when starting to navigate forward through history.
     *
     * @return true if the cursor was moved, false if there were no history entries
     *         or the cursor was already at the first entry
     */
    public boolean moveToFirst() {
        if (size() > 0 && index != 0) {
            index = 0;
            return true;
        }
        return false;
    }

    /**
     * Moves the history cursor to the end of the history buffer.
     * <p>
     * This positions the cursor after the last history entry, which represents
     * the current input line (not yet in history). This is the default position
     * when not navigating through history.
     */
    public void moveToEnd() {
        index = size();
    }

    /**
     * Returns the text of the history entry at the current cursor position.
     * <p>
     * If the cursor is at the end of the history (after the last entry),
     * this method returns an empty string.
     *
     * @return the text of the current history entry, or an empty string if at the end
     */
    public String current() {
        if (index >= size()) {
            return "";
        }
        return items.get(index).line();
    }

    /**
     * Moves the history cursor to the previous (older) entry.
     * <p>
     * This is typically called when the user presses the up arrow key to navigate
     * backward through history. If the cursor is already at the first entry,
     * this method does nothing and returns false.
     *
     * @return true if the cursor was moved, false if already at the first entry
     */
    public boolean previous() {
        if (index <= 0) {
            return false;
        }
        index--;
        return true;
    }

    /**
     * Moves the history cursor to the next (newer) entry.
     * <p>
     * This is typically called when the user presses the down arrow key to navigate
     * forward through history. If the cursor is already at the end of history,
     * this method does nothing and returns false.
     *
     * @return true if the cursor was moved, false if already at the end of history
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

    /**
     * Helper class for tracking history file state.
     * <p>
     * This class maintains information about history files, including how many entries
     * have been loaded from the file and how many entries are currently in the file.
     * This information is used for incremental saving and trimming of history files.
     */
    private static class HistoryFileData {
        private int lastLoaded = 0;
        private int entriesInFile = 0;

        public HistoryFileData() {}

        public HistoryFileData(int lastLoaded, int entriesInFile) {
            this.lastLoaded = lastLoaded;
            this.entriesInFile = entriesInFile;
        }

        public int getLastLoaded() {
            return lastLoaded;
        }

        public void setLastLoaded(int lastLoaded) {
            this.lastLoaded = lastLoaded;
        }

        public void decLastLoaded() {
            lastLoaded = lastLoaded - 1;
            if (lastLoaded < 0) {
                lastLoaded = 0;
            }
        }

        public int getEntriesInFile() {
            return entriesInFile;
        }

        public void setEntriesInFile(int entriesInFile) {
            this.entriesInFile = entriesInFile;
        }

        public void incEntriesInFile(int amount) {
            entriesInFile = entriesInFile + amount;
        }
    }
}
