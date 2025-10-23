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
 * @bug 4327146
 * @summary Tests menu width after removeAll()
 * @key headful
 * @run main bug4327146
 */

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

public class bug4327146 {
    private static JButton b;
    private static JMenu m;
    private static JFrame frame;
    private static volatile Point loc;
    private static volatile Dimension dim;
    private static volatile Rectangle old_popupBounds;
    private static volatile Rectangle new_popupBounds;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame("bug4327146");
                m = new JMenu("Menu");
                m.add(new JMenuItem("I'm an ugly bug, fix me right now please!"));

                JMenuBar mbar = new JMenuBar();
                mbar.add(m);
                frame.setJMenuBar(mbar);

                b = new JButton("Cut");
                b.addActionListener(e -> {
                    m.removeAll();
                    m.add(new JMenuItem("Fixed :)"));
                });
                mbar.add(b);
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(() -> {
                loc = b.getLocationOnScreen();
                dim = b.getSize();
                m.doClick();
            });
            robot.waitForIdle();
            robot.delay(500);
            SwingUtilities.invokeAndWait(() -> {
                old_popupBounds = m.getPopupMenu().getBounds();
            });
            robot.mouseMove(loc.x + dim.width / 2, loc.y + dim.height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            robot.delay(500);
            SwingUtilities.invokeAndWait(() -> {
                m.doClick();
            });
            robot.waitForIdle();
            robot.delay(500);
            SwingUtilities.invokeAndWait(() -> {
                new_popupBounds = m.getPopupMenu().getBounds();
            });
            if (new_popupBounds.getWidth() >= old_popupBounds.getWidth()) {
                System.out.println("before cut popup Bounds " + old_popupBounds);
                System.out.println("after cut popupBounds " + new_popupBounds);
                throw new RuntimeException("JMenu popup width is wrong");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

}
