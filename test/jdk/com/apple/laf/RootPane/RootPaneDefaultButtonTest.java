/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8344697
 * @summary Default button in AquaRootPaneUI should paint special background color
 * @requires (os.family == "mac")
 * @run main RootPaneDefaultButtonTest
 */

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

/**
 * This presents two dialogs, each with two possible default buttons. The
 * background color of the default button should change based on which radio
 * button is selected.
 * <p>
 * Note we've never expected this test to fail. This test was introduced
 * because the resolution to JDK-8344697 involved removing code, and we wanted
 * to double-check that the removed code didn't negatively affect how default
 * buttons are repainted.
 */
public class RootPaneDefaultButtonTest extends JDialog {

    record ButtonRenderingExpectation(JButton button,
                                      boolean appearAsDefault) {}

    public static void main(String[] args) throws Exception {
        if (!System.getProperty("os.name").contains("OS X")) {
            System.out.println("This test is for MacOS only.");
            return;
        }

        RootPaneDefaultButtonTest window1 = new RootPaneDefaultButtonTest();
        RootPaneDefaultButtonTest window2 = new RootPaneDefaultButtonTest();

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                Rectangle r1 = new Rectangle(0, 20,
                        window1.getWidth(), window1.getHeight());
                window1.setBounds(r1);

                Rectangle r2 = new Rectangle((int) (r1.getMaxX() + 10), 20,
                        window2.getWidth(), window2.getHeight());
                window2.setBounds(r2);

                window1.setVisible(true);
                window2.setVisible(true);
            }
        });

        Robot robot = new Robot();

        test(robot, window1.radioButton1,
                new ButtonRenderingExpectation(window1.button1, true),
                new ButtonRenderingExpectation(window1.button2, false),
                new ButtonRenderingExpectation(window2.button1, false),
                new ButtonRenderingExpectation(window2.button2, false));

        test(robot, window1.radioButton2,
                new ButtonRenderingExpectation(window1.button1, false),
                new ButtonRenderingExpectation(window1.button2, true),
                new ButtonRenderingExpectation(window2.button1, false),
                new ButtonRenderingExpectation(window2.button2, false));

        test(robot, window1.radioButton3,
                new ButtonRenderingExpectation(window1.button1, false),
                new ButtonRenderingExpectation(window1.button2, false),
                new ButtonRenderingExpectation(window2.button1, false),
                new ButtonRenderingExpectation(window2.button2, false));

        test(robot, window2.radioButton1,
                new ButtonRenderingExpectation(window1.button1, false),
                new ButtonRenderingExpectation(window1.button2, false),
                new ButtonRenderingExpectation(window2.button1, true),
                new ButtonRenderingExpectation(window2.button2, false));

        test(robot, window2.radioButton2,
                new ButtonRenderingExpectation(window1.button1, false),
                new ButtonRenderingExpectation(window1.button2, false),
                new ButtonRenderingExpectation(window2.button1, false),
                new ButtonRenderingExpectation(window2.button2, true));

        test(robot, window2.radioButton3,
                new ButtonRenderingExpectation(window1.button1, false),
                new ButtonRenderingExpectation(window1.button2, false),
                new ButtonRenderingExpectation(window2.button1, false),
                new ButtonRenderingExpectation(window2.button2, false));

        System.out.println("Test passed successfully");
    }

    private static void test(Robot robot, AbstractButton buttonToClick,
                             ButtonRenderingExpectation... expectations)
            throws Exception {
        robot.delay(100);

        Point mouseLoc = buttonToClick.getLocationOnScreen();
        robot.mouseMove(mouseLoc.x + buttonToClick.getSize().width / 2,
                mouseLoc.y + buttonToClick.getSize().height / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(20);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        robot.delay(100);

        // the colors may change depending on your system's appearance.
        // Depending on how you've configured "Appearance" in the
        // System Settings app: the default button may be blue (the default),
        // red, purple, etc. So instead of checking for a specific color: we'll
        // make sure 3-4 are the same color, and one is significantly
        // different.
        Color defaultColor = null;
        Color nonDefaultColor = null;

        for (ButtonRenderingExpectation expectation : expectations) {
            int x = expectation.button.getLocationOnScreen().x + 20;
            int y = expectation.button.getLocationOnScreen().y + 10;

            // this mouseMove is optional, but it helps debug this test to see
            // where we're sampling the pixel color from:
            robot.mouseMove(x, y);

            Color c = robot.getPixelColor(x, y);
            if (expectation.appearAsDefault) {
                if (defaultColor == null) {
                    defaultColor = c;
                } else {
                    throw new IllegalStateException(
                            "there should only be at most 1 default button");
                }
            } else {
                if (nonDefaultColor == null) {
                    nonDefaultColor = c;
                } else if (!isSimilar(nonDefaultColor, c)) {
                    throw new IllegalStateException(
                            "these two colors should match: " + c + ", " +
                                    nonDefaultColor);
                }
            }
        }

        if (defaultColor != null && isSimilar(defaultColor, nonDefaultColor)) {
            throw new IllegalStateException(
                    "The default button and non-default buttons should " +
                            "look different: " + defaultColor + " matches " +
                            nonDefaultColor);
        }
    }

    private static boolean isSimilar(Color c1, Color c2) {
        if (Math.abs(c1.getRed() - c2.getRed()) > 15) {
            return false;
        }
        if (Math.abs(c1.getGreen() - c2.getGreen()) > 15) {
            return false;
        }
        if (Math.abs(c1.getBlue() - c2.getBlue()) > 15) {
            return false;
        }
        return true;
    }

    JRadioButton radioButton1 = new JRadioButton(
            "\"Button 1\" is the default button");
    JRadioButton radioButton2 = new JRadioButton(
            "\"Button 2\" is the default button");
    JRadioButton radioButton3 = new JRadioButton("No default button");

    JButton button1 = new JButton("Button 1");
    JButton button2 = new JButton("Button 2");

    public RootPaneDefaultButtonTest() {
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(createRadioButtonPanel(), BorderLayout.NORTH);
        getContentPane().add(createPushButtonRow(), BorderLayout.SOUTH);
        pack();

        radioButton1.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                getRootPane().setDefaultButton(button1);
            }
        });

        radioButton2.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                getRootPane().setDefaultButton(button2);
            }
        });

        radioButton3.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                getRootPane().setDefaultButton(null);
            }
        });

        ButtonGroup g = new ButtonGroup();
        g.add(radioButton1);
        g.add(radioButton2);
        g.add(radioButton3);
        radioButton1.doClick();
    }

    private JPanel createPushButtonRow() {
        JPanel p = new JPanel(new GridLayout(1, 2));
        p.add(button1);
        p.add(button2);
        p.setBorder(new EmptyBorder(5,5,5,5));
        return p;
    }

    private JPanel createRadioButtonPanel() {
        JPanel p = new JPanel(new GridLayout(3, 1));
        p.add(radioButton1);
        p.add(radioButton2);
        p.add(radioButton3);
        p.setBorder(new EmptyBorder(5,5,5,5));
        return p;
    }
}
