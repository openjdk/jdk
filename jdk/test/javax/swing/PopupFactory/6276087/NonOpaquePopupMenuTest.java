/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6276087
 * @author Romain Guy
 * @summary Tests opacity of a popup menu.
 */
import java.awt.*;
import java.awt.event.*;

import javax.swing.*;
import sun.awt.SunToolkit;

public class NonOpaquePopupMenuTest extends JFrame {

    private static JMenu fileMenu;

    public NonOpaquePopupMenuTest() {
        getContentPane().setBackground(java.awt.Color.RED);
        JMenuBar menuBar = new JMenuBar();
        fileMenu = new JMenu("File");
        JMenuItem menuItem = new JMenuItem("New");
        menuBar.add(fileMenu);
        setJMenuBar(menuBar);

        fileMenu.add(menuItem);
        fileMenu.getPopupMenu().setOpaque(false);

        setSize(new Dimension(640, 480));
        setVisible(true);
    }

    public static void main(String[] args) throws Throwable {
        SunToolkit toolkit = (SunToolkit) Toolkit.getDefaultToolkit();
        Robot robot = new Robot();
        robot.setAutoDelay(250);

        SwingUtilities.invokeAndWait(new Runnable() {

            @Override
            public void run() {
                new NonOpaquePopupMenuTest();
            }
        });

        toolkit.realSync();

        Point p = getMenuClickPoint();
        robot.mouseMove(p.x, p.y);
        robot.mousePress(InputEvent.BUTTON1_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_MASK);

        toolkit.realSync();

        if (isParentOpaque()) {
            throw new RuntimeException("Popup menu parent is opaque");
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

    private static Point getMenuClickPoint() throws Exception {
        final Point[] result = new Point[1];

        SwingUtilities.invokeAndWait(new Runnable() {

            @Override
            public void run() {
                Point p = fileMenu.getLocationOnScreen();
                Dimension size = fileMenu.getSize();

                result[0] = new Point(p.x + size.width / 2,
                        p.y + size.height / 2);
            }
        });

        return result[0];

    }
}
