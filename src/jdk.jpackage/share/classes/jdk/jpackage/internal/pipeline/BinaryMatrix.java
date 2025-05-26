/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.pipeline;

import java.util.BitSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class BinaryMatrix {

    BinaryMatrix(BinaryMatrix other) {
        rows = other.rows;
        columns = other.columns;
        size = other.size;
        values = (BitSet)other.values.clone();
    }

    BinaryMatrix(int dimension) {
        this(dimension, dimension);
    }

    BinaryMatrix(int rows, int columns) {
        this(rows, columns, null);
    }

    BinaryMatrix(int rows, int columns, BitSet values) {
        this.rows = requirePositiveInteger(rows, "Number of rows must be positive integer");
        this.columns = requirePositiveInteger(columns, "Number of columns must be positive integer");
        size = rows * columns;
        this.values = Optional.ofNullable(values).orElseGet(() -> new BitSet(size));
    }

    @Override
    public int hashCode() {
        return Objects.hash(columns, rows, values);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        BinaryMatrix other = (BinaryMatrix) obj;
        return columns == other.columns && rows == other.rows && Objects.equals(values, other.values);
    }

    interface Cursor {
        int row();

        int column();

        boolean value();

        void value(boolean value);
    }

    boolean isSquare() {
        return columns == rows;
    }

    boolean isEmpty() {
        return values.isEmpty();
    }

    int getRowCount() {
        return rows;
    }

    int getColumnCount() {
        return columns;
    }

    Iterator<Cursor> getRowIterator(int row) {
        return new RowIterator(row);
    }

    Iterator<Cursor> getColumnIterator(int column) {
        return new ColumnIterator(column);
    }

    Spliterator<Cursor> getRowSpliterator(int row) {
        return Spliterators.spliterator(getRowIterator(row), columns, Spliterator.ORDERED);
    }

    Spliterator<Cursor> getColumnSpliterator(int column) {
        return Spliterators.spliterator(getColumnIterator(column), rows, Spliterator.ORDERED);
    }

    Stream<Cursor> getRowAsStream(int row) {
        return toStream(getRowSpliterator(row));
    }

    Stream<Cursor> getColumnAsStream(int column) {
        return toStream(getColumnSpliterator(column));
    }

    boolean isSet(int row, int column) {
        return values.get(toIndex(row, column));
    }

    void set(int row, int column, boolean value) {
        values.set(toIndex(row, column), value);
    }

    void set(int row, int column) {
        set(row, column, true);
    }

    void unset(int row, int column) {
        set(row, column, false);
    }

    private int toIndex(int row, int column) {
        Objects.checkIndex(row, rows);
        Objects.checkIndex(column, columns);
        return row * columns + column;
    }

    private static int requirePositiveInteger(int value, String message) {
        Objects.requireNonNull(message);
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static Stream<Cursor> toStream(Spliterator<Cursor> split) {
        return StreamSupport.stream(split, false);
    }

    /**
     * Iterator over values of some selection.
     */
    private abstract class SelectionIterator implements Iterator<Cursor> {
        SelectionIterator(int index, int limit) {
            this.limit = Objects.checkIndex(limit, size + 1);
            this.index = Objects.checkIndex(index, limit);
        }

        @Override
        public final boolean hasNext() {
            return index < limit;
        }

        @Override
        public final Cursor next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            final var value = new CursorImpl(index, BinaryMatrix.this);

            index = nextIndex(index);

            return value;
        }

        protected abstract int nextIndex(int idx);

        private final int limit;

        private int index;
    }

    /**
     * Iterator over values of some column.
     */
    private class ColumnIterator extends SelectionIterator {
        ColumnIterator(int column) {
            super(toIndex(0, column), size);
        }

        @Override
        protected int nextIndex(int idx) {
            return idx + columns;
        }
    }

    /**
     * Iterator over values of some row.
     */
    private class RowIterator extends SelectionIterator {
        RowIterator(int row) {
            super(toIndex(row, 0), (row + 1) * columns);
        }

        @Override
        protected int nextIndex(int idx) {
            return idx + 1;
        }
    }

    private record CursorImpl(int index, BinaryMatrix matrix) implements Cursor {

        CursorImpl {
            Objects.checkIndex(index, matrix.size);
        }

        @Override
        public int row() {
            return index / matrix.columns;
        }

        @Override
        public int column() {
            return index % matrix.columns;
        }

        @Override
        public boolean value() {
            return matrix.values.get(index);
        }

        @Override
        public void value(boolean value) {
            matrix.values.set(index, value);
        }

    }

    private final int rows;
    private final int columns;
    private final int size;
    private final BitSet values;
}
