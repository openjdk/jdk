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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
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

    private static List<Boolean> conv(int... values) {
        return IntStream.of(values).mapToObj(v -> v != 0).toList();
    }
}
