/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4321312
 * @summary Verifies no Exception thrown from BasicInternalFrameUI$BorderListener
 * @key headful
 * @run main bug4321312
 */

import java.awt.Dimension;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.Point;
import java.awt.Robot;

import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public class bug4321312 {

    private static JFrame frame;
    private static MyInternalFrame jif;
    private static volatile Point loc;
    private static volatile Dimension size;

    static boolean fails;
    static Exception exc;

    private static synchronized boolean isFails() {
        return fails;
    }

    private static synchronized void setFails(Exception e) {
        fails = true;
        exc = e;
    }

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    UIManager.setLookAndFeel(
                            "com.sun.java.swing.plaf.motif.MotifLookAndFeel");
                } catch (ClassNotFoundException | InstantiationException
                         | UnsupportedLookAndFeelException
                         | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }

                frame = new JFrame("bug4321312");
                JDesktopPane jdp = new JDesktopPane();
                frame.add(jdp);

                jif = new MyInternalFrame("Internal Frame", true);
                jdp.add(jif);
                jif.setSize(150, 150);
                jif.setVisible(true);

                frame.setSize(200, 200);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(() -> {
                loc = jif.getLocationOnScreen();
                size = jif.getSize();
            });
            robot.mouseMove(loc.x + size.width / 2, loc.y + size.height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            robot.delay(200);
            if (isFails()) {
                throw new RuntimeException(exc);
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    static class MyInternalFrame extends JInternalFrame {
        MyInternalFrame(String str, boolean b) {
            super(str, b);
        }

        protected void processMouseEvent(MouseEvent e) {
            try {
                super.processMouseEvent(e);
            } catch (Exception exc) {
                setFails(exc);
            }
        }
    }
}
