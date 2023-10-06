/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

import java.util.function.Predicate;

/*
 * @test
 * @bug 4365952
 * @key headful
 * @summary Test to check is File chooser can be Disabled
 * @run main FileChooserDisableTest
 */
public class FileChooserDisableTest {
    static Robot robot;
    static JFrame frame;
    static JFileChooser jfc;
    static volatile Point movePoint;

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        try {

            SwingUtilities.invokeAndWait(() -> {
                initialize();
            });
            String defaultDirectory = jfc.getCurrentDirectory().toString();
            robot.delay(1000);
            robot.waitForIdle();
            final AbstractButton[] desktopBtn = new AbstractButton[1];
            SwingUtilities.invokeAndWait(() -> {
                desktopBtn[0] = clickDetails();
                movePoint = desktopBtn[0].getLocationOnScreen();
            });
            Dimension btnSize = desktopBtn[0].getSize();
            robot.mouseMove(movePoint.x + btnSize.width / 2, movePoint.y + btnSize.height / 2);
            robot.delay(2000);
            robot.waitForIdle();
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(2000);
            robot.waitForIdle();
            String currentDirectory = jfc.getCurrentDirectory().toString();
            if (!currentDirectory.equals(defaultDirectory)) {
                throw new RuntimeException("File chooser disable failed");
            }
            System.out.println("Test Pass");

        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static AbstractButton clickDetails() {
        AbstractButton details = findDetailsButton(jfc);
        if (details == null) {
            throw new Error("Didn't find 'Home' button in JFileChooser");
        }
        return details;
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

    private static AbstractButton findDetailsButton(final Container container) {
        Component result = findComponent(container,
                c -> c instanceof JButton button
                        && "Home".equals(button.getToolTipText()));
        return (AbstractButton) result;
    }

    static void initialize() {
        frame = new JFrame("JFileChooser Disable test");
        jfc = new JFileChooser();
        jfc.setEnabled(false);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.add(jfc, BorderLayout.CENTER);
        frame.pack();
        frame.setVisible(true);
    }
}
