/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8268145
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @requires (os.family == "mac")
 * @summary Verify rendering artifact is not seen moving caret inside
 *          JTable with TableCellEditor having JTextField
 * @run main/manual TestCaretArtifact
 */

import javax.swing.DefaultCellEditor;
import javax.swing.JFrame;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;
import javax.swing.SwingUtilities;

public class TestCaretArtifact {

    private static final String INSTRUCTIONS = """
        Double click on "Click Here" textfield so that textfield becomes editable;
        Press spacebar. Press left arrow button.
        Do this few times.
        If artifact is seen, press Fail else press Pass.""";

    public static void  main(String[] args) throws Exception {
         PassFailJFrame.builder()
                .title("Caret Artifact Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(TestCaretArtifact::createUI)
                .build()
                .awaitAndCheck();
    }


    public static JFrame createUI()  {
        TableCellEditor editor = new TestEditor(new JTextField());
        JTable table = new JTable(new Object[][] {{"click here",
                "inactive forever"}},
                new Object[] {"1", "2"});

        JFrame frame = new JFrame("CaretArtifact");
        TableColumn column = table.getColumn("1");
        column.setCellEditor(editor);
        frame.getContentPane().add("Center", table);
        frame.setSize(400, 100);

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

