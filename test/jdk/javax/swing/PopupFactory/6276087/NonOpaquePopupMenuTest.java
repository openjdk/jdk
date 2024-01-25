/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @key headful
 * @bug 6276087
 * @summary Tests opacity of a popup menu.
 */
import java.awt.Point;
import java.awt.Dimension;
import java.awt.Robot;
import java.awt.event.InputEvent;

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import static javax.swing.UIManager.LookAndFeelInfo;
import static javax.swing.UIManager.getInstalledLookAndFeels;
import static javax.swing.UIManager.setLookAndFeel;

public class NonOpaquePopupMenuTest {

    private static JMenu fileMenu;
    private static JFrame frame;
    private static final String AQUALAF="com.apple.laf.AquaLookAndFeel";
    private volatile static Point p;
    private volatile static Dimension size;

    private static void createUI() {
        frame = new JFrame();
        frame.getContentPane().setBackground(java.awt.Color.RED);
        JMenuBar menuBar = new JMenuBar();
        fileMenu = new JMenu("File");
        JMenuItem menuItem = new JMenuItem("New");
        menuBar.add(fileMenu);
        frame.setJMenuBar(menuBar);

        fileMenu.add(menuItem);
        fileMenu.getPopupMenu().setOpaque(false);

        frame.setSize(new Dimension(640, 480));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) throws Throwable {
        LookAndFeelInfo[] lookAndFeelInfoArray = getInstalledLookAndFeels();
        Robot robot = new Robot();
        robot.setAutoDelay(100);

        for (LookAndFeelInfo lookAndFeelInfo : lookAndFeelInfoArray) {
            try {
                System.out.println(lookAndFeelInfo.getClassName());
                if ( AQUALAF == lookAndFeelInfo.getClassName()) {
                    System.out.println("This test scenario is not applicable for" +
                            " Aqua LookandFeel and hence skipping the validation");
                    continue;
                }
                robot.delay(1000);
                setLookAndFeel(lookAndFeelInfo.getClassName());

                SwingUtilities.invokeAndWait(() -> createUI());

                robot.waitForIdle();
                robot.delay(1000);

                SwingUtilities.invokeAndWait(() -> {
                    p = fileMenu.getLocationOnScreen();
                    size = fileMenu.getSize();
                });
                robot.mouseMove(p.x + size.width / 2, p.y + size.height / 2);
                robot.waitForIdle();
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

                robot.waitForIdle();

                if (isParentOpaque()) {
                    throw new RuntimeException("Popup menu parent is opaque");
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

    private static boolean isParentOpaque() throws Exception {
        final boolean result[] = new boolean[1];

        SwingUtilities.invokeAndWait(new Runnable() {

            @Override
            public void run() {
                result[0] = fileMenu.getPopupMenu().getParent().isOpaque();
            }
        });

        return result[0];
    }
}
