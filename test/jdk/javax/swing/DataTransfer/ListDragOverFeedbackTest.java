/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4546134
 * @summary Tests that JList shows the right drop location when it has multiple columns.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ListDragOverFeedbackTest
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
import javax.swing.TransferHandler;

public class ListDragOverFeedbackTest {
    private static final String INSTRUCTIONS = """
        JList should provide visual feedback when a DnD drag operation is
        occurring over it. This test is to check that it provides the
        feedback about the drop location correctly.

        Click on the label where it says "DRAG FROM HERE" and begin dragging.
        Drag over each column in each of the three JLists and make sure that
        the drop location indicated is appropriate for the mouse location. For
        instance, if the mouse is in the first column, the drop location should
        also be indicated in that first column.

        If above is true press PASS else press FAIL.
        """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(50)
                .testUI(ListDragOverFeedbackTest::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static final TransferHandler handler = new TransferHandler() {
        public boolean canImport(JComponent comp, DataFlavor[] flavors) {
            return true;
        }
    };

    private static JFrame createTestUI() {
        String[] vals = new String[] {
                "one", "two", "three", "four", "five", "six", "seven", "eight",
                "nine", "ten", "eleven", "twelve", "thirteen", "fourteen"};

        JFrame frame = new JFrame("ListDragOverFeedbackTest");
        final JLabel label = new JLabel("DRAG FROM HERE");
        label.setPreferredSize(new Dimension(400, 25));
        label.setTransferHandler(new TransferHandler("text"));
        label.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent me) {
                label.getTransferHandler().exportAsDrag(label, me,
                                              TransferHandler.COPY);
            }
        });

        JList list1 = new JList(vals);
        list1.setTransferHandler(handler);
        list1.setBorder(BorderFactory.createLineBorder(Color.BLACK));

        JList list2 = new JList(vals);
        list2.setLayoutOrientation(JList.VERTICAL_WRAP);
        list2.setTransferHandler(handler);
        list2.setBorder(BorderFactory.createLineBorder(Color.BLACK));

        JList list3 = new JList(vals);
        list3.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list3.setTransferHandler(handler);
        list3.setBorder(BorderFactory.createLineBorder(Color.BLACK));

        JPanel wrapper = new JPanel();
        wrapper.setLayout(new GridLayout(3, 1));
        wrapper.add(list1);
        wrapper.add(list2);
        wrapper.add(list3);

        frame.add(label, BorderLayout.NORTH);
        frame.add(wrapper);
        frame.setSize(400, 500);
        return frame;
    }
}
