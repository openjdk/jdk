/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.query;

import static jdk.jfr.internal.query.Configuration.MAX_PREFERRED_WIDTH;
import static jdk.jfr.internal.query.Configuration.MIN_PREFERRED_WIDTH;
import static jdk.jfr.internal.query.Configuration.PREFERRED_WIDTH;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.internal.query.Configuration.Truncate;
import jdk.jfr.internal.util.Output;

/**
 * Class responsible for printing and formatting the contents of a table.
 */
final class TableRenderer {
    private final Configuration configuration;
    private final List<TableCell> tableCells;
    private final Table table;
    private final Query query;
    private final Output out;
    private int width;
    private int preferredWidth;

    public TableRenderer(Configuration configuration, Table table, Query query) {
        this.configuration = configuration;
        this.tableCells = createTableCells(table);
        this.table = table;
        this.query = query;
        this.out = configuration.output;
    }

    private List<TableCell> createTableCells(Table table) {
        return table.getFields().stream().filter(f -> f.visible).map(f -> createTableCell(f)).toList();
    }

    private TableCell createTableCell(Field field) {
        Truncate truncate = configuration.truncate;
        if (truncate == null) {
            truncate = field.truncate;
        }
        if (configuration.cellHeight != 0) {
            return new TableCell(field, configuration.cellHeight, truncate);
        } else {
            return new TableCell(field, field.cellHeight, truncate);
        }
    }

    public void render() {
        if (isEmpty()) {
            if (configuration.title != null) {
                out.println();
                out.println("No events found for '" + configuration.title +"'.");
            }
            return;
        }
        if (tooManyColumns()) {
            out.println();
            out.println("Too many columns to fit width.");
            return;
        }

        formatRow();
        sortRows();
        setColumnWidths();
        printTitle();
        printHeaderRow();
        printHeaderRowSeparators();
        printRows();
    }

    private boolean isEmpty() {
        return tableCells.isEmpty() || table.getRows().isEmpty();
    }

    private boolean tooManyColumns() {
        int minWidth = tableCells.size() * TableCell.MINIMAL_CELL_WIDTH;
        if (configuration.width != 0) {
            return minWidth > configuration.width;
        }
        return minWidth > MAX_PREFERRED_WIDTH;
    }

    private void formatRow() {
        double[] max = calculateNormalization();
        for (Row row : table.getRows()) {
            for (Field field : table.getFields()) {
                int index = field.index;
                Object object = row.getValue(index);
                if (field.normalized && object instanceof Number number) {
                    object = number.doubleValue() / max[index];
                }
                String text = FieldFormatter.format(field, object);
                row.putText(index, text);
                if (index < tableCells.size()) {
                    TableCell cell = tableCells.get(index);
                    int width = text.length() + TableCell.COLUMN_SEPARATOR.length();
                    if (width > cell.getPreferredWidth()) {
                        cell.setPreferredWidth(width);
                    }
                }
            }
        }
    }

    private double[] calculateNormalization() {
        double[] max = new double[tableCells.size()];
        int index = 0;
        for (TableCell cell : tableCells) {
            if (cell.field.normalized) {
                for (Row row : table.getRows()) {
                    if (row.getValue(index) instanceof Number number) {
                        max[index] += number.doubleValue();
                    }
                }
            }
            index++;
        }
        return max;
    }

    private void sortRows() {
        TableSorter sorter = new TableSorter(table, query);
        sorter.sort();
    }

    private void setColumnWidths() {
        setRowWidths();
        setPreferredHeaderWidths();
        if (configuration.width == 0) {
            preferredWidth= determineTableWidth();
        } else {
            preferredWidth = configuration.width;
        }
        // Set minimum table cell width
        distribute(cell -> cell.width < TableCell.MINIMAL_CELL_WIDTH);
        // Fill with preferred width
        distribute(cell -> cell.width < cell.getPreferredWidth());
        // Distribute additional width to table cells with a non-fixed size
        distribute(cell -> !cell.field.fixedWidth);
        // If all table cells are fixed size, distribute to any of them
        distribute(cell -> true);
    }

    private void setRowWidths() {
        int rowCount = 0;
        for (Row row : table.getRows()) {
            if (rowCount == query.limit) {
                return;
            }
            int columnIndex = 0;
            for (TableCell cell : tableCells) {
                String text = row.getText(columnIndex);
                int width = text.length() + TableCell.COLUMN_SEPARATOR.length();
                if (width > cell.getPreferredWidth()) {
                    cell.setPreferredWidth(width);
                }
                columnIndex++;
            }
            rowCount++;
        }
    }

    private void setPreferredHeaderWidths() {
        for (TableCell cell : tableCells) {
            int headerWidth = cell.field.label.length();
            if (configuration.verboseHeaders) {
                headerWidth = Math.max(fieldName(cell.field).length(), headerWidth);
            }
            headerWidth += TableCell.COLUMN_SEPARATOR.length();
            if (headerWidth > cell.getPreferredWidth()) {
                cell.setPreferredWidth(headerWidth);
            }
        }
    }

    private int determineTableWidth() {
        int preferred = 0;
        for (TableCell cell : tableCells) {
            preferred += cell.getPreferredWidth();
        }
        // Avoid a very large table.
        if (preferred > MAX_PREFERRED_WIDTH) {
            return MAX_PREFERRED_WIDTH;
        }
        // Avoid a very small width, but not preferred width if there a few columns
        if (preferred < MIN_PREFERRED_WIDTH && tableCells.size() < 3) {
            return MIN_PREFERRED_WIDTH;
        }
        // Expand to preferred width
        if (preferred < PREFERRED_WIDTH) {
            return PREFERRED_WIDTH;
        }
        return preferred;
    }

    private void distribute(Predicate<TableCell> condition) {
        long amountLeft = preferredWidth - width;
        long last = -1;
        while (amountLeft > 0 && amountLeft != last) {
            last = amountLeft;
            for (TableCell cell : tableCells) {
                if (condition.test(cell)) {
                    cell.width++;
                    width++;
                    amountLeft--;
                }
            }
        }
    }

    private void printTitle() {
        String title = configuration.title;
        if (title != null) {
            if (isExperimental()) {
                title += " (Experimental)";
            }
            int pos = width - title.length();
            pos = Math.max(0, pos);
            pos = pos / 2;
            out.println();
            out.println(" ".repeat(pos) + title);
            out.println();
        }
    }

    private boolean isExperimental() {
        return tableCells.stream().flatMap(c -> c.field.sourceFields.stream()).anyMatch(f -> f.type.isExperimental());
    }

    private void printHeaderRow() {
        printRow(cell -> cell.field.label);
        if (configuration.verboseHeaders) {
            printRow(cell -> fieldName(cell.field));
        }
    }

    private void printHeaderRowSeparators() {
        printRow(cell -> "-".repeat(cell.getContentWidth()));
    }

    private void printRow(java.util.function.Function<TableCell, String> action) {
        for (TableCell cell : tableCells) {
            cell.setContent(action.apply(cell));
        }
        printRow();
    }

    private void printRows() {
        int rowCount = 0;
        for (Row row : table.getRows()) {
            if (rowCount == query.limit) {
                return;
            }
            int columnIndex = 0;
            for (TableCell cell : tableCells) {
                setCellContent(cell, row, columnIndex++);
            }
            printRow();
            rowCount++;
        }
    }

    private void setCellContent(TableCell cell, Row row, int columnIndex) {
        String text = row.getText(columnIndex);
        if (cell.cellHeight > 1) {
            Object o = row.getValue(columnIndex);
            if (o instanceof RecordedStackTrace s) {
                o = s.getFrames();
            }
            if (o instanceof Collection<?> c) {
                setMultiline(cell, c);
                return;
            }
        }

        if (text.length() > cell.getContentSize()) {
            Object o = row.getValue(columnIndex);
            cell.setContent(FieldFormatter.formatCompact(cell.field, o));
            return;
        }
        cell.setContent(text);
    }

    private void setMultiline(TableCell cell, Collection<?> objects) {
        int row = 0;
        cell.clear();
        for(Object object : objects) {
            if (row == cell.cellHeight) {
                return;
            }
            String text = FieldFormatter.format(cell.field, object);
            if (text.length() > cell.getContentWidth()) {
                text = FieldFormatter.formatCompact(cell.field, object);
            }
            cell.addLine(text);
            row++;
        }
        if (cell.field.lexicalSort) {
            cell.sort();
        }
    }

    private void printRow() {
        long maxHeight = 0;
        for (TableCell cell : tableCells) {
            maxHeight = Math.max(cell.getHeight(), maxHeight);
        }
        TableCell lastCell = tableCells.getLast();
        for (int rowIndex = 0; rowIndex < maxHeight; rowIndex++) {
            for (TableCell cell : tableCells) {
                if (rowIndex < cell.getHeight()) {
                    out.print(cell.getText(rowIndex));
                } else {
                    out.print(" ".repeat(cell.getContentWidth()));
                }
                if (cell != lastCell) {
                    out.print(TableCell.COLUMN_SEPARATOR);
                }
            }
            out.println();
        }
    }

    private String fieldName(Field field) {
        return "(" + field.name + ")";
    }

    public long getWidth() {
        return width;
    }
}
