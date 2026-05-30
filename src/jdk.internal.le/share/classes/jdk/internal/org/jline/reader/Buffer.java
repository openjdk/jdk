/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.reader;

/**
 * Represents the editable text buffer in the LineReader.
 * <p>
 * The Buffer interface provides methods for manipulating the text that the user
 * is currently editing in the LineReader. It supports operations such as cursor
 * movement, text insertion and deletion, and content retrieval.
 * <p>
 * The buffer maintains a current cursor position that indicates where text will
 * be inserted or deleted. Many of the methods in this interface operate relative
 * to this cursor position.
 * <p>
 * The default implementation is {@link org.jline.reader.impl.BufferImpl}.
 *
 * @see LineReader#getBuffer()
 * @see org.jline.reader.impl.BufferImpl
 */
public interface Buffer {

    /*
     * Read access
     */

    /**
     * Returns the current cursor position in the buffer.
     *
     * @return the current cursor position (0-based index)
     */
    int cursor();

    /**
     * Returns the character at the specified position in the buffer.
     *
     * @param i the position to check
     * @return the character at the specified position, or -1 if the position is invalid
     */
    int atChar(int i);

    /**
     * Returns the length of the buffer.
     *
     * @return the number of characters in the buffer
     */
    int length();

    /**
     * Returns the character at the current cursor position.
     *
     * @return the character at the cursor position, or -1 if the cursor is at the end of the buffer
     */
    int currChar();

    /**
     * Returns the character before the current cursor position.
     *
     * @return the character before the cursor position, or -1 if the cursor is at the beginning of the buffer
     */
    int prevChar();

    /**
     * Returns the character after the current cursor position.
     *
     * @return the character after the cursor position, or -1 if the cursor is at the end of the buffer
     */
    int nextChar();

    /*
     * Movement
     */

    /**
     * Moves the cursor to the specified position.
     *
     * @param position the position to move the cursor to
     * @return true if the cursor was moved, false if the position was invalid
     */
    boolean cursor(int position);

    /**
     * Moves the cursor by the specified number of characters.
     * Positive values move right, negative values move left.
     *
     * @param num the number of characters to move
     * @return the number of positions actually moved
     */
    int move(int num);

    /**
     * Moves the cursor up one line while maintaining the same column position if possible.
     * This is used for multi-line editing.
     *
     * @return true if the cursor was moved, false if it was already at the first line
     */
    boolean up();

    /**
     * Moves the cursor down one line while maintaining the same column position if possible.
     * This is used for multi-line editing.
     *
     * @return true if the cursor was moved, false if it was already at the last line
     */
    boolean down();

    /**
     * Moves the cursor by the specified number of columns and rows.
     * This is used for multi-line editing.
     *
     * @param dx the number of columns to move (positive for right, negative for left)
     * @param dy the number of rows to move (positive for down, negative for up)
     * @return true if the cursor was moved, false otherwise
     */
    boolean moveXY(int dx, int dy);

    /*
     * Modification
     */

    /**
     * Clears the buffer content.
     *
     * @return true if the buffer was modified
     */
    boolean clear();

    /**
     * Replaces the character at the current cursor position.
     *
     * @param c the character to set at the current position
     * @return true if the buffer was modified
     */
    boolean currChar(int c);

    /**
     * Writes a character at the current cursor position and advances the cursor.
     *
     * @param c the character to write
     */
    void write(int c);

    /**
     * Writes a character at the current cursor position and advances the cursor.
     *
     * @param c the character to write
     * @param overTyping if true, overwrites the character at the current position
     */
    void write(int c, boolean overTyping);

    /**
     * Writes a string at the current cursor position and advances the cursor.
     *
     * @param str the string to write
     */
    void write(CharSequence str);

    /**
     * Writes a string at the current cursor position and advances the cursor.
     *
     * @param str the string to write
     * @param overTyping if true, overwrites characters at the current position
     */
    void write(CharSequence str, boolean overTyping);

    /**
     * Deletes the character before the cursor position.
     *
     * @return true if the buffer was modified
     */
    boolean backspace();

    /**
     * Deletes multiple characters before the cursor position.
     *
     * @param num the number of characters to delete
     * @return the number of characters actually deleted
     */
    int backspace(int num);

    /**
     * Deletes the character at the cursor position.
     *
     * @return true if the buffer was modified
     */
    boolean delete();

    /**
     * Deletes multiple characters starting at the cursor position.
     *
     * @param num the number of characters to delete
     * @return the number of characters actually deleted
     */
    int delete(int num);

    /*
     * String
     */

    /**
     * Returns a substring of the buffer from the specified start position to the end.
     *
     * @param start the start index, inclusive
     * @return the substring
     */
    String substring(int start);

    /**
     * Returns a substring of the buffer from the specified start position to the specified end position.
     *
     * @param start the start index, inclusive
     * @param end the end index, exclusive
     * @return the substring
     */
    String substring(int start, int end);

    /**
     * Returns a substring of the buffer from the beginning to the current cursor position.
     *
     * @return the substring
     */
    String upToCursor();

    String toString();

    /*
     * Copy
     */

    /**
     * Creates a copy of this buffer.
     *
     * @return a new buffer with the same content and cursor position
     */
    Buffer copy();

    /**
     * Copies the content and cursor position from another buffer.
     *
     * @param buffer the buffer to copy from
     */
    void copyFrom(Buffer buffer);

    /**
     * Clear any internal buffer.
     */
    void zeroOut();
}
