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
 * @key headful
 * @bug 4655513
 * @summary TransferHandler doesn't recognize ACTION_LINK
            as a valid drop action
 * @library /javax/swing/regtesthelpers
 * @build Util
 * @run main bug4655513
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Robot;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureRecognizer;
import java.awt.dnd.DragSource;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

public class bug4655513 {
    private static final String LINK_URL = "http://www.example.com";
    private static volatile JEditorPane editor;
    private static volatile JLabel dragSource;
    private static JFrame frame;

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            SwingUtilities.invokeAndWait(bug4655513::createAndShowGUI);
            robot.waitForIdle();
            robot.delay(1000);

            Point dragStartLoc = Util.getCenterPoint(dragSource);
            Point dragEndLoc = Util.getCenterPoint(editor);
            robot.mouseMove(dragStartLoc.x, dragStartLoc.y);
            robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
            for (int y = dragStartLoc.y; y < dragEndLoc.y; y += 3) {
                robot.mouseMove(dragStartLoc.x, y);
                robot.delay(50);
            }
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(500);

            SwingUtilities.invokeAndWait(() -> {
                if (!editor.getText().contains(LINK_URL)) {
                    throw new RuntimeException("Test Failed! Drag & Drop did not work.");
                }
            });
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }

    private static void createAndShowGUI() {
        frame = new JFrame("Bug4655513 - Data Transfer");
        dragSource = new JLabel("To Test DnD, drag this label.");
        dragSource.setForeground(Color.RED);
        dragSource.setPreferredSize(new Dimension(250, 50));
        frame.add(dragSource, BorderLayout.NORTH);

        editor = new JEditorPane("text/plain", "Drop here.");
        editor.setPreferredSize(new Dimension(250, 50));
        frame.add(new JScrollPane(editor), BorderLayout.CENTER);

        DragSource ds = new DragSource();
        DragGestureRecognizer rec =
            ds.createDefaultDragGestureRecognizer(dragSource,
                    DnDConstants.ACTION_LINK,
                    dge -> dge.startDrag(null, new StringSelection(LINK_URL)));
        frame.setSize(300, 150);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
