/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, JetBrains s.r.o.. All rights reserved.
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
 * @bug 8266079
 * @summary [macosx] window rendering alpha composite test
 * @author Alexey Ushakov
 * @run main WindowAlphaCompositeTest
 * @requires (os.family == "mac")
 */

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import javax.swing.*;

public class WindowAlphaCompositeTest
{
    interface Validate {
        boolean validate(int x, int y, Color c);
    }
    static Color RED128 = new Color(128, 0, 0);
    static Color BLUE128 = new Color(0, 0, 128);
    static Color PURPLE128 = new Color(128, 0, 128);
    static Color RED_BLUE24 = new Color(230, 0, 24);
    static Validate redBlackCheck = (int x, int y, Color c) -> {
        Color expColor = Color.RED;
        if (x > 24 && x < 75) {
            expColor = Color.BLACK;
        }
        return validateColor(c, expColor);
    };

    static Validate redBlueCheck = (int x, int y, Color c) -> {
        Color expColor = Color.RED;
        if (x > 24 && x < 75) {
            expColor = Color.BLUE;
        }
        return validateColor(c, expColor);
    };

    static Validate redCheck = (int x, int y, Color c) -> {
        Color expColor = Color.RED;
        return validateColor(c, expColor);
    };

    static Validate redRed128Check = (int x, int y, Color c) -> {
        Color expColor = Color.RED;
        if (x > 24 && x < 75) {
            expColor = RED128;
        }
        return validateColor(c, expColor);
    };

    static Validate redBlue128Check = (int x, int y, Color c) -> {
        Color expColor = Color.RED;
        if (x > 24 && x < 75) {
            expColor = BLUE128;
        }
        return validateColor(c, expColor);
    };

    static Validate purple128Check = (int x, int y, Color c) -> {
        Color expColor = Color.RED;
        if (x > 24 && x < 75) {
            expColor = PURPLE128;
        }
        return validateColor(c, expColor);
    };
    static Validate redBlue24Check = (int x, int y, Color c) -> {
        Color expColor = Color.RED;
        if (x > 24 && x < 75) {
            expColor = RED_BLUE24;
        }
        return validateColor(c, expColor);
    };
    static Object[][] alphaComposites = {
            {AlphaComposite.Clear, redBlackCheck},
            {AlphaComposite.Dst, redCheck},
            {AlphaComposite.DstAtop, redCheck},
            {AlphaComposite.getInstance(AlphaComposite.DST_IN, 1.0f), redCheck},
            {AlphaComposite.getInstance(AlphaComposite.DST_IN, 0.5f), redRed128Check},
            {AlphaComposite.getInstance(AlphaComposite.DST_OUT, 1.0f), redBlackCheck},
            {AlphaComposite.getInstance(AlphaComposite.DST_OUT, 0.5f), redRed128Check},
            {AlphaComposite.DstOver, redCheck},
            {AlphaComposite.Src, redBlueCheck},
            {AlphaComposite.SrcAtop, redBlueCheck},
            {AlphaComposite.getInstance(AlphaComposite.SRC_IN, 1.0f), redBlueCheck},
            {AlphaComposite.getInstance(AlphaComposite.SRC_IN, 0.5f), redBlue128Check},
            {AlphaComposite.SrcOut, redBlackCheck},
            {AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f), redBlueCheck},
            {AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f), purple128Check},
            //Uncomment after resolving issues with Xor mode on Metal
            //{AlphaComposite.Xor, redBlackCheck},
            {AlphaComposite.getInstance(AlphaComposite.DST_OUT, 0.0f), redCheck},
            {AlphaComposite.getInstance(AlphaComposite.SRC_IN, 0.0f), redBlackCheck},
            {AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.01f), redCheck},
            {AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.099f), redBlue24Check}
    };

    private static final int TD = 10;
    static WindowAlphaCompositeTest theTest;
    private final Robot robot;
    private JFrame frame;

    private final static int DELAY = 1000;

    public WindowAlphaCompositeTest() {
        try {
            robot = new Robot();
        } catch (AWTException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void performTest(final AlphaComposite ac, Validate validate) {

        runSwing(() -> {
            frame = new JFrame();
            frame.setBounds(100, 100, 100, 150);
            JComponent contentPane = (JComponent) frame.getContentPane();
            JPanel comp = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    renderComposite((Graphics2D) g, ac, 100, 100);
                }
            };
            contentPane.add(comp);
            comp.setBackground(Color.BLACK);
            frame.setVisible(true);
        });

        robot.delay(DELAY);

        for (int px = 10; px <= 90; px += 20) {
            Color c = getTestPixel(px, 90);

            if (!validate.validate(px, 90, c)) {
                throw new RuntimeException("Test failed. Incorrect color " + c +
                        " at (" + px + "," + 90 + ") with composite rule=" + ac.getRule() +
                        " alpha=" + ac.getAlpha());
            }
        }

        runSwing(() -> frame.dispose());

        frame = null;
    }

    public void renderComposite(Graphics2D g, AlphaComposite ac,
                                int w, int h)
    {
        // draw stage
        // outer rect
        g.setComposite(AlphaComposite.SrcOver); // as default
        g.setPaint(Color.red);
        g.fillRect(0, 0, w, h);

        // inner rect
        g.setComposite(ac);
        g.setPaint(Color.blue);
        g.fillRect(w/4, h/4, w/2, h/2);
    }

    private Color getTestPixel(int x, int y) {
        Rectangle bounds = frame.getBounds();
        BufferedImage screenImage = robot.createScreenCapture(bounds);
        int rgb = screenImage.getRGB(x, y);
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return new Color(red, green, blue);
    }

    private static boolean validateColor(Color c, Color expected) {
        return Math.abs(c.getRed() - expected.getRed()) <= TD &&
            Math.abs(c.getGreen() - expected.getGreen()) <= TD &&
            Math.abs(c.getBlue() - expected.getBlue()) <= TD;
    }

    public void dispose() {
        if (frame != null) {
            frame.dispose();
            frame = null;
        }
    }

    private static void runSwing(Runnable r) {
        try {
            SwingUtilities.invokeAndWait(r);
        } catch (InterruptedException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        if (!System.getProperty("os.name").contains("OS X")) {
            System.out.println("This test is for MacOS only. Automatically passed on other platforms.");
            return;
        }

        try {
            for (Object[] alphaComposite : alphaComposites) {
                if (alphaComposite[1] == null) continue;
                runSwing(() -> theTest = new WindowAlphaCompositeTest());
                theTest.performTest((AlphaComposite) alphaComposite[0], (Validate) alphaComposite[1]);
            }
        } finally {
            if (theTest != null) {
                runSwing(() -> theTest.dispose());
            }
        }
    }
}
