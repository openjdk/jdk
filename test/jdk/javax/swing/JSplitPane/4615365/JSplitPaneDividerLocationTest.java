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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;

import static javax.swing.UIManager.getInstalledLookAndFeels;
/*
 * @test
 * @key headful
 * @bug 4615365
 * @summary This test confirms that the JSplitPane's current and last
 *          divider positions are correct when realized.
 * @run main JSplitPaneDividerLocationTest
 */
public class JSplitPaneDividerLocationTest {

    private static JFrame frame;
    private static JPanel panel;
    private static JButton leftButton;
    private static JToggleButton triggerButton;
    private static volatile int currentLoc;
    private static volatile int lastLoc;
    private static volatile int lastLocExpected;
    private static Robot robot;

    public static void main(String[] s) throws Exception {
        robot = new Robot();
        robot.setAutoWaitForIdle(true);
        robot.setAutoDelay(200);
        List<String> lafs = Arrays.stream(getInstalledLookAndFeels())
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

                pressButton(triggerButton);

                // Verifies that JSplitPane current and last divider
                // positions are correct and not as per JDK-4615365.
                if ((currentLoc == -1) || (lastLoc == 0)) {
                    throw new RuntimeException(
                            "Test failed for " + laf + " :- last divider loc:" +
                            "actual = " + lastLoc + ",expected = -1, current " +
                            "divider loc:actual=" + currentLoc + ",expected>0");
                }
                lastLocExpected = currentLoc;

                // Slide the split pane divider slightly to the right side.
                final Point leftButtonLoc = getButtonLoc(leftButton);
                robot.mouseMove(leftButtonLoc.x + leftButton.getWidth() + 5,
                                leftButtonLoc.y + 35);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseMove(leftButtonLoc.x + leftButton.getWidth() + 8,
                                leftButtonLoc.y + 35);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                pressButton(triggerButton);

                // Verifies that JSplitPane current and last divider positions
                // reflects the correct positions after a right slide.
                if ((lastLoc == lastLocExpected) && (currentLoc > lastLoc)) {
                    System.out.println("Test Passed.");
                } else {
                    throw new RuntimeException(
                            "Test failed for " + laf + ", because after a " +
                            "right " + "slide" + ", last divider " +
                            "location: " + "actual = " + lastLoc +
                            ", expected = " + lastLocExpected +
                            ", current divider " + "location: actual = " +
                            currentLoc + ", expected > " + lastLoc);
                }
            } finally {
                SwingUtilities.invokeAndWait(
                        JSplitPaneDividerLocationTest::disposeFrame);
            }
        }
    }

    private static void pressButton(JToggleButton button) throws Exception {
        final Point buttonLoc = getButtonLoc(button);
        robot.mouseMove(buttonLoc.x + 8, buttonLoc.y + 8);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    private static Point getButtonLoc(AbstractButton button)
            throws InterruptedException, InvocationTargetException {
        final AtomicReference<Point> loc = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            loc.set(button.getLocationOnScreen());
        });
        final Point buttonLoc = loc.get();
        return buttonLoc;
    }

    private static void createUI() {
        frame = new JFrame();
        panel = new JPanel();
        panel.setLayout(new BorderLayout());
        leftButton = new JButton("Left Button");
        JButton rightButton = new JButton("Right Button");

        final JSplitPane splitPane =
                new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, leftButton,
                               rightButton);
        panel.add(splitPane, BorderLayout.CENTER);

        splitPane.setDividerSize(10);

        triggerButton = new JToggleButton("Trigger");
        triggerButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                currentLoc = splitPane.getDividerLocation();
                lastLoc = splitPane.getLastDividerLocation();
                System.out.println(
                        "currentLoc = " + currentLoc + ", lastLoc = " +
                        lastLoc);
            }
        });
        panel.add(triggerButton, BorderLayout.SOUTH);

        frame.setContentPane(panel);
        frame.setSize(300, 300);
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
