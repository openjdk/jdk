/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4759934
 * @summary windows activation problem
 * @library /javax/swing/regtesthelpers
 * @build Util
 * @run main bug4759934
 */

import java.awt.Dialog;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class bug4759934 {
    private static JFrame fr;
    private static Dialog dlg;
    private static JFileChooser jfc;

    private static JButton frameBtn;
    private static JButton dialogBtn;

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            robot.setAutoWaitForIdle(true);
            robot.setAutoDelay(50);

            SwingUtilities.invokeAndWait(bug4759934::createTestUI);
            robot.waitForIdle();
            robot.delay(1000);

            Point frameBtnLoc = Util.getCenterPoint(frameBtn);
            robot.mouseMove(frameBtnLoc.x, frameBtnLoc.y);
            robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
            robot.delay(500);

            Point dlgBtnLoc = Util.getCenterPoint(dialogBtn);
            robot.mouseMove(dlgBtnLoc.x , dlgBtnLoc.y);
            robot.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
            robot.delay(500);

            robot.keyPress(KeyEvent.VK_ESCAPE);
            robot.keyRelease(KeyEvent.VK_ESCAPE);
            robot.delay(500);

            SwingUtilities.invokeAndWait(() -> {
                if (frameBtn.hasFocus() && !dialogBtn.hasFocus()) {
                    throw new RuntimeException("Test failed! Focus was passed back" +
                            " to Frame instead of Dialog");
                }
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (dlg != null) {
                    dlg.dispose();
                }
                if (fr != null) {
                    fr.dispose();
                }
            });
        }
    }

    private static void createTestUI() {
        fr = new JFrame("bug4759934 - JFrame");

        frameBtn = new JButton("Show Dialog");
        frameBtn.addActionListener(e -> createDialog());
        fr.add(frameBtn);

        fr.setSize(300, 200);
        fr.setLocationRelativeTo(null);
        fr.setVisible(true);
    }

    private static void createDialog() {
        dlg = new JDialog(fr, "bug4759934 - JDialog");

        dialogBtn = new JButton("Show FileChooser");
        dlg.add(dialogBtn);

        dialogBtn.addActionListener(e -> {
            jfc = new JFileChooser();
            jfc.showOpenDialog(dlg);
        });

        dlg.setSize(300, 200);
        dlg.setLocation(fr.getX() + fr.getWidth() + 10, fr.getY());
        dlg.setVisible(true);
    }
}
