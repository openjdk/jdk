/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4514071
 * @summary Tests that JTable, JList and JTree provide drag-over feedback.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual DragOverFeedbackTest
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.TransferHandler;

public class DragOverFeedbackTest {
    private static final String INSTRUCTIONS = """
        This test is designed to make sure that JTable, JTree, and JList
        provide visual feedback when a DnD drag operation occurs over them.

        Click on the label where it says "DRAG FROM HERE" and begin dragging.
        Drag over each of the three components (JTable, JTree, JList).
        While you're dragging over them, they should visually indicate the
        location where a drop would occur. This visual indication may use the
        selection but could be some other visual.

        If above is true press PASS else press FAIL.
        """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(50)
                .testUI(DragOverFeedbackTest::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static final TransferHandler handler = new TransferHandler() {
        public boolean canImport(JComponent comp, DataFlavor[] flavors) {
            return true;
        }
    };

    private static JFrame createTestUI() {
        JFrame frame = new JFrame("DragOverFeedbackTest");
        final JLabel label = new JLabel("DRAG FROM HERE");
        label.setPreferredSize(new Dimension(400, 25));
        label.setTransferHandler(new TransferHandler("text"));
        label.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent me) {
                label.getTransferHandler().exportAsDrag(label, me, TransferHandler.COPY);
            }
        });
        JTable table = new JTable(
                            new String[][] {{"one"}, {"two"}, {"three"}, {"four"}},
                            new String[] {"1"});
        table.setRowSelectionInterval(1, 1);
        table.setTransferHandler(handler);

        JList list = new JList(new String[] {"one", "two", "three", "four"});
        list.setSelectedIndex(1);
        list.setTransferHandler(handler);

        JTree tree = new JTree();
        tree.setSelectionRow(1);
        tree.setTransferHandler(handler);

        frame.add(label, BorderLayout.NORTH);

        JPanel wrapper = new JPanel();
        wrapper.setLayout(new GridLayout(3, 1));
        table.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        wrapper.add(table);
        list.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        wrapper.add(list);
        tree.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        wrapper.add(tree);
        frame.add(wrapper);
        frame.setSize(500, 500);
        return frame;
    }
}
