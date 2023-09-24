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
 * @summary ToolTip is shown partially when the application is near the bottom of screen.
 * @library ../regtesthelpers
 * @build Util JRobot
 * @key headful
 * @run main bug5078214
 */

import java.awt.BorderLayout;
import java.awt.event.MouseEvent;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;

public class bug5078214 {
    static volatile boolean passed = false;

    static volatile JFrame mainFrame;
    static volatile Rectangle bounds;
    static volatile Insets insets;
    static volatile JRobot r;

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                GraphicsEnvironment ge = GraphicsEnvironment.
                        getLocalGraphicsEnvironment();
                GraphicsDevice[] gs = ge.getScreenDevices();
                GraphicsConfiguration testGraphics = null;
                for (int j = 0; j < gs.length; j++) {
                    GraphicsDevice gd = gs[j];
                    GraphicsConfiguration[] gc =
                            gd.getConfigurations();
                    for (int i = 0; i < gc.length; i++) {
                        insets = Toolkit.getDefaultToolkit().getScreenInsets(gc[i]);
                        if (insets.bottom != 0) {
                            testGraphics = gc[i];
                            break;
                        }
                    }
                }

                if (testGraphics == null) {
                    System.out.print("We need at least one screen with ");
                    System.out.println("the taskbar at the bottom position.");
                    System.out.println("Testing skipped.");
                    return;
                }

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
                bounds = testGraphics.getBounds();
                int x = bounds.x + 200;
                int y = bounds.y + bounds.height - insets.bottom - 100;
                mainFrame.setLocation(x, y);
                mainFrame.setVisible(true);
            });

            r = new JRobot(true);
            Util.blockTillDisplayed(mainFrame);
            r.waitForIdle();
            r.delay(1000);

            test(bounds, insets);
            r.waitForIdle();
            r.delay(1000);

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

    public static void test(Rectangle b, Insets i) {
        r.delay(500);
        ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false);
        ToolTipManager.sharedInstance().setInitialDelay(100);
        r.mouseMove(b.x + 300, b.y + b.height - i.bottom - 10);
        r.delay(2000);
        Window[] ow = mainFrame.getOwnedWindows();
        if (ow == null || ow.length < 1) {
            System.out.println("No owned windows for JFrame - no tooltip shown?");
            passed = true;
            return;
        }

        Window ttwnd = ow[0];
        int wy = ttwnd.getBounds().y + ttwnd.getBounds().height - 1;
        if (wy < (b.y + b.height - i.bottom)) {
            // Position is Ok!
            passed = true;
        }
    }
}
