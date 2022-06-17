/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/*
 * @test
 * @bug 8279614
 * @summary The left line of the TitledBorder is not painted on 150 scale factor
 * @requires (os.family == "windows")
 * @run main ScaledEtchedBorderTest
 */

public class ScaledEtchedBorderTest {

    public static final Dimension SIZE = new Dimension(120, 20);

    public static Color highlight = Color.RED;
    public static Color shadow = Color.BLUE;

    private static final double[] scales =
            {1.00, 1.25, 1.50, 1.75, 2.00, 2.50, 3.00};

    private static final List<BufferedImage> images =
            new ArrayList<>(scales.length);

    private static final List<Point> panelLocations =
            new ArrayList<>(4);

    public static void main(String[] args) throws Exception {
        boolean showFrame = args.length > 0 && "-show".equals(args[0]);
        SwingUtilities.invokeAndWait(() -> testScaling(showFrame));
    }

    private static void testScaling(boolean show) {
        createGUI(show);

        for (int i = 0; i < scales.length; i++) {
            BufferedImage img = images.get(i);
            double scaling = scales[i];
            System.out.println("Testing scaling: " + scaling);


            // checking vertical border
            int x = SIZE.width / 2;
            checkVerticalBorder(x, img, scaling);

            for (Point p : panelLocations) {
                int y = (int) (p.y * scaling) + SIZE.height / 2;
                checkHorizontalBorder(y, img, scaling);
            }
        }
    }

    private static void checkHorizontalBorder(int y, BufferedImage img, double scaling) {
        int thickness = 0;
        boolean checkShadow = false;
        boolean checkHighlight = false;
        for (int x = 0; x < img.getWidth(); x++) {
            int color = img.getRGB(x, y);
            if (!checkHighlight && !checkShadow) {
                if (color == shadow.getRGB()) {
                    checkHighlight = true;
                    thickness++;
                } else if (color == highlight.getRGB()) {
                    throw new RuntimeException("Horizontal Border was clipped or overdrawn.");
                }
            } else if (checkHighlight) {
                if (color == shadow.getRGB()) {
                    thickness++;
                } else if (color == highlight.getRGB()) {
                    verifyThickness(x, y, thickness, scaling, "Horizontal");
                    checkHighlight = false;
                    checkShadow = true;
                    thickness = 1;
                } else {
                    throw new RuntimeException("Horizontal Border has empty space between highlight and shadow.");
                }
            } else {
                if (color == shadow.getRGB()) {
                    throw new RuntimeException("Border colors reversed.");
                } else if (color == highlight.getRGB()) {
                    thickness++;
                } else {
                    verifyThickness(x, y, thickness, scaling, "Horizontal");
                    checkShadow = false;
                    thickness = 0;
                }
            }
        }
    }

    private static void verifyThickness(int x, int y, int thickness, double scaling, String orientation) {
        int expected = (int) Math.floor(scaling);
        if (thickness != expected) {
            throw new RuntimeException("Unexpected " + orientation + " Border thickness at x:"
                                       + x + " y: " + y + ". Expected: " + expected + " Actual: " + thickness);
        }
    }

    private static void checkVerticalBorder(int x, BufferedImage img, double scaling) {
        int thickness = 0;
        boolean checkShadow = false;
        boolean checkHighlight = false;
        for (int y = 0; y < img.getHeight(); y++) {
            int color = img.getRGB(x, y);
            if (!checkHighlight && !checkShadow) {
                if (color == shadow.getRGB()) {
                    checkHighlight = true;
                    thickness++;
                } else if (color == highlight.getRGB()) {
                    throw new RuntimeException("Vertical Border was clipped or overdrawn.");
                }
            } else if (checkHighlight) {
                if (color == shadow.getRGB()) {
                    thickness++;
                } else if (color == highlight.getRGB()) {
                    verifyThickness(x, y, thickness, scaling, "Vertical");
                    checkHighlight = false;
                    checkShadow = true;
                    thickness = 1;
                } else {
                    throw new RuntimeException("Vertical Border has empty space between highlight and shadow.");
                }
            } else {
                if (color == shadow.getRGB()) {
                    throw new RuntimeException("Border colors reversed.");
                } else if (color == highlight.getRGB()) {
                    thickness++;
                } else {
                    verifyThickness(x, y, thickness, scaling, "Vertical");
                    checkShadow = false;
                    thickness = 0;
                }
            }
        }
    }

    private static void createGUI(boolean show) {
        // Render content panel
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        Dimension childSize = null;
        for (int i = 0; i < 4; i++) {
            JPanel childPanel = new JPanel(new BorderLayout());
            childPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createEmptyBorder(0, i, 4, 4),
                    BorderFactory.createEtchedBorder(highlight, shadow)));
            childPanel.add(Box.createRigidArea(SIZE), BorderLayout.CENTER);

            contentPanel.add(childPanel);
            if (childSize == null) {
                childSize = childPanel.getPreferredSize();
            }
            childPanel.setBounds(0, childSize.height * i, childSize.width, childSize.height);
        }

        contentPanel.setSize(childSize.width, childSize.height * 4);

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
        for (Component comp : contentPanel.getComponents()) {
            panelLocations.add(comp.getLocation());
        }

        if (show) {
            JFrame frame = new JFrame("Swing Test");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.getContentPane().add(contentPanel, BorderLayout.CENTER);
            frame.pack();
            frame.setLocationRelativeTo(null);
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
}
