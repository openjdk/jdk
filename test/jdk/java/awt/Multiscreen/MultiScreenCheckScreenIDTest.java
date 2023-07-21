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

import java.awt.AWTException;
import java.awt.Color;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;

import javax.swing.JWindow;
import javax.swing.SwingUtilities;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/*
 * @test
 * @bug 8280482
 * @key headful
 * @requires (os.family == "linux")
 * @library /java/awt/regtesthelpers
 * @summary Test to check if window GC doesn't change within same screen.
 * @run main MultiScreenCheckScreenIDTest
 */

public class MultiScreenCheckScreenIDTest {
    private static final int COLS = 12;
    private static final int ROWS = 8;
    private static final Color BACKGROUND = new Color(0, 0, 255, 64);
    private static GraphicsDevice[] screens;
    static List<Window> windowList = new ArrayList<>();

    static Robot robot;

    public static void main(final String[] args) throws Exception {
        try {
            createGUI();
            robot.delay(100);
            robot.waitForIdle();
        } finally {
            for (Window win : windowList) {
                win.dispose();
            }
        }
        System.out.println("Test Pass");
    }

    private static void createGUI() throws AWTException {
        new MultiScreenCheckScreenIDTest().createWindowGrid();
    }

    private void createWindowGrid() throws AWTException {
        screens = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getScreenDevices();

        if (screens.length < 2) {
            System.out.println("Testing aborted. Required min of 2 screens. " +
                    "Found : " + screens.length);
            return;
        }
        robot = new Robot();

        for (GraphicsDevice screen : screens) {
            Rectangle screenBounds = screen.getDefaultConfiguration().getBounds();

            for (Rectangle r : gridOfRectangles(screenBounds, COLS, ROWS)) {
                try {
                    SwingUtilities.invokeAndWait(() -> {
                        try {
                            createWindow(r);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                } catch (InterruptedException | InvocationTargetException e) {
                    e.printStackTrace();
                }
                robot.delay(50);
                robot.waitForIdle();
                if (windowList.get(windowList.size() - 1).getBounds().intersects
                        (screens[0].getDefaultConfiguration().getBounds())) {
                    if (!(windowList.get(windowList.size() - 1).
                            getGraphicsConfiguration().getBounds().
                            intersects(screens[0].getDefaultConfiguration().
                                    getBounds()))) {
                        throw new RuntimeException("Graphics configuration " +
                                "changed for screen 0");
                    }
                } else if (windowList.get(windowList.size() - 1).getBounds().
                        intersects(screens[1].getDefaultConfiguration().getBounds())) {
                    if (!(windowList.get(windowList.size() - 1).
                            getGraphicsConfiguration().getBounds().
                            intersects(screens[1].getDefaultConfiguration().
                                    getBounds()))) {
                        throw new RuntimeException("Graphics configuration " +
                                "changed for screen 1");
                    }
                }
            }
        }
    }

    private void createWindow(Rectangle bounds) {
        JWindow window = new JWindow();
        window.setBounds(bounds);
        window.setBackground(BACKGROUND);
        window.setAlwaysOnTop(true);
        window.setVisible(true);
        windowList.add(window);
    }

    private static List<Rectangle> gridOfRectangles(Rectangle r, int cols, int rows) {
        List<Rectangle> l = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            int y1 = r.y + (int) Math.round(r.height * (double) row / rows);
            int y2 = r.y + (int) Math.round(r.height * (double) (row + 1) / rows);
            for (int col = 0; col < cols; col++) {
                int x1 = r.x + (int) Math.round(r.width * (double) col / cols);
                int x2 = r.x + (int) Math.round(r.width * (double) (col + 1) / cols);
                l.add(new Rectangle(x1, y1, x2 - x1, y2 - y1));
            }
        }
        return l;
    }
}
