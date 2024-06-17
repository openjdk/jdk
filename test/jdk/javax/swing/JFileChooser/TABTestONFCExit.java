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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;

import java.util.function.Predicate;

/*
 * @test
 * @bug 6967482
 * @key headful
 * @summary Test to check if TAB is working on JTable after JFileChooser is
 *          closed
 * @run main TABTestONFCExit
 */

public class TABTestONFCExit {
    private static JTable table;
    private static JFileChooser fc;
    private static JFrame frame;
    private static Robot robot;
    private static volatile Point loc;
    private static volatile Rectangle rect;
    private static volatile int selectedColumnBeforeTabPress;
    private static volatile int selectedColumnAfterTabPress;

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        robot.setAutoDelay(50);
        UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        try {
            SwingUtilities.invokeAndWait(TABTestONFCExit::initialize);
            robot.waitForIdle();
            robot.delay(100);

            SwingUtilities.invokeAndWait(TABTestONFCExit::clickDetails);
            robot.waitForIdle();
            robot.delay(100);

            SwingUtilities.invokeAndWait(() -> {
                loc = table.getLocationOnScreen();
                rect = table.getCellRect(0, 0, true);
            });

            onClick(loc, rect);

            SwingUtilities.invokeAndWait(() ->
                    selectedColumnBeforeTabPress = table.getSelectedColumn());

            robot.keyPress(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_TAB);
            robot.waitForIdle();
            robot.delay(100);

            SwingUtilities.invokeAndWait(() ->
                    selectedColumnAfterTabPress = table.getSelectedColumn());
            robot.waitForIdle();
            robot.delay(100);

            if (selectedColumnAfterTabPress == selectedColumnBeforeTabPress) {
                throw new RuntimeException("TAB failed to move cell!");
            }
            System.out.println("Test Passed" );

        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void onClick(Point loc, Rectangle cellRect) {
        robot.mouseMove(loc.x + cellRect.x + cellRect.width / 2,
                loc.y + cellRect.y + cellRect.height / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        robot.delay(100);
    }

    private static void initialize() {
        frame = new JFrame("Tab Test");
        fc = new JFileChooser();
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.add(getJTable(), BorderLayout.NORTH);
        frame.add(fc, BorderLayout.SOUTH);
        frame.pack();
        frame.setVisible(true);
    }

    private static JTable getJTable() {
        if (table == null) {
            table = new JTable();
            table.setModel(new DefaultTableModel(5, 5));
        }
        return table;
    }
    private static void clickDetails() {
        AbstractButton details = findDetailsButton(fc);
        if (details == null) {
            throw new Error("Couldn't find 'Details' button in JFileChooser");
        }
        details.doClick();
    }

    private static AbstractButton findDetailsButton(final Container container) {
        Component result = findComponent(container,
                c -> c instanceof JToggleButton button
                        && "Details".equals(button.getToolTipText()));
        return (AbstractButton) result;
    }

    private static Component findComponent(final Container container,
                                           final Predicate<Component> predicate) {
        for (Component child : container.getComponents()) {
            if (predicate.test(child)) {
                return child;
            }
            if (child instanceof Container cont && cont.getComponentCount() > 0) {
                Component result = findComponent(cont, predicate);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
}
