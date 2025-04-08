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
 * @bug 4273320
 * @summary JTabbedPane.setTitleAt() should refresh when using HTML text
 * @key headful
*/

import java.awt.BorderLayout;
import java.awt.Rectangle;
import java.awt.Robot;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.plaf.TabbedPaneUI;

public class bug4273320 {

    static JFrame frame;
    static volatile JTabbedPane tabs;

    static final String PLAIN = "Plain";
    static final String HTML = "<html>A fairly long HTML text label</html>";

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(bug4273320::createUI);

            Robot robot = new Robot();
            robot.waitForIdle();
            robot.delay(1000);

            TabbedPaneUI ui = tabs.getUI();
            Rectangle origSize = ui.getTabBounds(tabs, 0);

            SwingUtilities.invokeAndWait(() -> {
                tabs.setTitleAt(0, HTML);
            });
            robot.waitForIdle();
            robot.delay(1000);

            Rectangle newSize = ui.getTabBounds(tabs, 0);
            // The tab should be resized larger if the longer HTML text is added
            System.out.println("orig = " + origSize.width + " x " + origSize.height);
            System.out.println("new = " + newSize.width + " x " + newSize.height);
            if (origSize.width >= newSize.width) {
                throw new RuntimeException("Tab text is not updated.");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    static void createUI() {
        frame = new JFrame("bug4273320");
        tabs = new JTabbedPane();
        JPanel panel = new JPanel();
        tabs.addTab(PLAIN, panel);
        frame.getContentPane().add(tabs, BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
    }
}
