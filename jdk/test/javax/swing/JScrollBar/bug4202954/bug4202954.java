/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
/* @test
   @bug 4202954
   @library ../../regtesthelpers
   @build Util
   @author Michael C. Albers
   @run main bug4202954
*/

import java.awt.*;
import java.awt.event.InputEvent;
import javax.swing.*;
import sun.awt.*;

public class bug4202954 {
    static JScrollPane buttonScrollPane;
    private static final SunToolkit toolkit = (SunToolkit) Toolkit.getDefaultToolkit();
    static Robot robot;
    public static void main(String[] args) throws Exception {
        if (OSInfo.getOSType() == OSInfo.OSType.MACOSX) {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        }

        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                createAndShowGUI();
            }
        });
        Point centerOfScrollPane = Util.getCenterPoint(buttonScrollPane);
        JButton rightScrollButton = findJButton(buttonScrollPane.getHorizontalScrollBar(), centerOfScrollPane.x, centerOfScrollPane.y);
        JButton bottomScrollButton = findJButton(buttonScrollPane.getVerticalScrollBar(), centerOfScrollPane.x, centerOfScrollPane.y);

        if (rightScrollButton == null || bottomScrollButton == null) {
            String errMessage = "Test can't be executed: ";
            errMessage = errMessage + rightScrollButton == null ? "can't find right button for horizontal scroll bar; " : ""
                    + bottomScrollButton == null ? "can't find bottom scroll button for vertical scroll bar" : "";
            throw new RuntimeException(errMessage);
        }

        robot = new Robot();
        robot.setAutoDelay(50);

        // test right, left and middle mouse buttons for horizontal scroll bar
        if (!doTest(rightScrollButton, InputEvent.BUTTON1_DOWN_MASK, true)) {
            throw new RuntimeException("Test failed: right arrow button didn't respond on left mouse button.");
        }
        if (!doTest(rightScrollButton, InputEvent.BUTTON2_DOWN_MASK, false)) {
            throw new RuntimeException("Test failed: right arrow button respond on right mouse button.");
        }
        if (!doTest(rightScrollButton, InputEvent.BUTTON3_DOWN_MASK, false)) {
            throw new RuntimeException("Test failed: right arrow button respond on middle mouse button.");
        }

        // test right, left and middle mouse buttons for vertical scroll bar
        if (!doTest(bottomScrollButton, InputEvent.BUTTON1_DOWN_MASK, true)) {
            throw new RuntimeException("Test failed: bottom arrow button didn't respond on left mouse button.");
        }
        if (!doTest(bottomScrollButton, InputEvent.BUTTON2_DOWN_MASK, false)) {
            throw new RuntimeException("Test failed: bottom arrow button respond on right mouse button.");
        }
        if (!doTest(bottomScrollButton, InputEvent.BUTTON3_DOWN_MASK, false)) {
            throw new RuntimeException("Test failed: bottom arrow button respond on middle mouse button.");
        }
    }
    public static void createAndShowGUI() {
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(5,5, 15,15));
        int buttonCount = 1;
        while (buttonCount <= 25) {
            buttonPanel.add(new JButton("Button #"+buttonCount));
            buttonCount++;
        }
        buttonScrollPane = new JScrollPane();
        buttonScrollPane.setViewportView(buttonPanel);

        JFrame testFrame = new JFrame("bug4202954");
        testFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        testFrame.setLayout(new BorderLayout());
        testFrame.add(BorderLayout.CENTER, buttonScrollPane);
        testFrame.setSize(450, 100);
        testFrame.setVisible(true);
    }
    public static JButton findJButton(final JScrollBar scrollBar, final int minX, final int minY) throws Exception {
        JButton button = Util.invokeOnEDT(new java.util.concurrent.Callable<JButton>() {
            @Override
            public JButton call() throws Exception {
                for (Component c: scrollBar.getComponents()) {
                    if (c instanceof JButton) {
                        Point p = c.getLocationOnScreen();
                        if (p.x > minX && p.y > minY) {
                            return (JButton) c;
                        }
                    }
                }
                return null;
            }
        });
        return button;
    }
    public static void clickMouseOnComponent(Component c, int buttons) throws Exception {
        Point p = Util.getCenterPoint(c);
        robot.mouseMove(p.x, p.y);
        robot.mousePress(buttons);
        robot.mouseRelease(buttons);
    }
    public static boolean doTest(JButton scrollButton, int buttons, boolean expectScroll) throws Exception {
        java.util.concurrent.Callable<Integer> horizontalValue = new java.util.concurrent.Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return buttonScrollPane.getHorizontalScrollBar().getValue();
            }
        };
        java.util.concurrent.Callable<Integer> verticalValue = new java.util.concurrent.Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return buttonScrollPane.getVerticalScrollBar().getValue();
            }
        };
        Integer oldHValue = Util.invokeOnEDT(horizontalValue);
        Integer oldVValue = Util.invokeOnEDT(verticalValue);

        clickMouseOnComponent(scrollButton, buttons);
        toolkit.realSync();

        int newHValue = Util.invokeOnEDT(horizontalValue);
        int newVValue = Util.invokeOnEDT(verticalValue);

        return (oldHValue != newHValue || oldVValue != newVValue) == expectScroll;
    }
}
