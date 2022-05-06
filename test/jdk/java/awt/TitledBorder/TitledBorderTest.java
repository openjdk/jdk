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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/*
 * @test
 * @key headful
 * @bug 8279614
 * @summary The left line of the TitledBorder is not painted on 150 scale factor
 * @requires (os.family == "windows")
 * @run main TitledBorderTest
 */

public class TitledBorderTest {

    public static final Dimension SIZE = new Dimension(120, 20);

    public static JFrame frame;
    public static JPanel contentPanel;
    public static JPanel parentPanel;
    public static JPanel childPanel;
    public static BufferedImage buff;
    public static Color highlight = Color.RED;
    public static Color shadow = Color.BLUE;
    public static boolean showFrame = false;

    private static final double[] scales =
            {1.00, 1.25, 1.50, 1.75, 2.00, 2.50, 3.00};

    private static final List<BufferedImage> images =
            new ArrayList<>(scales.length);

    private static final List<Point> panelLocations =
            new ArrayList<>(4);

    public static void main(String[] args) throws Exception {
        showFrame = args.length > 1 && "-show".equals(args[0]);
        try {
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        } catch (Exception e) {
            throw new RuntimeException("Could not get Windows laf.");
        }

        testScaling(showFrame);
    }

    private static void testScaling(boolean show) throws Exception {
        SwingUtilities.invokeAndWait(() -> createAndShowGUI(show));

        for (int i = 0; i < images.size(); i++) {
                BufferedImage img = images.get(i);
                double scaling = scales[i];

                // For vertical count the number of shadow / highlight in
                // the middle of the image at (x = SIZE.width / 2)
                // (there must be no background color between these two colours)
                // Then skip background until the next border where you count
                // shadow and highlight thickness

                 int x = SIZE.width / 2;
                 checkVerticalBorder(x, img, scaling);

                // For horizontal border, take the middle of each panel and
                // count the number of shadow and highlight pixels
                for (Point p : panelLocations) {
                    int y = (int) (p.y * scaling) + SIZE.height / 2;
                    System.out.println(scaling + " : " + y);

                     checkHorizontalBorder(y, img, scaling);
                }
        }
    }

    private static void hBorderLoop(int x1, int x2, int y, BufferedImage buff,
                                    Color c, double scaling) throws RuntimeException {
        int thickness = 0;
        for (int i = x1; i < x2; i++) {
            if (buff.getRGB(i, y) == c.getRGB()) thickness++;
        }

        int expected = (int) Math.floor(scaling);
        if (thickness > expected) {
            throw new RuntimeException("Horizontal Border drawn too thick. Thickness: "
                    + thickness + " Scaling: " + scaling + " y: " + y);
        } else if (thickness < expected) {
            throw new RuntimeException("Horizontal Border drawn too thin. Thickness: "
                    + thickness + " Scaling: " + scaling + " y: " + y);
        }
    }

    private static void checkHorizontalBorder(int y, BufferedImage img, double scaling) throws RuntimeException {
        // checking left border
        hBorderLoop(0, (int) (Math.floor(scaling)+(5*scaling)), y, img, shadow, scaling);
        hBorderLoop((int) Math.floor(scaling), (int) (Math.floor(scaling)*2+5*scaling), y, img, highlight, scaling);

        // checking right border
        hBorderLoop(img.getWidth() - ((int) (Math.floor(scaling)*2+5*scaling)), img.getWidth() - ((int) Math.floor(scaling)),
                y, img, shadow, scaling);
        hBorderLoop(img.getWidth() - ((int) (Math.floor(scaling)+5*scaling)), img.getWidth(),
                y, img, highlight, scaling);
    }

    private static void verifyThickness(int x, int thickness, double scaling) {
        int expected = (int) Math.floor(scaling);
        if (thickness < expected) throw new RuntimeException("Vertical Border drawn too thin. Thickness: "
                + thickness + " Scaling: " + scaling + " x: " + x);
        if (thickness > expected) throw new RuntimeException("Vertical Border drawn too thick. Thickness: "
                + thickness + " Scaling: " + scaling + " x: " + x);
    }

    private static void checkVerticalBorder(int x, BufferedImage img, double scaling) throws RuntimeException {
        int thickness = 0;
        boolean checkShadow = false;
        boolean checkHighlight = false;
        for (int i = 0; i < img.getHeight(); i++) {
            int color = img.getRGB(x,i);
            if (!checkHighlight && !checkShadow) {
                if (color == shadow.getRGB()) {
                    checkHighlight = true;
                    thickness++;
                } else if (color == highlight.getRGB()) {
                    throw new RuntimeException("Vertical Border was clipped or overdrawn."
                            + " Scaling: " + scaling + " x: " + x);
                } else {
                    continue;
                }
            } else if (checkHighlight) {
                if (color == shadow.getRGB()) {
                    thickness++;
                } else if (color == highlight.getRGB()) {
                    verifyThickness(x, thickness, scaling);
                    checkHighlight = false;
                    checkShadow = true;
                    thickness = 1;
                } else {
                    throw new RuntimeException("Vertical Border has empty space between highlight and shadow."
                            + " Scaling: " + scaling + " x: " + x);
                }
            } else {
                if (color == shadow.getRGB()) {
                    throw new RuntimeException("Border colors reversed.");
                } else if (color == highlight.getRGB()) {
                    thickness++;
                } else {
                    verifyThickness(x, thickness, scaling);
                    checkShadow = false;
                    thickness = 0;
                }
            }
        }
    }

    private static void createAndShowGUI(boolean showFrame) {
        // Render content panel
        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        for (int i = 0; i < 4; i++) {
            parentPanel = new JPanel(new BorderLayout());
            parentPanel.setBorder(BorderFactory.createEmptyBorder(0, i, 4, 0));

            childPanel = new JPanel(new BorderLayout());
            childPanel.setBorder(BorderFactory.createEtchedBorder(highlight, shadow));
            childPanel.add(Box.createRigidArea(SIZE), BorderLayout.CENTER);

            parentPanel.add(childPanel, BorderLayout.CENTER);
            contentPanel.add(parentPanel);
        }

        frame = new JFrame("Swing Test");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.getContentPane().add(contentPanel, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);

        for (double scaling : scales) {
            // Create BufferedImage
            BufferedImage buff = new BufferedImage((int) Math.ceil(contentPanel.getWidth() * scaling),
                    (int) Math.ceil(contentPanel.getHeight() * scaling),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D graph = buff.createGraphics();
            graph.scale(scaling, scaling);
            // Painting panel onto BufferedImage
            contentPanel.paint(graph);
            graph.dispose();
            // Save each image ? -- Here it's useful for debugging
            saveImage(buff, String.format("test%.2f.png", scaling));
            images.add(buff);
        }
        // Save coordinates of the panels
        Arrays.stream(contentPanel.getComponents())
                .map(Component::getLocation)
                .forEach(panelLocations::add);

        if (showFrame) {
            frame.setVisible(true);
        } else {
            frame.dispose();
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