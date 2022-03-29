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

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import static javax.swing.UIManager.getInstalledLookAndFeels;

/*
 * @test
 * @key headful
 * @bug 4618767
 * @summary This test confirms that typing a letter while a JTree has focus now makes the selection
 *          not jump to the item whose text starts with that letter if that typed letter is accompanied
 *          by modifier keys such as ALT or CTRL(eg: ALT+F).
 * @run main JTreeSelectedElementTest
 */
public class JTreeSelectedElementTest {

    private static final int FILE_MENU = KeyEvent.VK_F;
    private static JFrame frame;
    private static JTree tree;
    private static Robot robot;
    private static CountDownLatch menuSelectedEventLatch;

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        robot.setAutoWaitForIdle(true);
        robot.setAutoDelay(200);

        final boolean isMac = System.getProperty("os.name")
                                    .toLowerCase()
                                    .contains("os x");

        List<String> lafs = Arrays.stream(getInstalledLookAndFeels())
                                  .map(LookAndFeelInfo::getClassName)
                                  .collect(Collectors.toList());
        for (final String laf : lafs) {
            menuSelectedEventLatch = new CountDownLatch(1);
            try {
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

                // Select the node named as 'colors'
                Point pt = getNodeLocation(1);
                robot.mouseMove(pt.x, pt.y);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

                // Assertion check to verify that the selected node is 'colors'
                final String elementSelBefore = getCurrentNodeName();
                if (!"colors".equals(elementSelBefore)) {
                    throw new RuntimeException("Test failed for " + laf
                            + " as the tree node selected: " + elementSelBefore
                            + " is not the expected one 'colors'"
                    );
                }

                // Now operate Menu using Mnemonics, different key combinations for different OSes.
                // For most OSes it's ALT+F; on macOS it's ALT+CNTRL+F except for Nimbus LaF.
                if (isMac && !laf.contains("Nimbus")) {
                    hitKeys(KeyEvent.VK_ALT, KeyEvent.VK_CONTROL, FILE_MENU);
                } else {
                    hitKeys(KeyEvent.VK_ALT, FILE_MENU);
                }

                // Wait until the menu got selected.
                if (!menuSelectedEventLatch.await(3, TimeUnit.SECONDS)) {
                    throw new RuntimeException("Waited too long, but can't select menu using mnemonics for " + laf);
                }

                hitKeys(KeyEvent.VK_ENTER);

                String elementSelAfter = getCurrentNodeName();

                // As per the fix of BugID 4618767, the tree element selection should not change
                if (!elementSelBefore.equals(elementSelAfter)) {
                    throw new RuntimeException("Test failed for " + laf
                            + " as tree.getLastSelectedPathComponent() before: " + elementSelBefore
                            + " not same as tree.getLastSelectedPathComponent() after pressing Enter: "
                            + elementSelAfter
                    );
                }

                System.out.println("Test passed for laf: " + laf);

            } finally {
                SwingUtilities.invokeAndWait(JTreeSelectedElementTest::disposeFrame);
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

    private static String getCurrentNodeName() throws Exception {
        AtomicReference<String> nodeName = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            nodeName.set(tree.getLastSelectedPathComponent().toString().trim());
        });
        return nodeName.get();
    }

    private static Point getNodeLocation(int rowCount) throws Exception {
        AtomicReference<Point> treeNodeLoc = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            final Point locationOnScreen = tree.getLocationOnScreen();
            Rectangle rt = tree.getPathBounds(tree.getPathForRow(rowCount));
            locationOnScreen.translate(rt.x + rt.width / 2, rt.y + rt.height / 2);
            treeNodeLoc.set(locationOnScreen);
        });
        return treeNodeLoc.get();
    }

    private static void createUI() {
        frame = new JFrame();
        tree = new JTree();
        JMenu menu = new JMenu("File");
        menu.setMnemonic(FILE_MENU);
        JMenuItem menuItem = new JMenuItem("Dummy");
        menu.add(menuItem);
        menu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                menuSelectedEventLatch.countDown();
            }

            @Override
            public void menuDeselected(MenuEvent e) {
            }

            @Override
            public void menuCanceled(MenuEvent e) {
            }
        });

        JMenuBar menuBar = new JMenuBar();
        menuBar.add(menu);

        frame.setJMenuBar(menuBar);
        frame.setContentPane(tree);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.pack();
        frame.setAlwaysOnTop(true);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
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

}
