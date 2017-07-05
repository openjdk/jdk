/*
 * Copyright (c) 1995, Oracle and/or its affiliates. All rights reserved.
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

package sun.awt;

import java.awt.*;
import java.util.BitSet;


/**
 * A layout manager for a container that lays out grids.  Allows setting
 * the relative sizes of rows and columns.
 *
 * @author Herb Jellinek
 */


public class VariableGridLayout extends GridLayout {

    BitSet rowsSet = new BitSet();
    double rowFractions[] = null;

    BitSet colsSet = new BitSet();
    double colFractions[] = null;

    int rows;
    int cols;
    int hgap;
    int vgap;

    /**
     * Creates a grid layout with the specified rows and specified columns.
     * @param rows the rows
     * @param cols the columns
     */
    public VariableGridLayout(int rows, int cols) {
        this(rows, cols, 0, 0);

        if (rows != 0) {
            rowsSet = new BitSet(rows);
            stdRowFractions(rows);
        }

        if (cols != 0) {
            colsSet = new BitSet(cols);
            stdColFractions(cols);
        }
    }

    /**
     * Creates a grid layout with the specified rows, columns,
     * horizontal gap, and vertical gap.
     * @param rows the rows
     * @param cols the columns
     * @param hgap the horizontal gap variable
     * @param vgap the vertical gap variable
     * @exception IllegalArgumentException If the rows and columns are invalid.
     */
    public VariableGridLayout(int rows, int cols, int hgap, int vgap) {
        super(rows, cols, hgap, vgap);

        this.rows = rows;
        this.cols = cols;
        this.hgap = hgap;
        this.vgap = vgap;

        if (rows != 0) {
            rowsSet = new BitSet(rows);
            stdRowFractions(rows);
        }

        if (cols != 0) {
            colsSet = new BitSet(cols);
            stdColFractions(cols);
        }
    }

    void stdRowFractions(int nrows) {
        rowFractions = new double[nrows];
        for (int i = 0; i < nrows; i++) {
            rowFractions[i] = 1.0 / nrows;
        }
    }

    void stdColFractions(int ncols) {
        colFractions = new double[ncols];
        for (int i = 0; i < ncols; i++) {
            colFractions[i] = 1.0 / ncols;
        }
    }

    public void setRowFraction(int rowNum, double fraction) {
        rowsSet.set(rowNum);
        rowFractions[rowNum] = fraction;
    }

    public void setColFraction(int colNum, double fraction) {
        colsSet.set(colNum);
        colFractions[colNum] = fraction;
    }

    public double getRowFraction(int rowNum) {
        return rowFractions[rowNum];
    }

    public double getColFraction(int colNum) {
        return colFractions[colNum];
    }

    void allocateExtraSpace(double vec[], BitSet userSet) {
        // collect the space that's been explicitly allocated...
        double total = 0.0;
        int unallocated = 0;
        int i;
        for (i = 0; i < vec.length; i++) {
            if (userSet.get(i)) {
                total += vec[i];
            } else {
                unallocated++;
            }
        }

        // ... then spread the extra space
        if (unallocated != 0) {
            double space = (1.0 - total) / unallocated;
            for (i = 0; i < vec.length; i++) {
                if (!userSet.get(i)) {
                    vec[i] = space;
                    userSet.set(i);
                }
            }
        }
    }


    void allocateExtraSpace() {
        allocateExtraSpace(rowFractions, rowsSet);
        allocateExtraSpace(colFractions, colsSet);
    }

    /**
     * Lays out the container in the specified panel.
     * @param parent the specified component being laid out
     * @see Container
     */
    public void layoutContainer(Container parent) {
        Insets insets = parent.insets();
        int ncomponents = parent.countComponents();
        int nrows = rows;
        int ncols = cols;

        if (nrows > 0) {
            ncols = (ncomponents + nrows - 1) / nrows;
        } else {
            nrows = (ncomponents + ncols - 1) / ncols;
        }

        if (rows == 0) {
            stdRowFractions(nrows);
        }
        if (cols == 0) {
            stdColFractions(ncols);
        }

        Dimension size = parent.size();
        int w = size.width - (insets.left + insets.right);
        int h = size.height - (insets.top + insets.bottom);

        w = (w - (ncols - 1) * hgap);
        h = (h - (nrows - 1) * vgap);

        allocateExtraSpace();

        for (int c = 0, x = insets.left ; c < ncols ; c++) {
            int colWidth = (int)(getColFraction(c) * w);
            for (int r = 0, y = insets.top ; r < nrows ; r++) {
                int i = r * ncols + c;
                int rowHeight = (int)(getRowFraction(r) * h);

                if (i < ncomponents) {
                    parent.getComponent(i).reshape(x, y, colWidth, rowHeight);
                }
                y += rowHeight + vgap;
            }
            x += colWidth + hgap;
        }
    }

    static String fracsToString(double array[]) {
        String result = "["+array.length+"]";

        for (int i = 0; i < array.length; i++) {
            result += "<"+array[i]+">";
        }
        return result;
    }

    /**
     * Returns the String representation of this VariableGridLayout's values.
     */
    public String toString() {
        return getClass().getName() + "[hgap=" + hgap + ",vgap=" + vgap +
                                       ",rows=" + rows + ",cols=" + cols +
                                       ",rowFracs=" +
                                       fracsToString(rowFractions) +
                                       ",colFracs=" +
                                       fracsToString(colFractions) + "]";
    }
}
