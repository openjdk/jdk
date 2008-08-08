/*
 * Copyright 2004-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.tools.jconsole.inspector;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.io.*;

public abstract class XTable extends JTable {
    static final int NAME_COLUMN = 0;
    static final int VALUE_COLUMN = 1;
    private Color defaultColor, editableColor, droppableColor, errorColor;
    private Font normalFont, boldFont;

    public XTable () {
        super();
        TableSorter sorter;
        setModel(sorter = new TableSorter());
        sorter.addMouseListenerToHeaderInTable(this);
        setRowSelectionAllowed(false);
        setColumnSelectionAllowed(false);
        setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    }

    Color getDefaultColor() {
        return defaultColor;
    }

    Color getEditableColor() {
        return editableColor;
    }

    /**
     * This returns the select index as the table was at initialization
     */
    public int getSelectedIndex() {
        return convertRowToIndex(getSelectedRow());
    }

    /*
     * Converts the row into index (before sorting)
     */
    public int convertRowToIndex(int row) {
        if (row == -1) return row;
        if (getModel() instanceof TableSorter) {
            return (((TableSorter) getModel()).getInvertedIndex()[row]);
        } else {
            return row;
        }
    }

    public void emptyTable() {
        DefaultTableModel model = (DefaultTableModel)getModel();
        while (model.getRowCount()>0)
            model.removeRow(0);
    }

    public abstract boolean isTableEditable();
    public abstract boolean isColumnEditable(int column);
    public abstract boolean isReadable(int row);
    public abstract boolean isWritable(int row);
    public abstract boolean isCellError(int row, int col);
    public abstract boolean isAttributeViewable(int row, int col);
    public abstract void setTableValue(Object value,int row);
    public abstract Object getValue(int row);
    public abstract String getClassName(int row);
    public abstract String getValueName(int row);

    public boolean isReadWrite(int row) {
        return (isReadable(row) && isWritable(row));
    }

    //JTable re-implementation

    //attribute can be editable even if unavailable
    public boolean isCellEditable(int row, int col) {
        return ((isTableEditable() && isColumnEditable(col)
                 &&  isWritable(row)
                 && Utils.isEditableType(getClassName(row))));
    }

    //attribute can be droppable even if unavailable
    public boolean isCellDroppable(int row, int col) {
        return (isTableEditable() && isColumnEditable(col)
                && isWritable(row));
    }

    //returns null, means no tool tip
    public String getToolTip(int row, int column) {
        return null;
    }

    /**
     * This method sets read write rows to be blue, and other rows to be their
     * default rendered colour.
     */
    public TableCellRenderer getCellRenderer(int row, int column) {
        DefaultTableCellRenderer tcr =
            (DefaultTableCellRenderer) super.getCellRenderer(row,column);
        tcr.setToolTipText(getToolTip(row,column));
        if (defaultColor == null) {
            defaultColor = tcr.getForeground();
            editableColor = Color.blue;
            droppableColor = Color.green;
            errorColor = Color.red;
            // this sometimes happens for some reason
            if (defaultColor == null) {
                return tcr;
            }
        }
        if (column != VALUE_COLUMN) {
            tcr.setForeground(defaultColor);
            return tcr;
        }
        if (isCellError(row,column)) {
            tcr.setForeground(errorColor);
        } else if (isCellEditable(row, column)) {
            tcr.setForeground(editableColor);
        } else {
            tcr.setForeground(defaultColor);
        }
        return tcr;
    }

    public Component prepareRenderer(TableCellRenderer renderer,
                                     int row, int column) {
        Component comp = super.prepareRenderer(renderer, row, column);

        if (normalFont == null) {
            normalFont = comp.getFont();
            boldFont = normalFont.deriveFont(Font.BOLD);
        }

        if (column == VALUE_COLUMN && isAttributeViewable(row, VALUE_COLUMN)) {
            comp.setFont(boldFont);
        } else {
            comp.setFont(normalFont);
        }

        return comp;
    }
}
