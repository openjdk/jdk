/*
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4865918
 * @requires (os.family != "mac")
 * @summary REGRESSION:JCK1.4a-runtime api/javax_swing/interactive/JScrollBarTests.html#JScrollBar
 * @run main bug4865918
 */

import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JScrollBar;
import javax.swing.SwingUtilities;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import java.util.Date;

public class bug4865918 {

    private static TestScrollBar sbar;
    private static final CountDownLatch mousePressLatch = new CountDownLatch(1);

    public static void main(String[] argv) throws Exception {
        String osName = System.getProperty("os.name");
        if (osName.toLowerCase().contains("os x")) {
            System.out.println("This test is not for MacOS, considered passed.");
            return;
        }
        SwingUtilities.invokeAndWait(() -> setupTest());

        SwingUtilities.invokeAndWait(() -> sbar.pressMouse());
        if (!mousePressLatch.await(2, TimeUnit.SECONDS)) {
            throw new RuntimeException("Timed out waiting for mouse press");
        }

        if (getValue() != 9) {
            throw new RuntimeException("The scrollbar block increment is incorrect");
        }
    }

    private static int getValue() throws Exception {
        final int[] result = new int[1];

        SwingUtilities.invokeAndWait(() -> {
            result[0] = sbar.getValue();
        });

        System.out.println("value " + result[0]);
        return result[0];
    }

    private static void setupTest() {

        sbar = new TestScrollBar(JScrollBar.HORIZONTAL, -1, 10, -100, 100);
        sbar.setPreferredSize(new Dimension(200, 20));
        sbar.setBlockIncrement(10);
        sbar.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                mousePressLatch.countDown();
            }
        });

    }

    static class TestScrollBar extends JScrollBar {

        public TestScrollBar(int orientation, int value, int extent,
                int min, int max) {
            super(orientation, value, extent, min, max);

        }

        public void pressMouse() {
            MouseEvent me = new MouseEvent(sbar,
                    MouseEvent.MOUSE_PRESSED,
                    (new Date()).getTime(),
                    MouseEvent.BUTTON1_DOWN_MASK,
                    3 * getWidth() / 4, getHeight() / 2,
                    1, true);
            processMouseEvent(me);
        }
    }
}
