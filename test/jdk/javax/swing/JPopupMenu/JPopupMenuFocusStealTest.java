/*
 * Copyright (c) 2002, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;

import static javax.swing.UIManager.getInstalledLookAndFeels;

/*
 * @test
 * @key headful
 * @bug 4632782
 * @summary This test checks CCC #4632782, which verifies that showing a
 *          JPopupMenu shouldn't steal the focus out of current focused component.
 * @run main JPopupMenuFocusStealTest
 */
public class JPopupMenuFocusStealTest {
    private static JPopupMenu popupMenu;
    private static JComboBox comboBox;
    private static JFrame frame;
    private static Robot robot;
    private static JLabel label;

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        robot.setAutoDelay(200);
        robot.setAutoWaitForIdle(true);

        List<String> lafs = Arrays.stream(getInstalledLookAndFeels())
                                  .map(LookAndFeelInfo::getClassName)
                                  .collect(Collectors.toList());
        for (final String laf : lafs) {
                AtomicBoolean lafSetSuccess = new AtomicBoolean(false);
                SwingUtilities.invokeAndWait(() -> {
                    lafSetSuccess.set(setLookAndFeel(laf));
                    if (lafSetSuccess.get()) {
                        createUI();
                    }
                });
                if (!lafSetSuccess.get()) {
                    continue;
                }
                robot.waitForIdle();

                // Bring the mouse pointer to label
                mouseClick(label);
                // Get the Popup menu by Mouse Button 3 click
                robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
                hitKeys(KeyEvent.VK_DOWN, KeyEvent.VK_DOWN, KeyEvent.VK_ENTER);
                final AtomicBoolean isFocusOwner = new AtomicBoolean(false);
                SwingUtilities.invokeAndWait(
                        () -> isFocusOwner.set(comboBox.isFocusOwner()));
                SwingUtilities
                        .invokeAndWait(JPopupMenuFocusStealTest::disposeFrame);
                if (isFocusOwner.get()) {
                    System.out.println("Test Passed for " + laf);
                } else {
                    throw new RuntimeException("Test Failed for " + laf);
                }
        }
    }

    private static void createUI() {
        frame = new JFrame();
        frame.setTitle("Popup Menu Application");
        JPanel topPanel = new JPanel(new BorderLayout());
        Object[] array = {"Item1", "Item2", "Item3"};
        comboBox = new JComboBox(array);
        label = new JLabel("Check focus transfer from Combo to Popupmenu");
        topPanel.add(comboBox, BorderLayout.NORTH);
        topPanel.add(label, BorderLayout.CENTER);
        frame.getContentPane().add(topPanel);

        // Create some menu items for the popup
        popupMenu = new JPopupMenu("Menu");
        popupMenu.add(new JMenuItem("New"));
        popupMenu.add(new JMenuItem("Open..."));
        popupMenu.add(new JMenuItem("Save"));
        popupMenu.add(new JMenuItem("Save As..."));
        popupMenu.add(new JMenuItem("Exit"));

        topPanel.addMouseListener(new PopupMenuEventListener());
        popupMenu.setFocusable(false);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.pack();
        frame.setVisible(true);
    }

    private static void mouseClick(JComponent jComponent) throws Exception {
        final AtomicReference<Point> loc = new AtomicReference<>();
        SwingUtilities
                .invokeAndWait(() -> loc.set(jComponent.getLocationOnScreen()));
        final Point location = loc.get();
        robot.mouseMove(location.x + 15, location.y + 5);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    private static void hitKeys(int... keys) {
        for (int key : keys) {
            robot.keyPress(key);
        }
        for (int i = keys.length - 1; i >= 0; i--) {
            robot.keyRelease(keys[i]);
        }
    }

    private static boolean setLookAndFeel(String lafName) {
        try {
            UIManager.setLookAndFeel(lafName);
        } catch (UnsupportedLookAndFeelException ignored) {
            System.out.println("Ignoring Unsupported L&F: " + lafName);
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

    private static class PopupMenuEventListener extends MouseAdapter {
        public void mousePressed(MouseEvent me) {
            if (me.isPopupTrigger()) {
                popupMenu.show(me.getComponent(), me.getX(), me.getY());
            }
        }

    }

}
