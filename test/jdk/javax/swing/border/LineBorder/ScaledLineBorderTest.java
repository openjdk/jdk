/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import static sun.java2d.pipe.Region.clipRound;

/*
 * @test
 * @bug 8282958 8349188
 * @summary Verify LineBorder edges have the same width
 * @requires (os.family == "windows")
 * @modules java.desktop/sun.java2d.pipe
 * @run main ScaledLineBorderTest
 */
public class ScaledLineBorderTest {
    private static final Dimension SIZE = new Dimension(250, 50);

    private static final Color OUTER_COLOR = Color.BLACK;
    private static final Color BORDER_COLOR = Color.RED;
    private static final Color INSIDE_COLOR = Color.WHITE;
    private static final Color TRANSPARENT_COLOR = new Color(0x00000000, true);

    private static final double[] scales =
            {1.00, 1.25, 1.50, 1.75, 2.00, 2.50, 3.00};
    private static final int[] thickness = {1, 4, 10, 15};

    private record TestImage(BufferedImage image,
                             List<Point> panelLocations,
                             double scale,
                             int thickness) {
    }

    private record TestUI(JComponent content,
                          List<Point> panelLocations,
                          int thickness) {
    }


    public static void main(String[] args) throws Exception {
        Collection<String> params = Arrays.asList(args);
        final boolean showFrame = params.contains("-show");
        final boolean saveImages = params.contains("-save");
        SwingUtilities.invokeAndWait(() -> testScaling(showFrame, saveImages));
    }

    private static void testScaling(boolean showFrame, boolean saveImages) {
        for (int thickness : thickness) {
            TestUI testUI = createUI(thickness);
            if (showFrame) {
                showFrame(testUI.content);
            }

            List<TestImage> images = paintToImages(testUI, saveImages);
            verifyBorderRendering(images, saveImages);
        }

        if (errorCount > 0) {
            throw new Error("Test failed: "
                    + errorCount + " error(s) detected - "
                    + errorMessage);
        }

    }

    private static String errorMessage = null;
    private static int errorCount = 0;

    private static void verifyBorderRendering(final List<TestImage> images,
                                              final boolean saveImages) {
        for (TestImage test : images) {
            final BufferedImage img = test.image;
            final int effectiveThickness = clipRound(test.thickness * test.scale);
            try {
                checkVerticalBorders((int) (SIZE.width * test.scale / 2), effectiveThickness, img);

                for (Point p : test.panelLocations) {
                    int y = (int) ((p.y + (SIZE.height / 2)) * test.scale);
                    checkHorizontalBorder(y, effectiveThickness, img);
                }
            } catch (Error e) {
                if (errorMessage == null) {
                    errorMessage = e.getMessage();
                }
                errorCount++;

                System.err.printf("Scale: %.2f; thickness: %d, effective: %d\n",
                        test.scale, test.thickness, effectiveThickness);
                e.printStackTrace();

                saveImage(img, getImageFileName(test.scale, test.thickness));
            }
        }
    }

    private static void checkVerticalBorders(final int x,
                                             final int thickness,
                                             final BufferedImage img) {
        checkBorder(x, 0,
                0, 1,
                thickness, img);
    }

    private static void checkHorizontalBorder(final int y,
                                              final int thickness,
                                              final BufferedImage img) {
        checkBorder(0, y,
                1, 0,
                thickness, img);
    }

    private enum State {
        BACKGROUND, LEFT, INSIDE, RIGHT
    }

    private static void checkBorder(final int xStart, final int yStart,
                                    final int xStep,  final int yStep,
                                    final int thickness,
                                    final BufferedImage img) {
        final int width = img.getWidth();
        final int height = img.getHeight();

        State state = State.BACKGROUND;
        int borderThickness = 0;

        int x = xStart;
        int y = yStart;
        do {
            do {
                final int color = img.getRGB(x, y);
                switch (state) {
                    case BACKGROUND:
                        if (color == BORDER_COLOR.getRGB()) {
                            state = State.LEFT;
                            borderThickness = 1;
                        } else if (color != OUTER_COLOR.getRGB()
                                && color != TRANSPARENT_COLOR.getRGB()) {
                            throwUnexpectedColor(x, y, color);
                        }
                        break;

                    case LEFT:
                        if (color == BORDER_COLOR.getRGB()) {
                            borderThickness++;
                        } else if (color == INSIDE_COLOR.getRGB()) {
                            if (borderThickness != thickness) {
                                throwWrongThickness(thickness, borderThickness, x, y);
                            }
                            state = State.INSIDE;
                            borderThickness = 0;
                        } else {
                            throwUnexpectedColor(x, y, color);
                        }
                        break;

                    case INSIDE:
                        if (color == BORDER_COLOR.getRGB()) {
                            state = State.RIGHT;
                            borderThickness = 1;
                        } else if (color != INSIDE_COLOR.getRGB()) {
                            throwUnexpectedColor(x, y, color);
                        }
                        break;

                    case RIGHT:
                        if (color == BORDER_COLOR.getRGB()) {
                            borderThickness++;
                        } else if (color == OUTER_COLOR.getRGB()) {
                            if (borderThickness != thickness) {
                                throwWrongThickness(thickness, borderThickness, x, y);
                            }
                            state = State.BACKGROUND;
                            borderThickness = 0;
                        } else {
                            throwUnexpectedColor(x, y, color);
                        }
                }
            } while (yStep > 0 && ((y += yStep) < height));
        } while (xStep > 0 && ((x += xStep) < width));
    }

    private static void throwWrongThickness(int thickness, int borderThickness,
                                            int x, int y) {
        throw new Error(
                String.format("Wrong border thickness at %d, %d: %d vs %d",
                        x, y, borderThickness, thickness));
    }

    private static void throwUnexpectedColor(int x, int y, int color) {
        throw new Error(
                String.format("Unexpected color at %d, %d: %08x",
                        x, y, color));
    }

    private static TestUI createUI(int thickness) {
        Box contentPanel = Box.createVerticalBox();
        contentPanel.setBackground(OUTER_COLOR);

        List<Point> panelLocations = new ArrayList<>(4);

        Dimension childSize = null;
        for (int i = 0; i < 4; i++) {
            JComponent filler = new JPanel(null);
            filler.setBackground(INSIDE_COLOR);
            filler.setPreferredSize(SIZE);
            filler.setBounds(i, 0, SIZE.width, SIZE.height);
            filler.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, thickness));

            JPanel childPanel = new JPanel(new BorderLayout());
            childPanel.setBorder(BorderFactory.createEmptyBorder(0, i, 4, 4));
            childPanel.add(filler, BorderLayout.CENTER);
            childPanel.setBackground(OUTER_COLOR);

            contentPanel.add(childPanel);
            if (childSize == null) {
                childSize = childPanel.getPreferredSize();
            }
            childPanel.setBounds(0, childSize.height * i, childSize.width, childSize.height);

            panelLocations.add(childPanel.getLocation());
        }

        contentPanel.setSize(childSize.width, childSize.height * 4);

        return new TestUI(contentPanel, panelLocations, thickness);
    }

    private static void showFrame(JComponent content) {
        JFrame frame = new JFrame("Scaled Line Border Test");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.getContentPane().add(content, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static List<TestImage> paintToImages(final TestUI testUI,
                                                 final boolean saveImages) {
        final List<TestImage> images = new ArrayList<>(scales.length);
        final JComponent content = testUI.content;
        for (double scale : scales) {
            BufferedImage image =
                    new BufferedImage((int) Math.ceil(content.getWidth() * scale),
                            (int) Math.ceil(content.getHeight() * scale),
                            BufferedImage.TYPE_INT_ARGB);

            Graphics2D g2d = image.createGraphics();
            g2d.scale(scale, scale);
            content.paint(g2d);
            g2d.dispose();

            if (saveImages) {
                saveImage(image, getImageFileName(scale, testUI.thickness));
            }
            images.add(new TestImage(image, testUI.panelLocations,
                    scale, testUI.thickness));
        }
        return images;
    }

    private static String getImageFileName(final double scaling,
                                           final int thickness) {
        return String.format("test%02d@%.2f.png", thickness, scaling);
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
