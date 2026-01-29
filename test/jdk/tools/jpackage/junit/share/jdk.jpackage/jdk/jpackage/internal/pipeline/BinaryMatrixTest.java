/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class BinaryMatrixTest {

    record CtorTest(int rows, int columns, boolean isSquare, boolean fail) {

        static CtorTest create(int rows, int columns) {
            return new CtorTest(rows, columns, rows == columns, false);
        }

        static CtorTest createFail(int rows, int columns) {
            return new CtorTest(rows, columns, false, true);
        }

        void test() {
            if (fail) {
                assertThrows(IllegalArgumentException.class, () -> new BinaryMatrix(rows, columns));
            } else {
                final var matrix = new BinaryMatrix(rows, columns);
                assertEquals(rows, matrix.getRowCount());
                assertEquals(columns, matrix.getColumnCount());
                assertEquals(isSquare, matrix.isSquare());
            }
        }
    }

    @ParameterizedTest
    @MethodSource
    public void testCtor(CtorTest testSpec) {
        testSpec.test();
    }

    private static Stream<CtorTest> testCtor() {
        return Stream.of(
                CtorTest.create(1, 2),
                CtorTest.create(2, 2),
                CtorTest.create(7, 4),
                CtorTest.createFail(0, 1),
                CtorTest.createFail(1, 0),
                CtorTest.createFail(-1, 1),
                CtorTest.createFail(1, -1),
                CtorTest.createFail(0, 0),
                CtorTest.createFail(-3, -9)
        );
    }

    record MatrixSpec(int rows, int columns, String encodedMatrixData) {

        MatrixSpec(int rows, int columns) {
            this(rows, columns, null);
        }

        BinaryMatrix createMatrix() {
            if (encodedMatrixData == null) {
                return new BinaryMatrix(rows, columns);
            }

            final var charArray = encodedMatrixData.toCharArray();

            if (charArray.length != rows * columns) {
                throw new IllegalArgumentException("Matrix data is not matching matrix dimensions");
            }

            final var matrixData = new BitSet(charArray.length);

            IntStream.range(0, charArray.length).forEach(index -> {
                final var chr = charArray[index];
                switch (chr) {
                    case '0' -> {
                        break;
                    }

                    case '1' -> {
                        matrixData.set(index);
                        break;
                    }

                    default -> {
                        throw new IllegalArgumentException(String.format("Unrecognized character: %c", chr));
                    }
                }
            });

            return new BinaryMatrix(rows, columns, matrixData);
        }
    }

    enum Selection {
        ROW,
        COLUMN
    }

    record SelectionTest(MatrixSpec matrixSpec, Selection type, int index, List<Boolean> expected) {

        static SelectionTest createRow(int rows, int columns, String encodedMatrixData, int row, int ... expected) {
            return new SelectionTest(new MatrixSpec(rows, columns, encodedMatrixData), Selection.ROW, row, conv(expected));
        }

        static SelectionTest createColumn(int rows, int columns, String encodedMatrixData, int column, int ... expected) {
            return new SelectionTest(new MatrixSpec(rows, columns, encodedMatrixData), Selection.COLUMN, column, conv(expected));
        }

        static SelectionTest createRow(int rows, int columns, int row) {
            return new SelectionTest(new MatrixSpec(rows, columns), Selection.ROW, row, null);
        }

        static SelectionTest createColumn(int rows, int columns, int column) {
            return new SelectionTest(new MatrixSpec(rows, columns), Selection.COLUMN, column, null);
        }

        void test() {
            final var matrix = matrixSpec.createMatrix();
            if (expected == null) {
                assertThrows(IndexOutOfBoundsException.class, () -> getIterator(matrix));
                assertThrows(IndexOutOfBoundsException.class, () -> getSpliterator(matrix));
            } else {
                final var it = getIterator(matrix);
                assertEquals(expected, readSelection(it::forEachRemaining));

                final var split = getSpliterator(matrix);
                assertEquals(expected, readSelection(split::forEachRemaining));

                assertThrows(NoSuchElementException.class, it::next);
            }
        }

        List<Boolean> readSelection(Consumer<Consumer<BinaryMatrix.Cursor>> forEach) {
            final List<Boolean> actualData = new ArrayList<>();
            final int[] variableIndexValue = new int[] { -1 };

            forEach.accept(cursor -> {
                final int fixedIndex;
                final int variableIndex;
                switch (type) {
                    case ROW -> {
                        fixedIndex = cursor.row();
                        variableIndex = cursor.column();
                    }
                    case COLUMN -> {
                        fixedIndex = cursor.column();
                        variableIndex = cursor.row();
                    }
                    default -> {
                        throw new IllegalArgumentException();
                    }
                }

                assertEquals(index, fixedIndex);
                assertEquals(variableIndexValue[0] + 1, variableIndex);
                variableIndexValue[0] = variableIndex;

                actualData.add(cursor.value());
            });

            return actualData;
        }

        Iterator<BinaryMatrix.Cursor> getIterator(BinaryMatrix matrix) {
            switch (type) {
                case ROW -> {
                    return matrix.getRowIterator(index);
                }
                case COLUMN -> {
                    return matrix.getColumnIterator(index);
                }
                default -> {
                    throw new IllegalArgumentException();
                }
            }
        }

        Spliterator<BinaryMatrix.Cursor> getSpliterator(BinaryMatrix matrix) {
            switch (type) {
                case ROW -> {
                    return matrix.getRowSpliterator(index);
                }
                case COLUMN -> {
                    return matrix.getColumnSpliterator(index);
                }
                default -> {
                    throw new IllegalArgumentException();
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource
    public void testSelection(SelectionTest testSpec) {
        testSpec.test();
    }

    private static Stream<SelectionTest> testSelection() {
        return Stream.of(
                SelectionTest.createRow(1, 1, "0", 0, 0),
                SelectionTest.createColumn(1, 1, "0", 0, 0),
                SelectionTest.createRow(1, 1, "1", 0, 1),
                SelectionTest.createColumn(1, 1, "1", 0, 1),

                SelectionTest.createRow(3, 2, "00" + "01" + "10", 0, 0, 0),
                SelectionTest.createRow(3, 2, "00" + "01" + "10", 1, 0, 1),
                SelectionTest.createRow(3, 2, "00" + "01" + "10", 2, 1, 0),
                SelectionTest.createColumn(3, 2, "00" + "01" + "10", 0, 0, 0, 1),
                SelectionTest.createColumn(3, 2, "00" + "01" + "10", 1, 0, 1, 0),

                SelectionTest.createRow(3, 2, -1),
                SelectionTest.createRow(3, 2, 3),
                SelectionTest.createRow(3, 2, 12),

                SelectionTest.createColumn(3, 2, -1),
                SelectionTest.createColumn(3, 2, 2),
                SelectionTest.createColumn(3, 2, 12)
        );
    }

    record SetValueTest(MatrixSpec matrixSpec, int row, int column, Boolean expected) {

        static SetValueTest create(int rows, int columns, String encodedMatrixData, int row, int column, boolean expected) {
            return new SetValueTest(new MatrixSpec(rows, columns, encodedMatrixData), row, column, expected);
        }

        static SetValueTest create(int rows, int columns, int row, int column) {
            return new SetValueTest(new MatrixSpec(rows, columns), row, column, null);
        }

        void test() {
            final var matrix = matrixSpec.createMatrix();
            final var matrixCopy = matrixSpec.createMatrix();
            if (expected == null) {
                assertThrows(IndexOutOfBoundsException.class, () -> matrix.set(row, column));
                assertEquals(matrixCopy, matrix);

                assertThrows(IndexOutOfBoundsException.class, () -> matrix.set(row, column, true));
                assertEquals(matrixCopy, matrix);

                assertThrows(IndexOutOfBoundsException.class, () -> matrix.set(row, column, false));
                assertEquals(matrixCopy, matrix);

                assertThrows(IndexOutOfBoundsException.class, () -> matrix.unset(row, column));
                assertEquals(matrixCopy, matrix);

                assertThrows(IndexOutOfBoundsException.class, () -> matrix.isSet(row, column));
                assertEquals(matrixCopy, matrix);
            } else {
                assertEquals(expected, matrix.isSet(row, column));

                matrix.set(row, column, expected);
                assertEquals(expected, matrix.isSet(row, column));
                assertEquals(matrixCopy, matrix);

                if (expected) {
                    matrix.set(row, column);
                } else {
                    matrix.unset(row, column);
                }
                assertEquals(expected, matrix.isSet(row, column));
                assertEquals(matrixCopy, matrix);

                matrix.set(row, column, !expected);
                assertNotEquals(expected, matrix.isSet(row, column));
                assertNotEquals(matrixCopy, matrix);

                if (expected) {
                    matrix.set(row, column);
                } else {
                    matrix.unset(row, column);
                }
                assertEquals(expected, matrix.isSet(row, column));
                assertEquals(matrixCopy, matrix);
            }
        }
    }

    @ParameterizedTest
    @MethodSource
    public void testSetValue(SetValueTest testSpec) {
        testSpec.test();
    }

    private static List<SetValueTest> testSetValue() {
        final List<SetValueTest> data = new ArrayList<>();

        data.addAll(List.of(
                SetValueTest.create(1, 1, "0", 0, 0, false),
                SetValueTest.create(1, 1, "1", 0, 0, true),

                SetValueTest.create(3, 2, -1, 0),
                SetValueTest.create(3, 2, 3, 0),
                SetValueTest.create(3, 2, 12, 0),

                SetValueTest.create(3, 2, 0, -1),
                SetValueTest.create(3, 2, 0, 2),
                SetValueTest.create(3, 2, 0, 12),

                SetValueTest.create(3, 2, 3, 2)
        ));

        final var matrixData = new boolean[3][5];
        matrixData[0] = new boolean[] { false, true, false, true, true };
        matrixData[1] = new boolean[] { true, false, true, false, true };
        matrixData[2] = new boolean[] { false, false, true, false, false };

        final var sb = new StringBuilder();
        for (int i = 0; i != 3; ++i) {
            for (int j = 0; j != 5; ++j) {
                sb.append(matrixData[i][j] ? '1' : '0');
            }
        }

        final var encodedMatrixData = sb.toString();
        for (int i = 0; i != 3; ++i) {
            for (int j = 0; j != 5; ++j) {
                data.add(SetValueTest.create(3, 5, encodedMatrixData, i, j, matrixData[i][j]));
            }
        }

        return data;
    }

    @Test
    public void testEquals() {
        final var matrixSpec = new MatrixSpec(2, 3, "001" + "101");

        final var a = matrixSpec.createMatrix();
        final var b = matrixSpec.createMatrix();

        assertTrue(a.equals(b));
        assertTrue(a.equals(a));
        assertFalse(a.equals(null));
        assertFalse(a.equals(matrixSpec));
    }

    @Test
    public void testHashCode() {
        final var matrixSpec2x3 = new MatrixSpec(2, 3);

        assertEquals(matrixSpec2x3.createMatrix().hashCode(), matrixSpec2x3.createMatrix().hashCode());

        final var matrixSpec3x2 = new MatrixSpec(3, 2);
        assertNotEquals(matrixSpec2x3.createMatrix().hashCode(), matrixSpec3x2.createMatrix().hashCode());
    }

    private static List<Boolean> conv(int... values) {
        return IntStream.of(values).mapToObj(v -> v != 0).toList();
    }
}
