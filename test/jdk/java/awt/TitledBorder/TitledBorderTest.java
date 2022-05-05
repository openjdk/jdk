/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.Border;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/*
 * @test
 * @key headful
 * @bug 8279614
 * @summary The left line of the TitledBorder is not painted on 150 scale factor
 * @requires (os.family == "windows")
 * @run main TitledBorderTest
 */

public class TitledBorderTest {

    public static JFrame frame;
    public static JPanel contentPanel;
    public static JPanel parentPanel;
    public static JPanel childPanel;
    public static BufferedImage buff;
    public static Color highlight = Color.RED;
    public static Color shadow = Color.BLUE;
    public static boolean showFrame = true;

    public static void main(String[] args) throws Exception {
        try {
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        } catch (Exception e) {
            throw new RuntimeException("Could not get Windows laf.");
        }

        for (double scaling : new double[] {1.50}) {
            testScaling(scaling, showFrame);
        }
    }

    private static void testScaling(double scaling, boolean show) throws Exception {
        SwingUtilities.invokeAndWait(() -> createAndShowGUI(scaling, show));
        Robot robot = new Robot();

        while(showFrame && frame.isVisible()) Thread.sleep(500);

        robot.waitForIdle();
        // testing left edge
        checkVerticalBorder(15, 70, 20, 80, highlight, scaling);
        checkVerticalBorder(18, 120, 23, 130, highlight, scaling);
        checkVerticalBorder(20, 170, 25, 180, highlight, scaling);
        checkVerticalBorder(22, 220, 28, 230, highlight, scaling);

        // testing right edge

        // testing top edge

        // testing bottom edge

    }

    private static void checkHorizontalBorder(int x1, int y1, int x2, int y2,
                                              Color color, double scaling) throws RuntimeException {
        for (int j = x1; j < x2; j++) {
            int thickness = 0;
            for (int i = y1; i < y2; i++) {
                if (buff.getRGB(i, j) == color.getRGB()) thickness++;
            }
            if (thickness > Math.floor(scaling)) {
                System.out.println(y1 + " " + y2 + " " + thickness);
                saveImage(buff, "test.png");
                throw new RuntimeException("Border drawn too thick.");
            } else if (thickness < Math.floor(scaling)) {
                System.out.println(y1 + " " + y2 + " " + thickness);
                saveImage(buff, "test.png");
                throw new RuntimeException("BorderLayout was clipped or overdrawn.");
            }
        }
    }

    private static void checkVerticalBorder(int x1, int y1, int x2, int y2,
                                            Color color, double scaling) throws RuntimeException {
        for (int j = y1; j < y2; j++) {
            int thickness = 0;
            for (int i = x1; i < x2; i++) {
                if (buff.getRGB(i, j) == color.getRGB()) thickness++;
            }
            if (thickness > Math.floor(scaling)) {
                System.out.println(y1 + " " + y2 + " " + thickness);
                saveImage(buff, "test.png");
                throw new RuntimeException("Border drawn too thick.");
            } else if (thickness < Math.floor(scaling)) {
                System.out.println(y1 + " " + y2 + " " + thickness);
                saveImage(buff, "test.png");
                throw new RuntimeException("BorderLayout was clipped or overdrawn.");
            }
        }
    }

    private static void createAndShowGUI(double scaling, boolean showFrame) {
        // Render content panel
        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setSize(new java.awt.Dimension(300, 200));

        for (int i = 0; i < 4; i++) {
            parentPanel = new JPanel(new BorderLayout());
            parentPanel.setBorder(BorderFactory.createEmptyBorder(5, 5 + i, 5, 5));

            childPanel = new JPanel(new BorderLayout());
            childPanel.setBorder(BorderFactory.createEtchedBorder(highlight, shadow));
            childPanel.add(new JCheckBox(), BorderLayout.CENTER);

            parentPanel.add(childPanel, BorderLayout.CENTER);
            contentPanel.add(parentPanel);
        }

        // Create BufferedImage
        buff = new BufferedImage(contentPanel.getWidth() * ((int) Math.ceil(scaling)),
                contentPanel.getHeight() * ((int) Math.ceil(scaling)), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graph = buff.createGraphics();

        // Set affine transform
        graph.scale(scaling, scaling);

        // Painting panel onto BufferedImage
        contentPanel.paint(graph);
        graph.dispose();

        if (showFrame) {
            frame = new JFrame("Swing Test");
            frame.setSize(300, 200);
            frame.getContentPane().add(contentPanel, BorderLayout.CENTER);
            frame.setVisible(true);
        }
    }

    private static void saveImage(BufferedImage image, String filename) {
        try {
            ImageIO.write(image, "png", new File(filename));
        } catch (IOException e) {
            // Don't propagate the exception
            e.printStackTrace();
        }
    }

    private static void setLookAndFeel(UIManager.LookAndFeelInfo laf) {
        try {
            UIManager.setLookAndFeel(laf.getClassName());
            System.out.println(laf.getName());
        } catch (UnsupportedLookAndFeelException ignored) {
            System.out.println("Unsupported LookAndFeel: " + laf.getClassName());
        } catch (ClassNotFoundException | InstantiationException |
                IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
