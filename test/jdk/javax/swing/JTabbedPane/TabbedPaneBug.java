/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Robot;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

/*
 * @test
 * @bug 8259687
 * @key headful
 * @summary Tests whether the selected tab's component is on the top and visible.
 * @run main TabbedPaneBug
 */
public class TabbedPaneBug {

    private static final int FIRST = 0;
    private static final int SECOND = 1;
    private static JFrame window;
    private static JTabbedPane tabs;
    private static final JPanel firstTab = new JPanel();
    private static final JPanel secondTab = new JPanel();

    public static void main(String[] args) throws AWTException,
            InterruptedException, InvocationTargetException {
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(200);
            SwingUtilities.invokeAndWait(() -> createAndShowGUI());

            robot.waitForIdle();
            robot.mouseMove(window.getWidth()/2, window.getHeight()/2);
            robot.delay(500);
            Color actualColor = robot.getPixelColor(window.getWidth()/2,
                    window.getHeight()/2);
            robot.delay(200);

            if (actualColor.equals(Color.RED)) {
                throw new RuntimeException("The second (selected) component" +
                        " is not on the top!!");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (window != null) {
                    window.dispose();
                }
            });
        }

    }

    private static void createAndShowGUI() {
        window = new JFrame("Tabbed Pane Bug");
        tabs = new JTabbedPane();

        firstTab.setBackground(Color.RED);
        secondTab.setBackground(Color.GREEN);

        tabs.addChangeListener((e) -> {
            if (tabs.getSelectedComponent() == null) {
                switch (tabs.getSelectedIndex()) {
                    case FIRST:
                        tabs.setComponentAt(tabs.getSelectedIndex(), firstTab);
                        break;
                    case SECOND:
                        tabs.setComponentAt(tabs.getSelectedIndex(), secondTab);
                        break;
                    default:
                }
            }
        });

        tabs.addTab("First", null);
        tabs.addTab("Second", null);

        tabs.setSelectedIndex(SECOND);
        window.add(tabs);
        window.setSize(200, 200);
        window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        window.setVisible(true);
    }
}
