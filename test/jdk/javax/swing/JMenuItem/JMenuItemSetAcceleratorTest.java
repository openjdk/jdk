/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/*
 * @test
 * @key headful
 * @bug 8296275
 * @summary To verify the setAccelerator method of JMenuItem.
 * @requires (os.family=="mac")
 * @run main JMenuItemSetAcceleratorTest
 */

public class JMenuItemSetAcceleratorTest {
    private static JFrame frame;
    private static final CountDownLatch actionLatch = new CountDownLatch(1);
    private volatile static Point frameAt;
    private volatile static Dimension frameSize;

    private static void createAndShow() {
        frame = new JFrame("JMenuItem.setAccelerator");
        frame.setLayout(new FlowLayout());

        JMenuBar bar = new JMenuBar();
        JMenu menu = new JMenu("File");
        JMenuItem menuItem = new JMenuItem("Menu Item");

        menuItem.setAccelerator(
            KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.META_DOWN_MASK));
        menuItem.addActionListener(e -> {
            System.out.println("menu item action.");
            actionLatch.countDown();
        });

        menu.add(menuItem);
        bar.add(menu);

        frame.setJMenuBar(bar);
        frame.setSize(200, 200);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(JMenuItemSetAcceleratorTest::createAndShow);

            Robot robot = new Robot();
            robot.setAutoDelay(50);
            robot.setAutoWaitForIdle(true);

            EventQueue.invokeAndWait(() -> {
                frameAt = frame.getLocationOnScreen();
                frameSize = frame.getSize();
            });

            robot.mouseMove(frameAt.x + frameSize.width / 2,
                frameAt.y + frameSize.height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();

            robot.keyPress(KeyEvent.VK_META);
            robot.keyPress(KeyEvent.VK_M);
            robot.keyRelease(KeyEvent.VK_M);
            robot.keyRelease(KeyEvent.VK_META);

            if (!actionLatch.await(5, TimeUnit.SECONDS)) {
                throw new RuntimeException(
                    "Hasn't received the JMenuItem action event by pressing "
                        + "accelerator keys, test fails.");
            }
            System.out.println("Test passed, received action event on menu item.");
        } finally {
            SwingUtilities.invokeAndWait(JMenuItemSetAcceleratorTest::disposeFrame);
        }
    }

    public static void disposeFrame() {
        if (frame != null) {
            frame.dispose();
        }
    }
}
