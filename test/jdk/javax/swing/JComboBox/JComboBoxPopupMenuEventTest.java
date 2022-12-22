/*
 * Copyright (c) 2001, 2022, Oracle and/or its affiliates. All rights reserved.
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
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import static javax.swing.UIManager.getInstalledLookAndFeels;

/*
 * @test
 * @key headful
 * @bug 4287690 4331058
 * @summary This testcase tests RFE-4287690 and RFE-4331058 requests,
 *          JComboBox should send drop down visible as well as invisible events.
 * @run main JComboBoxPopupMenuEventTest
 */
public class JComboBoxPopupMenuEventTest {

    private static final String[] compStrs =
            {"Apple", "Citibank", "Cisco", "Cienna", "Oracle", "IBM"};
    private static Robot robot;
    private static JComboBox comboBox;
    private static JTextField searchTextField;
    private static CountDownLatch popupMenuVisibleLatch;
    private static CountDownLatch popupMenuInvisibleLatch;
    private static JFrame frame;

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        robot.setAutoWaitForIdle(true);
        robot.setAutoDelay(200);
        List<String> lafs = Arrays.stream(getInstalledLookAndFeels())
                                  .map(LookAndFeelInfo::getClassName)
                                  .collect(Collectors.toList());
        for (final String laf : lafs) {
            try {
                popupMenuVisibleLatch = new CountDownLatch(1);
                popupMenuInvisibleLatch = new CountDownLatch(1);
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

                mouseClick(searchTextField);
                hitKeys(KeyEvent.VK_C, KeyEvent.VK_I);
                mouseClick(comboBox);

                // Verifying whether popupMenuWillBecomeVisible method of
                // PopupMenuListener gets called when popup menu appears.
                if (!popupMenuVisibleLatch.await(3, TimeUnit.SECONDS)) {
                    throw new RuntimeException(
                            "Waited too long, but popupMenuWillBecomeVisible " +
                            "not yet got called for " + laf);
                }

                hitKeys(KeyEvent.VK_ENTER);

                // Verifying whether popupMenuWillBecomeInvisible method of
                // PopupMenuListener gets called when popup menu disappears.
                if (!popupMenuInvisibleLatch.await(3, TimeUnit.SECONDS)) {
                    throw new RuntimeException(
                            "Waited too long, but popupMenuWillBecomeInvisible " +
                            "not yet got called for " + laf);
                }

                System.out.println("Test passed for " + laf);
            } finally {
                SwingUtilities.invokeAndWait(
                        JComboBoxPopupMenuEventTest::disposeFrame);
            }
        }
    }

    private static void mouseClick(JComponent jComponent) throws Exception {
        final Point location = getLocationOnScreen(jComponent);
        robot.mouseMove(location.x + 8, location.y + 8);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    private static Point getLocationOnScreen(JComponent jComponent)
            throws Exception {
        final AtomicReference<Point> loc = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> loc.set(jComponent.getLocationOnScreen()));
        return loc.get();
    }

    private static void hitKeys(int... keys) {
        for (int key : keys) {
            robot.keyPress(key);
        }

        for (int i = keys.length - 1; i >= 0; i--) {
            robot.keyRelease(keys[i]);
        }
    }

    public static void createUI() {
        frame = new JFrame();
        JPanel panel = new JPanel();
        searchTextField = new JTextField(6);
        panel.add(searchTextField);
        comboBox = new JComboBox(compStrs);
        panel.add(comboBox);
        comboBox.addPopupMenuListener(new PopupMenuListener() {
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                System.out.println("popupMenuWillBecomeVisible() got called");
                popupMenuVisibleLatch.countDown();
                comboBox.removeAllItems();
                String text = searchTextField.getText().trim();
                Arrays.stream(compStrs)
                      .filter(str -> str.toLowerCase().startsWith(text))
                      .forEach(str -> comboBox.addItem(str));
            }

            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                System.out.println("popupMenuWillBecomeInvisible() got called");
                popupMenuInvisibleLatch.countDown();
            }

            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });

        frame.setContentPane(panel);
        frame.setSize(250, 100);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
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
