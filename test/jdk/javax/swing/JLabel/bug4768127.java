/*
 * Copyright (c) 2002, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4768127
 * @summary ToolTipManager not removed from components
 * @key headful
 */

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.MouseMotionListener;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;

public class bug4768127 {
    static JFrame fr;
    static volatile Point p;
    static volatile JLabel[] label = new JLabel[2];

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                fr = new JFrame("bug4768127");

                JDesktopPane jdp = new JDesktopPane();
                JInternalFrame jif1 = new JInternalFrame("jif 1");
                JInternalFrame jif2 = new JInternalFrame("jif 2");
                label[0] = new JLabel("Label 1");
                label[1] = new JLabel("Label 2");

                label[0].setToolTipText("tooltip 1");
                jif1.getContentPane().add(label[0]);
                jif1.setBounds(0, 0, 130, 160);
                jif1.setVisible(true);
                jdp.add(jif1);

                label[1].setToolTipText("tooltip 2");
                jif2.getContentPane().add(label[1]);
                jif2.setBounds(210, 0, 130, 220);
                jif2.setVisible(true);
                jdp.add(jif2);

                fr.getContentPane().add(jdp);
                fr.setLocationRelativeTo(null);

                fr.setSize(400, 300);
                fr.setVisible(true);
            });

            Robot robot = new Robot();
            robot.setAutoDelay(10);
            robot.waitForIdle();
            robot.delay(3000);

            clickLabel(0, robot);
            robot.waitForIdle();
            robot.delay(3000);

            clickLabel(1, robot);
            robot.waitForIdle();
            robot.delay(3000);

            clickLabel(0, robot);
            robot.waitForIdle();
            robot.delay(3000);

            clickLabel(1, robot);
            robot.waitForIdle();
            robot.delay(3000);

            MouseMotionListener[] mml = label[0].getMouseMotionListeners();
            if (mml.length > 0 && mml[0] instanceof ToolTipManager) {
                throw new RuntimeException("Extra MouseMotionListeners were added to the label \"Label 1\" by ToolTipManager");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (fr != null) {
                    fr.dispose();
                }
            });
        }
    }

    static void clickLabel(int i, Robot robot) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            p = label[i].getLocationOnScreen();
        });
        final Rectangle rect = label[i].getBounds();
        robot.mouseMove(p.x + rect.width / 2, p.y + rect.height / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        //Generate mouseMotionEvent
        robot.mouseMove(p.x + rect.width / 2 + 3, p.y + rect.height / 2 + 3);
        robot.mouseMove(p.x + rect.width / 2, p.y + rect.height / 2);
    }
}
