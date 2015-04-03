/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

/* @test
   @bug 7123767
   @summary Wrong tooltip location in Multi-Monitor configurations
   @author Vladislav Karnaukhov
   @run main bug7123767
*/

import javax.swing.*;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;

public class bug7123767 extends JFrame {

    private static class TestFactory extends PopupFactory {

        private static TestFactory newFactory = new TestFactory();
        private static PopupFactory oldFactory;

        private TestFactory() {
            super();
        }

        public static void install() {
            if (oldFactory == null) {
                oldFactory = getSharedInstance();
                setSharedInstance(newFactory);
            }
        }

        public static void uninstall() {
            if (oldFactory != null) {
                setSharedInstance(oldFactory);
            }
        }

        // Actual test happens here
        public Popup getPopup(Component owner, Component contents, int x, int y) {
            GraphicsConfiguration mouseGC = testGC(MouseInfo.getPointerInfo().getLocation());
            if (mouseGC == null) {
                throw new RuntimeException("Can't find GraphicsConfiguration that mouse pointer belongs to");
            }

            GraphicsConfiguration tipGC = testGC(new Point(x, y));
            if (tipGC == null) {
                throw new RuntimeException("Can't find GraphicsConfiguration that tip belongs to");
            }

            if (!mouseGC.equals(tipGC)) {
                throw new RuntimeException("Mouse and tip GCs are not equal");
            }

            return super.getPopup(owner, contents, x, y);
        }

        private static GraphicsConfiguration testGC(Point pt) {
            GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice[] devices = environment.getScreenDevices();
            for (GraphicsDevice device : devices) {
                GraphicsConfiguration[] configs = device.getConfigurations();
                for (GraphicsConfiguration config : configs) {
                    Rectangle rect = config.getBounds();
                    Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(config);
                    adjustInsets(rect, insets);
                    if (rect.contains(pt))
                        return config;
                }
            }

            return null;
        }
    }

    private static final int MARGIN = 10;
    private static bug7123767 frame;
    private static Robot robot;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(new MetalLookAndFeel());
        setUp();
        testToolTip();
        TestFactory.uninstall();
    }

    // Creates a window that is stretched across all available monitors
    // and adds itself as ContainerListener to track tooltips drawing
    private bug7123767() {
        super();

        ToolTipManager.sharedInstance().setInitialDelay(0);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        TestFactory.install();

        JLabel label1 = new JLabel("no preferred location");
        label1.setToolTipText("tip");
        add(label1, BorderLayout.WEST);

        JLabel label2 = new JLabel("preferred location (20000, 20000)") {
            public Point getToolTipLocation(MouseEvent event) {
                return new Point(20000, 20000);
            }
        };

        label2.setToolTipText("tip");
        add(label2, BorderLayout.EAST);

        setUndecorated(true);
        pack();

        Rectangle rect = new Rectangle();
        GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] devices = environment.getScreenDevices();
        for (GraphicsDevice device : devices) {
            GraphicsConfiguration[] configs = device.getConfigurations();
            for (GraphicsConfiguration config : configs) {
                Insets localInsets = Toolkit.getDefaultToolkit().getScreenInsets(config);
                Rectangle localRect = config.getBounds();
                adjustInsets(localRect, localInsets);
                rect.add(localRect);
            }
        }
        setBounds(rect);
    }

    private static void setUp() throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                frame = new bug7123767();
                frame.setVisible(true);
            }
        });
    }

    // Moves mouse pointer to the corners of every GraphicsConfiguration
    private static void testToolTip() throws AWTException {

        robot = new Robot();
        robot.setAutoDelay(20);
        robot.waitForIdle();

        GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] devices = environment.getScreenDevices();
        for (GraphicsDevice device : devices) {
            GraphicsConfiguration[] configs = device.getConfigurations();
            for (GraphicsConfiguration config : configs) {
                Rectangle rect = config.getBounds();
                Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(config);
                adjustInsets(rect, insets);

                // Upper left
                glide(rect.x + rect.width / 2, rect.y + rect.height / 2,
                        rect.x + MARGIN, rect.y + MARGIN);
                robot.waitForIdle();

                // Lower left
                glide(rect.x + rect.width / 2, rect.y + rect.height / 2,
                        rect.x + MARGIN, rect.y + rect.height - MARGIN);
                robot.waitForIdle();

                // Upper right
                glide(rect.x + rect.width / 2, rect.y + rect.height / 2,
                        rect.x + rect.width - MARGIN, rect.y + MARGIN);
                robot.waitForIdle();

                // Lower right
                glide(rect.x + rect.width / 2, rect.y + rect.height / 2,
                        rect.x + rect.width - MARGIN, rect.y + rect.height - MARGIN);
                robot.waitForIdle();
            }
        }
    }

    private static void glide(int x0, int y0, int x1, int y1) throws AWTException {
        if (robot == null) {
            robot = new Robot();
            robot.setAutoDelay(20);
        }

        float dmax = (float) Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        float dx = (x1 - x0) / dmax;
        float dy = (y1 - y0) / dmax;

        robot.mouseMove(x0, y0);
        for (int i = 1; i <= dmax; i += 10) {
            robot.mouseMove((int) (x0 + dx * i), (int) (y0 + dy * i));
        }
    }

    private static void adjustInsets(Rectangle rect, final Insets insets) {
        rect.x += insets.left;
        rect.y += insets.top;
        rect.width -= (insets.left + insets.right);
        rect.height -= (insets.top + insets.bottom);
    }
}
