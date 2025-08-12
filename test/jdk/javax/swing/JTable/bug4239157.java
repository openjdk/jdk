/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.DefaultCellEditor;
import javax.swing.JFrame;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;

/*
 * @test
 * @bug 4239157
 * @summary Tests that JTable performs cell validation properly
 *          (i.e. does not accept entries for which stopCellEditing()
 *           returns false)
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4239157
 */

public class bug4239157 {
    private static final String INSTRUCTIONS = """
            You see a JTable having one row and two columns.
            Click in the very first cell (where "click here" is displayed).
            Edit its content (e.g. type some letters) and press right arrow key.
            The edited cell should stay active, its content shouldn't change.
            The right cell (that with text "inactive forever") shouldn't become active.
            The same should be true when you press Tab key.
            If it is so, test passes, otherwise it fails.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(50)
                .testUI(bug4239157::createTestUI)
                .build()
                .awaitAndCheck();
    }

    public static JFrame createTestUI() {
        JFrame frame = new JFrame("bug4239157");
        JTable table = new JTable(new Object[][]{{"click here",
                "inactive forever"}},
                new Object[]{"1", "2"});
        frame.add("Center", table);
        TableColumn column = table.getColumn("1");
        TableCellEditor editor = new TestEditor(new JTextField());
        column.setCellEditor(editor);

        frame.pack();
        return frame;
    }

    static class TestEditor extends DefaultCellEditor {
        public TestEditor(JTextField tf) {
            super(tf);
        }

        public boolean stopCellEditing() {
            return false;
        }
    }
}
