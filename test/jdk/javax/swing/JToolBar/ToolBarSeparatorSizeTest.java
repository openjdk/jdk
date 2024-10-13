/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4346610
 * @key headful
 * @summary Verifies if Adding JSeparator to JToolBar "pushes" buttons added
 *          after separator to edge
 * @run main ToolBarSeparatorSizeTest
 */
import java.awt.BorderLayout;
import java.awt.image.BufferedImage;
import java.awt.Rectangle;
import java.awt.Robot;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import javax.imageio.ImageIO;

public class ToolBarSeparatorSizeTest {

    private static JFrame frame;
    private static JSeparator separator;
    private static JToolBar toolBar;
    private static volatile Rectangle toolBarBounds;
    private static volatile int sepWidth;
    private static volatile int sepPrefWidth;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(100);
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame("ToolBar Separator Test");
                toolBar = new JToolBar();
                toolBar.add(new JButton("button 1"));
                toolBar.add(new JButton("button 2"));
                separator = new JSeparator(SwingConstants.VERTICAL);
                toolBar.add(separator);
                toolBar.add(new JButton("button 3"));
                frame.getContentPane().setLayout(new BorderLayout());
                frame.getContentPane().add(toolBar, BorderLayout.NORTH);
                frame.getContentPane().add(new JPanel(), BorderLayout.CENTER);
                frame.setSize(400, 100);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(() -> {
                toolBarBounds = new Rectangle(toolBar.getLocationOnScreen(),
                                              toolBar.getSize());
                sepWidth = separator.getSize().width;
                sepPrefWidth = separator.getPreferredSize().width;
            });
            if (sepWidth != sepPrefWidth) {
                System.out.println("size " + sepWidth);
                System.out.println("preferredSize " + sepPrefWidth);
                BufferedImage img = robot.createScreenCapture(toolBarBounds);
                ImageIO.write(img, "png", new java.io.File("image.png"));
                throw new RuntimeException("Separator size is too wide");
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
