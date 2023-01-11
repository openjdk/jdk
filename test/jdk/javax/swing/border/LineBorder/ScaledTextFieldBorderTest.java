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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.LineBorder;

/*
 * @test
 * @bug 8282958
 * @summary Verify all the borders are rendered consistently for a JTextField
 *          in Windows LaF which uses LineBorder
 * @requires (os.family == "windows")
 * @run main ScaledTextFieldBorderTest
 */
public class ScaledTextFieldBorderTest {

    private static final double[] scales = {
            1.00, 1.25, 1.50, 1.75,
            2.00, 2.25, 2.50, 2.75,
            3.00
    };

    private static final List<BufferedImage> images =
            new ArrayList<>(scales.length);

    private static final List<Point> panelLocations =
            new ArrayList<>(4);

    private static Dimension textFieldSize;

    public static void main(String[] args) throws Exception {
        Collection<String> params = Arrays.asList(args);
        final boolean showFrame = params.contains("-show");
        final boolean saveImages = params.contains("-save");
        SwingUtilities.invokeAndWait(() -> testScaling(showFrame, saveImages));
    }

    private static void testScaling(boolean showFrame, boolean saveImages) {
        JComponent content = createUI();
        if (showFrame) {
            showFrame(content);
        }

        paintToImages(content, saveImages);
        verifyBorderRendering(saveImages);
    }

    private static void verifyBorderRendering(final boolean saveImages) {
        String errorMessage = null;
        int errorCount = 0;
        for (int i = 0; i < images.size(); i++) {
            BufferedImage img = images.get(i);
            double scaling = scales[i];
            try {
                int thickness = (int) Math.floor(scaling);

                checkVerticalBorders(textFieldSize.width / 2, thickness, img);

                for (Point p : panelLocations) {
                    int y = (int) (p.y * scaling) + textFieldSize.height / 2;
                    checkHorizontalBorder(y, thickness, img);
                }
            } catch (Error | Exception e) {
                if (errorMessage == null) {
                    errorMessage = e.getMessage();
                }
                errorCount++;

                System.err.printf("Scaling: %.2f\n", scaling);
                e.printStackTrace();

                // Save the image if it wasn't already saved
                if (!saveImages) {
                    saveImage(img, getImageFileName(scaling));
                }
            }
        }

        if (errorCount > 0) {
            throw new Error("Test failed: "
                    + errorCount + " error(s) detected - "
                    + errorMessage);
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

    private static final int transparentColor = 0x00000000;
    private static int panelColor;
    private static int borderColor;
    private static int insideColor;

    private static void checkBorder(final int xStart, final int yStart,
                                    final int xStep,  final int yStep,
                                    final int thickness,
                                    final BufferedImage img) {
        final int width = img.getWidth();
        final int height = img.getHeight();

        State state = State.BACKGROUND;
        int borderThickness = -1;

        int x = xStart;
        int y = yStart;
        do {
            do {
                final int color = img.getRGB(x, y);
                switch (state) {
                    case BACKGROUND:
                        if (color == borderColor) {
                            state = State.LEFT;
                            borderThickness = 1;
                        } else if (color != panelColor
                                && color != transparentColor) {
                            throwUnexpectedColor(x, y, color);
                        }
                        break;

                    case LEFT:
                        if (color == borderColor) {
                            borderThickness++;
                        } else if (color == insideColor) {
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
                        if (color == borderColor) {
                            state = State.RIGHT;
                            borderThickness = 1;
                        } else if (color != insideColor) {
                            throwUnexpectedColor(x, y, color);
                        }
                        break;

                    case RIGHT:
                        if (color == borderColor) {
                            borderThickness++;
                        } else if (color == panelColor) {
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

        if (state != State.BACKGROUND) {
            throw new Error(String.format("Border is not rendered correctly at %d, %d", x, y));
        }
    }

    private static void throwWrongThickness(int thickness, int borderThickness, int x, int y) {
        throw new Error(
                String.format("Wrong border thickness at %d, %d: %d vs %d",
                        x, y, borderThickness, thickness));
    }

    private static void throwUnexpectedColor(int x, int y, int color) {
        throw new Error(
                String.format("Unexpected color at %d, %d: %08x",
                        x, y, color));
    }

    private static JComponent createUI() {
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        final LineBorder tfBorder = new LineBorder(Color.RED);

        for (int i = 0; i < 4; i++) {
            JTextField textField = new JTextField(10);
            textField.setBorder(tfBorder);
            Box childPanel = Box.createHorizontalBox();
            childPanel.add(Box.createHorizontalStrut(i));
            childPanel.add(textField);
            childPanel.add(Box.createHorizontalStrut(4));

            contentPanel.add(childPanel);
            contentPanel.add(Box.createVerticalStrut(4));

            if (textFieldSize == null) {
                textFieldSize = textField.getPreferredSize();
                borderColor = tfBorder.getLineColor().getRGB();
                insideColor = textField.getBackground().getRGB();
            }
            textField.setBounds(i, 0, textFieldSize.width, textFieldSize.height);
            childPanel.setBounds(0, (textFieldSize.height + 4) * i,
                    textFieldSize.width + i + 4, textFieldSize.height);
            panelLocations.add(childPanel.getLocation());
        }

        contentPanel.setSize(textFieldSize.width + 4,
                (textFieldSize.height + 4) * 4);

        panelColor = contentPanel.getBackground().getRGB();

        return contentPanel;
    }

    private static void showFrame(JComponent content) {
        JFrame frame = new JFrame("Text Field Border Test");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.getContentPane().add(content, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void paintToImages(final JComponent content,
                                      final boolean saveImages) {
        for (double scaling : scales) {
            BufferedImage image =
                    new BufferedImage((int) Math.ceil(content.getWidth() * scaling),
                            (int) Math.ceil(content.getHeight() * scaling),
                            BufferedImage.TYPE_INT_ARGB);

            Graphics2D g2d = image.createGraphics();
            g2d.scale(scaling, scaling);
            content.paint(g2d);
            g2d.dispose();

            if (saveImages) {
                saveImage(image, getImageFileName(scaling));
            }
            images.add(image);
        }
    }

    private static String getImageFileName(final double scaling) {
        return String.format("test%.2f.png", scaling);
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
