/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.terminal;

/**
 * Represents the position of the cursor within a terminal.
 *
 * <p>
 * The Cursor class encapsulates the coordinates of the cursor in a terminal, providing
 * access to its X (column) and Y (row) position. Cursor positions are used for various
 * terminal operations such as text insertion, deletion, and formatting.
 * </p>
 *
 * <p>
 * In terminal coordinates:
 * </p>
 * <ul>
 *   <li><b>X coordinate</b> - Represents the column position (horizontal), typically 0-based</li>
 *   <li><b>Y coordinate</b> - Represents the row position (vertical), typically 0-based</li>
 * </ul>
 *
 * <p>
 * Cursor objects are typically obtained from a {@link Terminal} using the
 * {@link Terminal#getCursorPosition(java.util.function.IntConsumer)} method, which queries
 * the terminal for its current cursor position. This information can be used to determine
 * where text will be inserted or to calculate relative positions for cursor movement.
 * </p>
 *
 * <p>Example usage:</p>
 * <pre>
 * Terminal terminal = TerminalBuilder.terminal();
 *
 * // Get current cursor position
 * Cursor cursor = terminal.getCursorPosition(c -> {});
 * if (cursor != null) {
 *     System.out.println("Cursor position: column=" + cursor.getX() + ", row=" + cursor.getY());
 * }
 * </pre>
 *
 * <p>
 * Note that not all terminals support cursor position reporting. The
 * {@link Terminal#getCursorPosition(java.util.function.IntConsumer)} method may return
 * {@code null} if cursor position reporting is not supported.
 * </p>
 *
 * @see Terminal#getCursorPosition(java.util.function.IntConsumer)
 */
public class Cursor {

    private final int x;
    private final int y;

    /**
     * Creates a new Cursor instance at the specified coordinates.
     *
     * <p>
     * This constructor creates a Cursor object representing a position in the terminal
     * at the given column (x) and row (y) coordinates. In terminal coordinates, the
     * origin (0,0) is typically at the top-left corner of the screen.
     * </p>
     *
     * @param x the column position (horizontal coordinate)
     * @param y the row position (vertical coordinate)
     */
    public Cursor(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Returns the column position (horizontal coordinate) of this cursor.
     *
     * <p>
     * The X coordinate represents the horizontal position of the cursor in the terminal,
     * measured in character cells from the left edge of the terminal. The leftmost column
     * is typically position 0.
     * </p>
     *
     * @return the column position (X coordinate)
     */
    public int getX() {
        return x;
    }

    /**
     * Returns the row position (vertical coordinate) of this cursor.
     *
     * <p>
     * The Y coordinate represents the vertical position of the cursor in the terminal,
     * measured in character cells from the top edge of the terminal. The topmost row
     * is typically position 0.
     * </p>
     *
     * @return the row position (Y coordinate)
     */
    public int getY() {
        return y;
    }

    /**
     * Compares this Cursor object with another object for equality.
     *
     * <p>
     * Two Cursor objects are considered equal if they have the same X and Y coordinates.
     * </p>
     *
     * @param o the object to compare with
     * @return {@code true} if the objects are equal, {@code false} otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (o instanceof Cursor) {
            Cursor c = (Cursor) o;
            return x == c.x && y == c.y;
        } else {
            return false;
        }
    }

    /**
     * Returns a hash code for this Cursor object.
     *
     * <p>
     * The hash code is computed based on the X and Y coordinates.
     * </p>
     *
     * @return a hash code value for this object
     */
    @Override
    public int hashCode() {
        return x * 31 + y;
    }

    /**
     * Returns a string representation of this Cursor object.
     *
     * <p>
     * The string representation includes the X and Y coordinates.
     * </p>
     *
     * @return a string representation of this object
     */
    @Override
    public String toString() {
        return "Cursor[" + "x=" + x + ", y=" + y + ']';
    }
}
