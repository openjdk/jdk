/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4846413
 * @summary Checks if No tooltip modification when no KeyStroke modifier
 * @library ../../regtesthelpers
 * @build Util
 * @run main bug4846413
 */

import java.io.File;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JToolTip;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.imageio.ImageIO;
import javax.swing.plaf.metal.MetalToolTipUI;

public class bug4846413 {

    private static volatile boolean isTooltipAdded;
    private static JButton button;
    private static JFrame frame;
    private static Robot robot;

    public static void main(String[] args) throws Exception {
        try {
            robot = new Robot();
            robot.setAutoDelay(100);

            UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");

            SwingUtilities.invokeAndWait(() -> createAndShowGUI());

            robot.waitForIdle();
	    robot.delay(1000);

            Point movePoint = getButtonPoint();
            robot.mouseMove(movePoint.x, movePoint.y);
	    if (System.getProperty("os.name").contains("OS X")) {
                String version = System.getProperty("os.version", "");
                if (version.startsWith("14.")) {
                    robot.mouseMove(movePoint.x, movePoint.y);
                }
            }
            robot.waitForIdle();

            long timeout = System.currentTimeMillis() + 9000;
            while (!isTooltipAdded && (System.currentTimeMillis() < timeout)) {
                try {Thread.sleep(500);} catch (Exception e) {}
            }

            SwingUtilities.invokeAndWait(() -> checkToolTip());
	} finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void checkToolTip() {
        JToolTip tooltip = (JToolTip) Util.findSubComponent(JFrame.getFrames()[0], "JToolTip");

        if (tooltip == null) {
            try {
                Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                BufferedImage img = robot.createScreenCapture(
                            new Rectangle(0, 0, (int)screenSize.getWidth(),
                                                (int)screenSize.getHeight()));
                ImageIO.write(img, "png", new File("screen.png"));
	    } catch (Exception e) {}
            throw new RuntimeException("Tooltip has not been found!");
        }

        MetalToolTipUI tooltipUI = (MetalToolTipUI) MetalToolTipUI.createUI(tooltip);
        tooltipUI.installUI(tooltip);

        if (!"-Insert".equals(tooltipUI.getAcceleratorString())) {
            throw new RuntimeException("Tooltip acceleration is not properly set!");
        }
    }

    private static Point getButtonPoint() throws Exception {
        final Point[] result = new Point[1];

        SwingUtilities.invokeAndWait(new Runnable() {

            @Override
            public void run() {
                Point p = button.getLocationOnScreen();
                Dimension size = button.getSize();
                result[0] = new Point(p.x + size.width / 2, p.y + size.height / 2);
            }
        });
        return result[0];
    }

    private static void createAndShowGUI() {
        frame = new JFrame("Test");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(200, 200);
        frame.setLocationRelativeTo(null);

        button = new JButton("Press me");
        button.setToolTipText("test");
        button.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0, true), "someCommand");
        button.getActionMap().put("someCommand", null);
        frame.getContentPane().add(button);

        JLayeredPane layeredPane = (JLayeredPane) Util.findSubComponent(
                frame, "JLayeredPane");
        layeredPane.addContainerListener(new ContainerAdapter() {

            @Override
            public void componentAdded(ContainerEvent e) {
                isTooltipAdded = true;
            }
        });

        frame.setVisible(true);
    }
}
