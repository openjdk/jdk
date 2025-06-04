/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Label;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.util.List;

import javax.swing.JOptionPane;

/*
 * @test
 * @bug 6179157
 * @key multimon
 * @summary Tests dnd to another screen
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual DragToAnotherScreenTest
 */

public class DragToAnotherScreenTest {
    private static Label label0;
    private static Label label1;
    private static final int HGAP = 20;

    private static final String INSTRUCTIONS = """
                The following test is applicable for Single as well
                as Multi-monitor screens.

                It is a semi-automated test, the test will prompt
                the user whether the drag and drop action was successful or not
                and automatically PASS/FAIL the test.

                If on multi-monitor screens then please position
                the drag and drop windows on different screens.

                If you can not move the mouse from the frame "Drag Source"
                to the frame "Drop Target" press PASS,
                else proceed to the next step.

                Drag the label "Drag me" and drop it on the
                label "Drop on me".

                If you can not drag to the second label (for example
                if you can not drag across screens) press FAIL.

                After the drag and drop action, the test displays
                Success/Failure msg in JOptionPane.
                Click on OK button and the test is configured to
                automatically PASS/FAIL.
                """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                      .title("Test Instructions")
                      .instructions(INSTRUCTIONS)
                      .columns(35)
                      .testUI(DragToAnotherScreenTest::createAndShowUI)
                      .positionTestUI(DragToAnotherScreenTest::positionMultiTestUI)
                      .logArea(10)
                      .build()
                      .awaitAndCheck();
    }

    private static List<Frame> createAndShowUI() {
        PassFailJFrame.log("----- System Configuration ----");
        PassFailJFrame.log("Toolkit:" + Toolkit.getDefaultToolkit()
                                               .getClass()
                                               .getName());

        GraphicsDevice[] gd = GraphicsEnvironment.getLocalGraphicsEnvironment()
                                                 .getScreenDevices();
        if (gd.length == 1) {
            PassFailJFrame.log("Single Monitor");
        } else {
            PassFailJFrame.log("Multi-Monitor");
        }
        PassFailJFrame.log("--------------");
        PassFailJFrame.log("Test logs:\n");
        Frame frame0 = new Frame("Drag Source", gd[0].getDefaultConfiguration());
        frame0.setSize(300, 300);
        label0 = new Label("Drag me");
        frame0.add(label0);

        Frame frame1 = new Frame("Drop Target", gd[(gd.length > 1 ? 1 : 0)].getDefaultConfiguration());
        frame1.setSize(300, 300);
        label1 = new Label("Drop on me");
        frame1.add(label1);

        DragGestureListener dragGestureListener = dge -> dge.startDrag(null, new StringSelection(label0.getText()), null);
        new DragSource().createDefaultDragGestureRecognizer(label0,
                                                            DnDConstants.ACTION_COPY, dragGestureListener);

        DropTargetAdapter dropTargetAdapter = new DropTargetAdapter() {
            public void drop(DropTargetDropEvent dtde) {
                Transferable t = dtde.getTransferable();
                if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    try {
                        String str = (String) t.getTransferData(DataFlavor.stringFlavor);
                        label1.setText(str);
                        JOptionPane.showMessageDialog(frame0,
                                                      "getTransferData was successful",
                                                      "Test Passed", JOptionPane.PLAIN_MESSAGE);
                    } catch (Exception e) {
                        dtde.dropComplete(false);
                        e.printStackTrace();
                        PassFailJFrame.log("getTransferData() Failed");
                        JOptionPane.showMessageDialog(frame0,
                                                      "getTransferData() Failed",
                                                      "Test Failed", JOptionPane.ERROR_MESSAGE);
                        PassFailJFrame.forceFail("getTransferData() Failed");
                    }
                    dtde.dropComplete(true);
                } else {
                    dtde.rejectDrop();
                    PassFailJFrame.log("stringFlavor is not supported by Transferable");
                    JOptionPane.showMessageDialog(frame0,
                                                  "stringFlavor is not supported by Transferable",
                                                  "Test Failed", JOptionPane.ERROR_MESSAGE);
                    PassFailJFrame.forceFail("stringFlavor is not supported by Transferable");
                }
            }
        };
        new DropTarget(label1, dropTargetAdapter);
        return List.of(frame0, frame1);
    }

    private static void positionMultiTestUI(List<? extends Window> windows,
                                            PassFailJFrame.InstructionUI instructionUI) {
        int x = instructionUI.getLocation().x + instructionUI.getSize().width + HGAP;
        for (Window w : windows) {
            w.setLocation(x, instructionUI.getLocation().y);
            x += w.getWidth() + HGAP;
        }
    }
}
