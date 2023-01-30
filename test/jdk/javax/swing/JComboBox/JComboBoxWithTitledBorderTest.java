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

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;

import static javax.swing.UIManager.getInstalledLookAndFeels;

/*
 * @test
 * @key headful
 * @bug 8300269
 * @summary This test verifies the issue: Can't see the selected JComboBox
 *          item if it has a titled border.
 * @run main JComboBoxWithTitledBorderTest
 */
public class JComboBoxWithTitledBorderTest {
    private static final String[] comboStrings =
            {"First", "Second", "Third", "Fourth"};
    private static JFrame frame;
    private static JComboBox<String> combo;
    private static Robot robot;

    public static void main(String[] argv) throws Exception {
        robot = new Robot();
        robot.setAutoWaitForIdle(true);
        robot.setAutoDelay(200);
        List<String> lafs = Arrays.stream(getInstalledLookAndFeels())
                .map(LookAndFeelInfo::getClassName)
                .collect(Collectors.toList());
        for (final String laf : lafs) {
            // Skip GTK L&F because pressing ENTER after editing JComboBox
            // doesn't change text and resets to starting text instead.
            if (laf.equals("com.sun.java.swing.plaf.gtk.GTKLookAndFeel")) {
                continue;
            }
            try {
                AtomicBoolean lafSetSuccess = new AtomicBoolean(false);
                System.out.println("Setting LAF: " + laf);
                SwingUtilities.invokeAndWait(() -> {
                    lafSetSuccess.set(setLookAndFeel(laf));
                    if (lafSetSuccess.get()) {
                        createAndShowGUI(laf);
                    }
                });
                if (!lafSetSuccess.get()) {
                    continue;
                }
                robot.waitForIdle();

                mouseClick(combo);

                hitKeys(KeyEvent.VK_RIGHT, KeyEvent.VK_BACK_SPACE,
                        KeyEvent.VK_ENTER);
                String item = (String) combo.getSelectedItem();
                System.out.println("Current item: " + item);
                // Deletes the last character of the combo item and check
                // whether its getting reflected in item. Bug JDK-8300269: It's
                // not getting reflected in case of AquaLookAndFeel.
                if ("Firs".equals(item)) {
                    System.out.println("Test Passed for " + laf);
                } else {
                    captureScreen();
                    throw new RuntimeException("Test Failed for " + laf);
                }
            } finally {
                SwingUtilities.invokeAndWait(
                        JComboBoxWithTitledBorderTest::disposeFrame);
            }
        }
    }

    private static void hitKeys(int... keys) {
        for (int key : keys) {
            robot.keyPress(key);
        }
        for (int i = keys.length - 1; i >= 0; i--) {
            robot.keyRelease(keys[i]);
        }
    }

    private static void mouseClick(JComponent jComponent) throws Exception {
        final AtomicReference<Point> loc = new AtomicReference<>();
        SwingUtilities
                .invokeAndWait(() -> loc.set(jComponent.getLocationOnScreen()));
        final Point location = loc.get();
        robot.mouseMove(location.x + 25, location.y + 5);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    private static void createAndShowGUI(final String laf) {
        frame = new JFrame("JComboBox with Titled Border test");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JPanel panel = new JPanel();
        combo = new JComboBox<>(comboStrings);
        combo.setEditable(true);

        // Create a titled border for the ComboBox with the LAF name as title.
        String[] lafStrings = laf.split("[.]");
        combo.setBorder(BorderFactory.createTitledBorder(
                lafStrings[lafStrings.length - 1]));
        panel.add(combo);
        frame.getContentPane().add(panel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static boolean setLookAndFeel(String lafName) {
        try {
            UIManager.setLookAndFeel(lafName);
        } catch (UnsupportedLookAndFeelException ignored) {
            System.out.println("Ignoring Unsupported LAF: " + lafName);
            return false;
        } catch (ClassNotFoundException | InstantiationException
                | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    private static void disposeFrame() {
        if (frame != null) {
            frame.dispose();
            frame = null;
        }
    }

    private static void captureScreen() {
        try {
            final Rectangle screenBounds = new Rectangle(
                    Toolkit.getDefaultToolkit().getScreenSize());
            ImageIO.write(robot.createScreenCapture(screenBounds),
                    "png", new File("failScreen.png"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}