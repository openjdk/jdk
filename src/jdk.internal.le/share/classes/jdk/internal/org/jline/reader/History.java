/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.reader;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Iterator;
import java.util.ListIterator;

/**
 * Console command history management interface.
 * <p>
 * The History interface provides functionality for storing, retrieving, and navigating
 * through previously entered commands. It allows users to recall and reuse commands
 * they've typed before, which is a fundamental feature of interactive command-line
 * interfaces.
 * <p>
 * History implementations typically support:
 * <ul>
 *   <li>Adding new entries as commands are executed</li>
 *   <li>Navigating backward and forward through history</li>
 *   <li>Persisting history to a file for use across sessions</li>
 *   <li>Filtering or ignoring certain commands based on patterns</li>
 * </ul>
 * <p>
 * Each history entry contains the command text along with metadata such as the
 * timestamp when it was executed.
 * <p>
 * The default implementation is {@link org.jline.reader.impl.history.DefaultHistory}.
 *
 * @since 2.3
 * @see LineReader#getHistory()
 * @see LineReaderBuilder#history(History)
 */
public interface History extends Iterable<History.Entry> {

    /**
     * Initialize the history for the given reader.
     * @param reader the reader to attach to
     */
    void attach(LineReader reader);

    /**
     * Load history.
     * @throws IOException if a problem occurs
     */
    void load() throws IOException;

    /**
     * Save history.
     * @throws IOException if a problem occurs
     */
    void save() throws IOException;

    /**
     * Write history to the file. If incremental only the events that are new since the last incremental operation to
     * the file are added.
     * @param  file        History file
     * @param  incremental If true incremental write operation is performed.
     * @throws IOException if a problem occurs
     */
    void write(Path file, boolean incremental) throws IOException;

    /**
     * Append history to the file. If incremental only the events that are new since the last incremental operation to
     * the file are added.
     * @param  file        History file
     * @param  incremental If true incremental append operation is performed.
     * @throws IOException if a problem occurs
     */
    void append(Path file, boolean incremental) throws IOException;

    /**
     * Read history from the file. If checkDuplicates is <code>true</code> only the events that
     * are not contained within the internal list are added.
     * @param  file        History file
     * @param  checkDuplicates If <code>true</code>, duplicate history entries will be discarded
     * @throws IOException if a problem occurs
     */
    void read(Path file, boolean checkDuplicates) throws IOException;

    /**
     * Purge history.
     * @throws IOException if a problem occurs
     */
    void purge() throws IOException;

    /**
     * Returns the number of items in the history.
     *
     * @return the number of history items
     */
    int size();

    /**
     * Checks if the history is empty.
     *
     * @return true if the history contains no items
     */
    default boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Returns the current index in the history.
     *
     * @return the current index
     */
    int index();

    /**
     * Returns the index of the first element in the history.
     *
     * @return the index of the first history item
     */
    int first();

    /**
     * Returns the index of the last element in the history.
     *
     * @return the index of the last history item
     */
    int last();

    /**
     * Returns the history item at the specified index.
     *
     * @param index the index of the history item to retrieve
     * @return the history item at the specified index
     */
    String get(int index);

    default void add(String line) {
        add(Instant.now(), line);
    }

    /**
     * Adds a new item to the history with the specified timestamp.
     *
     * @param time the timestamp for the history item
     * @param line the line to add to the history
     */
    void add(Instant time, String line);

    /**
     * Check if an entry should be persisted or not.
     *
     * @param entry the entry to check
     * @return <code>true</code> if the given entry should be persisted, <code>false</code> otherwise
     */
    default boolean isPersistable(Entry entry) {
        return true;
    }

    //
    // Entries
    //

    /**
     * Represents a single history entry containing a command line and its metadata.
     * <p>
     * Each entry in the history has an index position, a timestamp indicating when
     * it was added, and the actual command line text.
     */
    interface Entry {
        /**
         * Returns the index of this entry in the history.
         *
         * @return the index position of this entry
         */
        int index();

        /**
         * Returns the timestamp when this entry was added to the history.
         *
         * @return the timestamp of this entry
         */
        Instant time();

        /**
         * Returns the command line text of this entry.
         *
         * @return the command line text
         */
        String line();
    }

    /**
     * Returns a list iterator over the history entries starting at the specified index.
     *
     * @param index the index to start iterating from
     * @return a list iterator over the history entries
     */
    ListIterator<Entry> iterator(int index);

    default ListIterator<Entry> iterator() {
        return iterator(first());
    }

    default Iterator<Entry> reverseIterator() {
        return reverseIterator(last());
    }

    default Iterator<Entry> reverseIterator(int index) {
        return new Iterator<Entry>() {
            private final ListIterator<Entry> it = iterator(index + 1);

            @Override
            public boolean hasNext() {
                return it.hasPrevious();
            }

            @Override
            public Entry next() {
                return it.previous();
            }

            @Override
            public void remove() {
                it.remove();
                resetIndex();
            }
        };
    }

    //
    // Navigation
    //

    /**
     * Return the content of the current buffer.
     *
     * @return the content of the current buffer
     */
    String current();

    /**
     * Move the pointer to the previous element in the buffer.
     *
     * @return true if we successfully went to the previous element
     */
    boolean previous();

    /**
     * Move the pointer to the next element in the buffer.
     *
     * @return true if we successfully went to the next element
     */
    boolean next();

    /**
     * Moves the history index to the first entry.
     *
     * @return Return false if there are no iterator in the history or if the
     * history is already at the beginning.
     */
    boolean moveToFirst();

    /**
     * This moves the history to the last entry. This entry is one position
     * before the moveToEnd() position.
     *
     * @return Returns false if there were no history iterator or the history
     * index was already at the last entry.
     */
    boolean moveToLast();

    /**
     * Move to the specified index in the history
     *
     * @param index The index to move to.
     * @return      Returns true if the index was moved.
     */
    boolean moveTo(int index);

    /**
     * Move to the end of the history buffer. This will be a blank entry, after
     * all of the other iterator.
     */
    void moveToEnd();

    /**
     * Reset index after remove
     */
    void resetIndex();
}
