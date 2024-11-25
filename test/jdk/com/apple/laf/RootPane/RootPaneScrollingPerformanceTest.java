/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Scrolling tall panel should not take several times longer using default AquaButtonUI.
 * @requires (os.family == "mac")
 * @run main RootPaneScrollingPerformanceTest
 */

import javax.swing.*;
import javax.swing.event.AncestorListener;
import java.awt.*;
import java.awt.event.*;

/**
 * This simulates a mouse scroll wheel movement over a scrollpane with 1,000 checkboxes.
 * <p>
 * This is a direct adaption of the source code attached to 8344697.
 */
public class RootPaneScrollingPerformanceTest extends JPanel {
    public static void main(String[] args) throws Exception {
        if (!System.getProperty("os.name").contains("OS X")) {
            System.out.println("This test is for MacOS only. Automatically passed on other platforms.");
            return;
        }

        RootPaneScrollingPerformanceTest test = new RootPaneScrollingPerformanceTest();
        Robot robot = new Robot();
        SwingUtilities.invokeAndWait(() -> {
            JFrame f = new JFrame();
            f.getContentPane().add(test);
            f.pack();
            f.setVisible(true);
        });

        // let UI settle down a little:
        robot.delay(250);

        SwingUtilities.invokeAndWait(() -> {
            test.simulateSwipeButton.doClick();
        });
        while (test.elapsedTestTime == null) {
            robot.delay(100);
        }
        long t1 = test.elapsedTestTime;

        test.elapsedTestTime = null;
        SwingUtilities.invokeAndWait(() -> {
            test.includeAncestorListenerButton.doClick();
            test.simulateSwipeButton.doClick();
        });

        while (test.elapsedTestTime == null) {
            robot.delay(100);
        }
        long t2 = test.elapsedTestTime;

        System.out.println("The time it took by default was: " + t1);
        System.out.println("The time it took when suppressing AncestorListeners was: " + t2);
        System.out.println("These two times should be nearly equal.");

        // allow some random fluctuation, but when they're wildly different: this test fails
        float f = ((float)t2)/((float)t1);
        if (f < .5)
            throw new RuntimeException("This test failed.");
    }

    static final int NUMBER_OF_CHECKBOXES = 5_000;

    private static final String PROPERTY_ORIGINAL_ANCESTOR_LISTENERS = "originalAncestorListeners";

    JScrollPane scrollPane;
    JCheckBox includeAncestorListenerButton = new JCheckBox("Include AncestorListeners", true);
    JPanel scrollPaneContent = new JPanel(new GridLayout(NUMBER_OF_CHECKBOXES, 1));

    record ScrollMovement(int wheelRotation, double preciseWheelRotation, long when) {};

    JButton simulateSwipeButton = new JButton("Simulate Swipe");

    /**
     * These describe the MouseWheelEvents I observed when I swiped down over the scrollpane using
     * my MacBook's touchpad.
     */
    ScrollMovement[] swipeMovements = new ScrollMovement[] {
            new ScrollMovement(0, 0.4, -1),
            new ScrollMovement(1, 0.5, 0),
            new ScrollMovement(6, 6.5, 15),
            new ScrollMovement(0, 0.1, 15),
            new ScrollMovement(12, 11.600000000000001, 30),
            new ScrollMovement(12, 12.3, 49),
            new ScrollMovement(13, 12.3, 63),
            new ScrollMovement(11, 11.8, 82),
            new ScrollMovement(12, 11.3, 97),
            new ScrollMovement(10, 10.700000000000001, 113),
            new ScrollMovement(11, 10.3, 130),
            new ScrollMovement(9, 9.700000000000001, 147),
            new ScrollMovement(10, 9.3, 163),
            new ScrollMovement(9, 8.9, 182),
            new ScrollMovement(8, 8.4, 197),
            new ScrollMovement(8, 7.9, 214),
            new ScrollMovement(7, 7.4, 233),
            new ScrollMovement(7, 7.0, 249),
            new ScrollMovement(7, 6.4, 265),
            new ScrollMovement(6, 6.0, 281),
            new ScrollMovement(5, 5.7, 298),
            new ScrollMovement(6, 5.300000000000001, 315),
            new ScrollMovement(5, 4.9, 331),
            new ScrollMovement(4, 4.5, 348),
            new ScrollMovement(4, 4.1000000000000005, 365),
            new ScrollMovement(4, 3.9000000000000004, 382),
            new ScrollMovement(4, 3.5, 399),
            new ScrollMovement(3, 3.3000000000000003, 415),
            new ScrollMovement(3, 3.0, 431),
            new ScrollMovement(3, 2.8000000000000003, 449),
            new ScrollMovement(2, 2.6, 465),
            new ScrollMovement(3, 2.2, 481),
            new ScrollMovement(2, 2.1, 498),
            new ScrollMovement(2, 1.9000000000000001, 515),
            new ScrollMovement(1, 1.7000000000000002, 532),
            new ScrollMovement(2, 1.6, 548),
            new ScrollMovement(1, 1.4000000000000001, 565),
            new ScrollMovement(2, 1.3, 582),
            new ScrollMovement(1, 1.2000000000000002, 599),
            new ScrollMovement(1, 1.1, 615),
            new ScrollMovement(1, 1.0, 632),
            new ScrollMovement(1, 0.9, 649),
            new ScrollMovement(1, 0.9, 666),
            new ScrollMovement(0, 0.8, 682),
            new ScrollMovement(1, 0.7000000000000001, 699),
            new ScrollMovement(1, 0.7000000000000001, 716),
            new ScrollMovement(0, 0.6000000000000001, 733),
            new ScrollMovement(1, 0.6000000000000001, 749),
            new ScrollMovement(1, 0.5, 766),
            new ScrollMovement(0, 0.5, 783),
            new ScrollMovement(1, 0.5, 800),
            new ScrollMovement(0, 0.4, 817),
            new ScrollMovement(0, 0.4, 834),
            new ScrollMovement(1, 0.4, 849),
            new ScrollMovement(0, 0.30000000000000004, 865),
            new ScrollMovement(0, 0.30000000000000004, 883),
            new ScrollMovement(1, 0.30000000000000004, 899),
            new ScrollMovement(0, 0.30000000000000004, 916),
            new ScrollMovement(0, 0.2, 933),
            new ScrollMovement(0, 0.2, 949),
            new ScrollMovement(1, 0.2, 966),
            new ScrollMovement(0, 0.2, 983),
            new ScrollMovement(0, 0.1, 998),
            new ScrollMovement(0, 0.1, 1017),
            new ScrollMovement(0, 0.1, 1033),
            new ScrollMovement(0, 0.1, 1049),
            new ScrollMovement(0, 0.1, 1067),
            new ScrollMovement(0, 0.1, 1100),
            new ScrollMovement(0, 0.1, 1117),
            new ScrollMovement(1, 0.1, 1134)
    };

    Long elapsedTestTime = null;

    public RootPaneScrollingPerformanceTest() {
        simulateSwipeButton.setToolTipText("Simulate scrolling as if swiping two fingers across a touchpad.");
        simulateSwipeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                long startTime = System.currentTimeMillis();
                for (ScrollMovement m : swipeMovements) {
                    MouseWheelEvent event = new MouseWheelEvent(
                            scrollPane,
                            MouseWheelEvent.MOUSE_WHEEL,
                            m.when + startTime, 0,
                            50, 50, 50, 50, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1,
                            m.wheelRotation,
                            m.preciseWheelRotation);
                    Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(event);
                }

                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                elapsedTestTime = (System.currentTimeMillis() - startTime);
                                System.out.println("Swipe simulation took " + elapsedTestTime + " ms");
                            }
                        });
                    }
                });
            }
        });

        scrollPane = new JScrollPane(scrollPaneContent);
        for (int a = 1; a <= NUMBER_OF_CHECKBOXES; a++) {
            JCheckBox checkbox = new JCheckBox("Checkbox " + a);
            scrollPaneContent.add(checkbox);
            checkbox.putClientProperty(PROPERTY_ORIGINAL_ANCESTOR_LISTENERS, checkbox.getAncestorListeners());
        }
        scrollPane.setPreferredSize(new Dimension(800, 400));

        setLayout(new BorderLayout());
        add(scrollPane, BorderLayout.CENTER);
        add(simulateSwipeButton, BorderLayout.NORTH);
        add(includeAncestorListenerButton, BorderLayout.SOUTH);

        includeAncestorListenerButton.setToolTipText("Toggling off AncestorListeners resolves the performance complaint demonstrated here.");
        includeAncestorListenerButton.addActionListener(e -> {
            for (Component c : scrollPaneContent.getComponents()) {
                JComponent jc = (JComponent) c;
                AncestorListener[] listeners = (AncestorListener[]) jc.getClientProperty(PROPERTY_ORIGINAL_ANCESTOR_LISTENERS);
                for (AncestorListener listener : listeners) {
                    if (includeAncestorListenerButton.isSelected()) {
                        jc.addAncestorListener(listener);
                    } else {
                        jc.removeAncestorListener(listener);
                    }
                }
            }
        });
    }
}