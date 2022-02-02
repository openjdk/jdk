/*
 * Copyright 2022 JetBrains s.r.o.
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
 * @bug 8015854
 * @summary JButton text set as html image had additional unwanted padding
 * @run main HtmlButtonImageTest
 */

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.Color;
import java.awt.Robot;
import java.net.URL;
import java.nio.file.Path;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.SwingConstants;
import javax.swing.UnsupportedLookAndFeelException;

public final class HtmlButtonImageTest {
    private static JFrame frame;
    private static Point point;
    private static URL urlImage;
    private static JButton button;

    public static final int BUTTON_HEIGHT = 37;
    public static final int BUTTON_WIDTH = 37;
    public static final int SQUARE_HEIGHT = 19;
    public static final int SQUARE_WIDTH = 19;
    private static final int PIXEL_BUFFER = 3;

    public static void main(String[] args) throws Exception {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Unsupported LookAndFeel: " + e);
        }

        Robot robot = new Robot();
        robot.setAutoDelay(2000);
        robot.setAutoWaitForIdle(true);

        SwingUtilities.invokeAndWait(() -> {
            createAndShowGUI();
        });

        // retrieve color of pixels at each edge of square image by starting at the center of the button
        robot.mouseMove(frame.getLocationOnScreen().x, frame.getLocationOnScreen().y);
        robot.mouseMove(button.getLocationOnScreen().x, button.getLocationOnScreen().y);

        setupCenterCoord();
        robot.mouseMove(point.x, point.y);

        // store each pixel color on the edge of each side of the red square
        robot.mouseMove(point.x - (SQUARE_WIDTH/2) + PIXEL_BUFFER, point.y);
        Color leftClr = robot.getPixelColor(point.x - (SQUARE_WIDTH/2) + PIXEL_BUFFER, point.y);
        robot.mouseMove(point.x + (SQUARE_WIDTH/2) - PIXEL_BUFFER, point.y);
        Color rightClr = robot.getPixelColor(point.x + (SQUARE_WIDTH/2) - PIXEL_BUFFER, point.y);
        robot.mouseMove(point.x, point.y - (SQUARE_HEIGHT/2) + PIXEL_BUFFER);
        Color topClr = robot.getPixelColor(point.x, point.y - (SQUARE_HEIGHT/2) + PIXEL_BUFFER);
        robot.mouseMove(point.x, point.y + (SQUARE_HEIGHT/2) - PIXEL_BUFFER);
        Color botClr = robot.getPixelColor(point.x, point.y + (SQUARE_HEIGHT/2) - PIXEL_BUFFER);

        // check if all colors at points are red
        if(!checkRedness(leftClr) || !checkRedness(rightClr)
                || !checkRedness(topClr) || !checkRedness(botClr)) {
            throw new RuntimeException("HTML image not centered in button" + leftClr + rightClr + topClr + botClr);
        }

        // close frame when complete
        SwingUtilities.invokeAndWait(() -> {
            frame.dispose();
        });

    }

    private static void createAndShowGUI()
    {
        frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new FlowLayout());

        // create JButton of size 37x37 text set to a 19x19 image of a red square loaded through html tags
        button = new JButton();
        button.setFocusPainted(false);
        button.setPreferredSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));

        // create path to button text's image to find valid path when using jtreg as well
        Path srcDir = Path.of(System.getProperty("test.src", "."));
        button.setText("<html><img src='" + srcDir.resolve("red_square.png").toUri() + "'></html>");

        frame.add(button);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void setupCenterCoord() {
        // adjust coordinates to be the center of the button
        point = button.getLocationOnScreen();
        point.x += BUTTON_WIDTH / 2;
        point.y += BUTTON_HEIGHT / 2;
    }

    private static boolean checkRedness(Color c) {
        // checks for redness since anti-aliasing causes edges to be not exactly 255,0,0 rgb values
        if(c.getRed() > 250 && c.getBlue() < 10 && c.getGreen() < 10) {
            return true;
        }
        return false;
    }
}