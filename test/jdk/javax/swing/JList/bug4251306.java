/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4251306
 * @summary Test that Shift-Space keybinding works properly in JList.
 * @key headful
 * @run main bug4251306
 */

import java.awt.Robot;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

public class bug4251306 {
    private static JFrame f;
    private static JList lst;
    private static CountDownLatch listGainedFocusLatch;
    private static volatile boolean failed;
    public static void main(String[] args) throws Exception {
        try {
            listGainedFocusLatch = new CountDownLatch(1);
            createUI();
            runTest();
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (f != null) {
                    f.dispose();
                }
            });
        }
    }

    private static void createUI() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            f = new JFrame("bug4251306");
            lst = new JList<>(new String[]{"anaheim", "bill",
                "chicago", "dingo"});
            lst.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    listGainedFocusLatch.countDown();
                }
            });
            JScrollPane sp = new JScrollPane(lst);
            f.add(sp);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setAlwaysOnTop(true);
            f.setVisible(true);
        });
    }

    private static void runTest() throws Exception {
        if (!listGainedFocusLatch.await(3, TimeUnit.SECONDS)) {
            throw new RuntimeException("Waited too long, but can't gain focus for list");
        }
        Robot robot = new Robot();
        robot.setAutoDelay(500);
        robot.waitForIdle();
        robot.keyPress(KeyEvent.VK_A);
        robot.keyRelease(KeyEvent.VK_A);
        robot.waitForIdle();
        robot.keyPress(KeyEvent.VK_SHIFT);
        robot.keyPress(KeyEvent.VK_SPACE);
        robot.keyPress(KeyEvent.VK_DOWN);
        robot.keyRelease(KeyEvent.VK_DOWN);
        robot.keyPress(KeyEvent.VK_DOWN);
        robot.keyRelease(KeyEvent.VK_DOWN);
        robot.keyPress(KeyEvent.VK_DOWN);
        robot.keyRelease(KeyEvent.VK_DOWN);
        robot.keyRelease(KeyEvent.VK_SPACE);
        robot.keyRelease(KeyEvent.VK_SHIFT);

        SwingUtilities.invokeAndWait(() -> {
            if (!lst.isSelectedIndex(0) ||
                !lst.isSelectedIndex(1) ||
                !lst.isSelectedIndex(2) ||
                !lst.isSelectedIndex(3)) {
                failed = true;
            }
        });
        if (failed) {
            throw new RuntimeException("Required list items are not selected");
        }
    }
}
