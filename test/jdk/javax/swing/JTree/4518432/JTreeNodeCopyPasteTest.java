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

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.swing.JFrame;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;

import static javax.swing.UIManager.getInstalledLookAndFeels;

/*
 * @test
 * @key headful
 * @bug 4518432
 * @summary Verifies that Copying from JTree node and then changing the data in the component that was copied from,
 *          is not causing the pastes to use the new edited data instead of the original copied data.
 * @run main JTreeNodeCopyPasteTest
 */
public class JTreeNodeCopyPasteTest {

    private static JFrame frame;
    private static JTree tree;
    private static Robot robot;
    private static boolean isMac;

    public static void main(String[] args) throws Exception {
        runTest();
    }

    private static void runTest() throws Exception {
        isMac = System.getProperty("os.name").toLowerCase().contains("os x");
        robot = new Robot();
        robot.setAutoDelay(100);
        robot.setAutoWaitForIdle(true);

        // Filter out Motif laf, as it doesn't support copy-paste in JTree.
        List<String> lafs = Arrays.stream(getInstalledLookAndFeels())
                                  .filter(laf -> !laf.getName().contains("Motif"))
                                  .map(LookAndFeelInfo::getClassName)
                                  .collect(Collectors.toList());
        for (final String laf : lafs) {
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

                String expectedNodeName = getCurrentNodeName();

                // Copy the contents of that node
                copyOrPaste(KeyEvent.VK_C, laf);

                // Edit the Contents of that cell
                mouseTripleClick();

                typeSomeText();

                robot.keyPress(KeyEvent.VK_ENTER);
                robot.keyRelease(KeyEvent.VK_ENTER);

                // Select next node
                pt = getNodeLocation(2);
                robot.mouseMove(pt.x, pt.y);

                // Edit the Contents of that cell
                mouseTripleClick();

                // paste the content copied earlier
                copyOrPaste(KeyEvent.VK_V, laf);

                robot.keyPress(KeyEvent.VK_ENTER);
                robot.keyRelease(KeyEvent.VK_ENTER);

                // Now get the node contents of second node
                String actualNodeName = getCurrentNodeName();

                if (expectedNodeName.equals(actualNodeName)) {
                    System.out.println("Test Passed in " + laf);
                } else {
                    throw new RuntimeException("Test Failed in " + laf + ", Expected : " + expectedNodeName
                            + ", but actual : " + actualNodeName);
                }
            } finally {
                SwingUtilities.invokeAndWait(JTreeNodeCopyPasteTest::disposeFrame);
            }
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
            locationOnScreen.translate((int) (rt.getX() + rt.getWidth() / 2), (int) (rt.getY() + rt.getHeight() / 2));
            treeNodeLoc.set(locationOnScreen);
        });
        return treeNodeLoc.get();
    }

    private static void copyOrPaste(int keyCode, String laf) {
        // For AquaLookAndFeel in Mac, the key combination for copy/paste is META + (C or V)
        // For other OSes and other lafs, the key combination is CONTROL + (C or V)
        robot.keyPress(isMac && laf.contains("Aqua") ? KeyEvent.VK_META : KeyEvent.VK_CONTROL);
        robot.keyPress(keyCode);
        robot.keyRelease(keyCode);
        robot.keyRelease(isMac && laf.contains("Aqua") ? KeyEvent.VK_META : KeyEvent.VK_CONTROL);
    }

    private static void mouseTripleClick() {
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    private static void typeSomeText() {
        robot.keyPress(KeyEvent.VK_T);
        robot.keyRelease(KeyEvent.VK_T);
        robot.keyPress(KeyEvent.VK_E);
        robot.keyRelease(KeyEvent.VK_E);
        robot.keyPress(KeyEvent.VK_X);
        robot.keyRelease(KeyEvent.VK_X);
        robot.keyPress(KeyEvent.VK_T);
        robot.keyRelease(KeyEvent.VK_T);
    }

    private static void createUI() {
        frame = new JFrame();
        tree = new JTree();
        tree.setEditable(true);
        frame.setContentPane(tree);
        frame.setSize(new Dimension(200, 200));
        frame.setAlwaysOnTop(true);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.toFront();
        frame.setVisible(true);
    }

    private static boolean setLookAndFeel(String lafName) {
        try {
            UIManager.setLookAndFeel(lafName);
        } catch (UnsupportedLookAndFeelException ignored) {
            System.out.println("Ignoring Unsupported laf : " + lafName);
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
