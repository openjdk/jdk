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
 * Represents the dimensions of a terminal in terms of rows and columns.
 *
 * <p>
 * The Size class encapsulates the dimensions of a terminal screen, providing methods to get and set
 * the number of rows and columns. Terminal dimensions are used for various operations such as
 * cursor positioning, screen clearing, and text layout calculations.
 * </p>
 *
 * <p>
 * Terminal dimensions are typically measured in character cells, where:
 * </p>
 * <ul>
 *   <li><b>Columns</b> - The number of character cells in each row (width)</li>
 *   <li><b>Rows</b> - The number of character cells in each column (height)</li>
 * </ul>
 *
 * <p>
 * Size objects are typically obtained from a {@link Terminal} using {@link Terminal#getSize()},
 * and can be used to adjust display formatting or to set the terminal size using
 * {@link Terminal#setSize(Size)}.
 * </p>
 *
 * <p>Example usage:</p>
 * <pre>
 * Terminal terminal = TerminalBuilder.terminal();
 *
 * // Get current terminal size
 * Size size = terminal.getSize();
 * System.out.println("Terminal dimensions: " + size.getColumns() + "x" + size.getRows());
 *
 * // Create a new size and set it
 * Size newSize = new Size(80, 24);
 * terminal.setSize(newSize);
 * </pre>
 *
 * @see Terminal#getSize()
 * @see Terminal#setSize(Size)
 */
public class Size {

    private int rows;
    private int cols;

    /**
     * Creates a new Size instance with default dimensions (0 rows and 0 columns).
     *
     * <p>
     * This constructor creates a Size object with zero dimensions. The dimensions
     * can be set later using {@link #setRows(int)} and {@link #setColumns(int)}.
     * </p>
     */
    public Size() {}

    /**
     * Creates a new Size instance with the specified dimensions.
     *
     * <p>
     * This constructor creates a Size object with the specified number of columns and rows.
     * </p>
     *
     * @param columns the number of columns (width)
     * @param rows the number of rows (height)
     */
    @SuppressWarnings("this-escape")
    public Size(int columns, int rows) {
        this();
        setColumns(columns);
        setRows(rows);
    }

    /**
     * Returns the number of columns (width) in this terminal size.
     *
     * <p>
     * The number of columns represents the width of the terminal in character cells.
     * </p>
     *
     * @return the number of columns
     * @see #setColumns(int)
     */
    public int getColumns() {
        return cols;
    }

    /**
     * Sets the number of columns (width) for this terminal size.
     *
     * <p>
     * The number of columns represents the width of the terminal in character cells.
     * </p>
     *
     * @param columns the number of columns to set
     * @see #getColumns()
     */
    public void setColumns(int columns) {
        cols = (short) columns;
    }

    /**
     * Returns the number of rows (height) in this terminal size.
     *
     * <p>
     * The number of rows represents the height of the terminal in character cells.
     * </p>
     *
     * @return the number of rows
     * @see #setRows(int)
     */
    public int getRows() {
        return rows;
    }

    /**
     * Sets the number of rows (height) for this terminal size.
     *
     * <p>
     * The number of rows represents the height of the terminal in character cells.
     * </p>
     *
     * @param rows the number of rows to set
     * @see #getRows()
     */
    public void setRows(int rows) {
        this.rows = (short) rows;
    }

    /**
     * A cursor position combines a row number with a column position.
     * <p>
     * Note each row has {@code col+1} different column positions,
     * including the right margin.
     * </p>
     *
     * @param col the new column
     * @param row the new row
     * @return the cursor position
     */
    public int cursorPos(int row, int col) {
        return row * (cols + 1) + col;
    }

    /**
     * Copies the dimensions from another Size object to this one.
     *
     * <p>
     * This method updates this Size object to have the same dimensions
     * (rows and columns) as the specified Size object.
     * </p>
     *
     * @param size the Size object to copy dimensions from
     */
    public void copy(Size size) {
        setColumns(size.getColumns());
        setRows(size.getRows());
    }

    /**
     * Compares this Size object with another object for equality.
     *
     * <p>
     * Two Size objects are considered equal if they have the same number of
     * rows and columns.
     * </p>
     *
     * @param o the object to compare with
     * @return {@code true} if the objects are equal, {@code false} otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (o instanceof Size) {
            Size size = (Size) o;
            return rows == size.rows && cols == size.cols;
        } else {
            return false;
        }
    }

    /**
     * Returns a hash code for this Size object.
     *
     * <p>
     * The hash code is computed based on the rows and columns values.
     * </p>
     *
     * @return a hash code value for this object
     */
    @Override
    public int hashCode() {
        return rows * 31 + cols;
    }

    /**
     * Returns a string representation of this Size object.
     *
     * <p>
     * The string representation includes the number of columns and rows.
     * </p>
     *
     * @return a string representation of this object
     */
    @Override
    public String toString() {
        return "Size[" + "cols=" + cols + ", rows=" + rows + ']';
    }
}
