/*
 * Copyright (c) 2004, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5078214
 * @key headful
 * @summary ToolTip is shown partially when the application is near the bottom of screen.
 * @library /test/lib
 * @build jtreg.SkippedException
 * @run main bug5078214
 */

import java.awt.BorderLayout;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.MouseEvent;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;

import jtreg.SkippedException;

public class bug5078214 {
    static volatile boolean passed = false;

    static volatile JFrame mainFrame;
    static volatile Rectangle bounds;
    static volatile Insets insets;
    static Robot robot;

    public static void main(String[] args) throws Exception {
        try {
            if (getGraphicsConfig() == null) {
                throw new SkippedException("We need at least one screen " +
                        "with the taskbar at the bottom position.");
            }
            bounds = getGraphicsConfig().getBounds();

            SwingUtilities.invokeAndWait(() -> {
                mainFrame = new JFrame("bug5078214");
                mainFrame.setLayout(new BorderLayout());
                mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                JButton button = new JButton("Button with tooltip") {
                    public Point getToolTipLocation(MouseEvent ev) {
                        return new Point(100, 100);
                    }
                };
                mainFrame.add(button, BorderLayout.CENTER);
                button.setToolTipText("ToolTip for this button");

                // Position frame
                mainFrame.setSize(200, 200);
                int x = bounds.x + 200;
                int y = bounds.y + bounds.height - insets.bottom - 100;
                mainFrame.setLocation(x, y);
                mainFrame.setVisible(true);
            });

            robot = new Robot();
            robot.waitForIdle();
            robot.delay(1000);

            test(bounds, insets);

            if (!passed) {
                throw new RuntimeException("ToolTip shown outside of the visible area. Test failed.");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (mainFrame != null) {
                    mainFrame.dispose();
                }
            });
        }
    }

    public static void test(Rectangle b, Insets i) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false);
            ToolTipManager.sharedInstance().setInitialDelay(100);
        });

        robot.mouseMove(b.x + 300, b.y + b.height - i.bottom - 10);
        robot.delay(500);
        Window[] ow = mainFrame.getOwnedWindows();
        if (ow == null || ow.length < 1) {
            throw new RuntimeException("No owned windows for JFrame - no tooltip shown?");
        }

        Window ttwnd = ow[0];
        int wy = ttwnd.getBounds().y + ttwnd.getBounds().height - 1;
        passed = wy < (b.y + b.height - i.bottom);
    }

    public static GraphicsConfiguration getGraphicsConfig() {
        GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment()
                                                      .getScreenDevices();
        for (GraphicsDevice device : devices) {
            GraphicsConfiguration config = device.getDefaultConfiguration();
            insets = Toolkit.getDefaultToolkit().getScreenInsets(config);
            if (insets.bottom != 0) {
                return config;
            }
        }
        return null;
    }
}
