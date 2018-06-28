/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
// Checkstyle: stop


package org.graalvm.compiler.jtt.hotpath;

import java.util.Random;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 */
public class HP_life extends JTTTest {

    public static int test(int generations) {
        reset();
        for (int i = 0; i < generations; ++i) {
            step();
        }
        int sum = 0;
        for (int row = 0; row < rows; ++row) {
            for (int col = 0; col < cols; ++col) {
                boolean value = cell(row, col);
                sum += (row * 15223242 + col * 21623234) ^ ((value ? 1 : 0) * 15323142);
            }
        }
        return sum;
    }

    private static final int rows = 20;
    private static final int cols = 20;
    private static boolean cells[] = new boolean[rows * cols];

    private static boolean cell(int row, int col) {
        return ((row >= 0) && (row < rows) && (col >= 0) && (col < cols) && cells[row * cols + col]);
    }

    private static boolean step() {
        boolean next[] = new boolean[rows * cols];
        boolean changed = false;
        for (int row = rows - 1; row >= 0; --row) {
            int row_offset = row * cols;
            for (int col = cols - 1; col >= 0; --col) {
                int count = 0;
                if (cell(row - 1, col - 1)) {
                    count++;
                }
                if (cell(row - 1, col)) {
                    count++;
                }
                if (cell(row - 1, col + 1)) {
                    count++;
                }
                if (cell(row, col - 1)) {
                    count++;
                }
                if (cell(row, col + 1)) {
                    count++;
                }
                if (cell(row + 1, col - 1)) {
                    count++;
                }
                if (cell(row + 1, col)) {
                    count++;
                }
                if (cell(row + 1, col + 1)) {
                    count++;
                }
                boolean old_state = cells[row_offset + col];
                boolean new_state = (!old_state && count == 3) || (old_state && (count == 2 || count == 3));
                if (!changed && new_state != old_state) {
                    changed = true;
                }
                next[row_offset + col] = new_state;
            }
        }
        cells = next;
        return changed;
    }

    private static void reset() {
        Random random = new Random(0);
        boolean cells2[] = HP_life.cells;
        for (int offset = 0; offset < cells2.length; ++offset) {
            cells2[offset] = random.nextDouble() > 0.5;
        }
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 5);
    }

}
