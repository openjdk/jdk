/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.JScrollBar;
import javax.swing.JPanel;
import javax.swing.JFrame;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.Box;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/*
 * @test
 * @bug 8190264
 * @summary JScrollBar ignores its border when using macOS Mac OS X Aqua look and feel
 * @run main ScrollBarBorderTest
 */
public class ScrollBarBorderTest {

    // On macOS 10.12.6 using the Mac look and feel (com.apple.laf.AquaLookAndFeel)
    // the scroll bar ignores the custom border and allows the scroll thumb to move
    // beneath the border. Run with:
    // java ScrollBarBorderTest

    // If run using any other look and feel (e.g. Metal) then the right side of
    // the scroll bar stops at the border as expected. Run with:
    // java -Dswing.defaultlaf=javax.swing.plaf.metal.MetalLookAndFeel ScrollBarBorderTest

    // Java version: 1.8.0_151

    private static JScrollBar scrollBar;
    private static JPanel panel;
    private static JFrame frame;
    private static Robot robot;
    private int thumbPressed = 0;

    public void createAndShowGUI() {
        // create scroll bar
        scrollBar = new JScrollBar(Scrollbar.HORIZONTAL);
        scrollBar.setBorder(new CustomBorder());
        scrollBar.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) throws RuntimeException {
                thumbPressed++;
                if(thumbPressed > 1) {
                    throw new RuntimeException("Thumb was able to move into the border.");
                }
            }
        });

        // create panel
        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        panel.add(new JLabel(UIManager.getLookAndFeel().toString()));
        panel.add(Box.createVerticalStrut(20));
        panel.add(scrollBar);

        // create frame
        frame = new JFrame("ScrollBarBorderTest");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().add(panel);
        frame.pack();
        frame.setVisible(true);
    }


    private static void cleanUp() {
        frame.dispose();
    }

    public static void main(String[] args) {
        ScrollBarBorderTest borderTest = new ScrollBarBorderTest();
        borderTest.createAndShowGUI();

        try {
            robot = new Robot();
        } catch (Exception e) {
            throw new RuntimeException("Unable to create Robot.");
        }
        robot.setAutoDelay(50);
        robot.waitForIdle();

        Point p = frame.getLocationOnScreen();

        robot.mouseMove(p.x + 40, p.y + 95);
        robot.mousePress(MouseEvent.getMaskForButton(MouseEvent.BUTTON1));
        robot.waitForIdle();

        robot.mouseMove(p.x + 480, p.y + 95);
        robot.mouseRelease(MouseEvent.getMaskForButton(MouseEvent.BUTTON1));
        robot.waitForIdle();

        robot.mousePress(MouseEvent.getMaskForButton(MouseEvent.BUTTON1));
        robot.waitForIdle();

        robot.mouseRelease(MouseEvent.getMaskForButton(MouseEvent.BUTTON1));
        robot.waitForIdle();


        cleanUp();
    }


    // custom border
    private static class CustomBorder implements Border {
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            g.setColor(new Color(255, 0, 0, 100));
            g.fillRect(width - 150, y, width, height);
        }

        public Insets getBorderInsets(Component c) {
            return new Insets(0, 0, 0, 150);
        }

        public boolean isBorderOpaque() {
            return true;
        }
    }

}