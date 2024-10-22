/*
 * Copyright (c) 2008, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6691503
 * @summary Checks that there is no opportunity for a malicious application
 *          to show a popup menu which has whole screen size when
 *          heavyweight popup menu is shown from an app.
 * @run main bug6691503
 */

import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import java.awt.Robot;
import java.awt.Window;

public class bug6691503 {
    private static JFrame frame;
    private static JPopupMenu popupMenu;
    private static volatile boolean isAlwaysOnTop1 = false;

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            SwingUtilities.invokeAndWait(bug6691503::setupUI);
            robot.waitForIdle();
            robot.delay(1000);

            SwingUtilities.invokeAndWait(bug6691503::testApplication);
            robot.delay(200);

            if (!isAlwaysOnTop1) {
                throw new RuntimeException("Malicious Application can show always-on-top" +
                        "popup menu which has whole screen size");
            }
            System.out.println("Test passed");
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }

    private static void setupUI() {
        frame = new JFrame("bug6691503");
        popupMenu = new JPopupMenu();
        JMenuItem click = new JMenuItem("Click");
        popupMenu.add(click);
        frame.setSize(200, 200);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void testApplication() {
        popupMenu.show(frame, 0, 0);
        Window popupWindow = (Window)
                (popupMenu.getParent().getParent().getParent().getParent());
        isAlwaysOnTop1 = popupWindow.isAlwaysOnTop();
        System.out.println("Application: popupWindow.isAlwaysOnTop() = "
                + isAlwaysOnTop1);
        popupMenu.setVisible(false);
    }
}
