/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8262981
 * @key headful
 * @summary Test JSlider Accessibility doAccessibleAction(int)
 * @run main JSliderAccessibleAction
 */

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import static java.util.stream.Collectors.toList;

public class JSliderAccessibleAction {

    private static JFrame jFrame;
    private static JSlider jSlider;
    private static JButton decrementBtn;
    private static JButton incrementBtn;
    private static JButton invalidDecrementBtn;
    private static JButton invalidIncrementBtn;
    private static final int INVALID_DECREMENT = 2;
    private static final int INVALID_INCREMENT = -1;
    private static final int VALID_DECREMENT = 1;
    private static final int VALID_INCREMENT = 0;
    private static volatile int currentJSliderValue;
    private static volatile int jSliderInitialValue;
    private static volatile CountDownLatch invalidDecrementCountDownLatch;
    private static volatile CountDownLatch invalidIncrementCountDownLatch;
    private static volatile CountDownLatch validDecrementCountDownLatch;
    private static volatile CountDownLatch validIncrementCountDownLatch;

    private static void createTestUI() {
        jFrame = new JFrame("Test JSlider Accessible Action");
        jSlider = new JSlider();
        AccessibleContext ac = jSlider.getAccessibleContext();
        ac.setAccessibleName("JSlider Accessible Test");

        AccessibleContext accessibleContext = jSlider.getAccessibleContext();
        AccessibleAction accessibleAction =
                accessibleContext.getAccessibleAction();

        if (accessibleAction == null) {
            throw new RuntimeException("JSlider getAccessibleAction() should " +
                    "not be null");
        }

        if (accessibleAction.getAccessibleActionCount() != 2) {
            throw new RuntimeException("JSlider AccessibleAction supports " +
                    "only two actions ( AccessibleAction.DECREMENT & " +
                    "AccessibleAction.INCREMENT ) but got " + accessibleAction.getAccessibleActionCount());
        }

        JLabel jSliderValueLbl = new JLabel("JSlider value : " + jSlider.getValue() + "%",
                JLabel.CENTER);
        Container container = jFrame.getContentPane();

        container.add(jSliderValueLbl, BorderLayout.NORTH);
        container.add(jSlider, BorderLayout.CENTER);

        jSlider.addChangeListener((changeEvent) -> {
            currentJSliderValue = jSlider.getValue();
            jSliderValueLbl.setText("JSlider value : " + currentJSliderValue + "%");
            System.out.println("changed : " + changeEvent);
        });

        invalidDecrementBtn = new JButton("Invalid Decrement");
        invalidDecrementBtn.addActionListener((actionEvent) -> {
            invalidDecrementCountDownLatch.countDown();
            accessibleAction.doAccessibleAction(INVALID_DECREMENT);
        });

        invalidIncrementBtn = new JButton("Invalid Increment");
        invalidIncrementBtn.addActionListener((actionEvent) -> {
            invalidIncrementCountDownLatch.countDown();
            accessibleAction.doAccessibleAction(INVALID_INCREMENT);
        });

        decrementBtn = new JButton("Decrement");
        decrementBtn.addActionListener((actionEvent) -> {
            validDecrementCountDownLatch.countDown();
            accessibleAction.doAccessibleAction(VALID_DECREMENT);
        });

        incrementBtn = new JButton("Increment");
        incrementBtn.addActionListener((actionEvent) -> {
            accessibleAction.doAccessibleAction(VALID_INCREMENT);
            validIncrementCountDownLatch.countDown();
        });

        JPanel buttonPanel = new JPanel(new GridLayout(4, 1));
        buttonPanel.add(invalidDecrementBtn);
        buttonPanel.add(invalidIncrementBtn);
        buttonPanel.add(decrementBtn);
        buttonPanel.add(incrementBtn);
        container.add(buttonPanel, BorderLayout.SOUTH);

        jFrame.pack();
        jFrame.setLocationRelativeTo(null);
        jFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        jFrame.setVisible(true);
    }

    private static boolean setLookAndFeel(String lafName) {
        try {
            UIManager.setLookAndFeel(lafName);
        } catch (UnsupportedLookAndFeelException unsupportedLookAndFeelException) {
            System.out.println("Ignoring Unsupported laf : " + lafName);
            return false;
        } catch (ClassNotFoundException | InstantiationException
                | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    public static void testJSliderAccessibleAction() throws AWTException,
            InterruptedException, InvocationTargetException {
        Robot robot = new Robot();
        robot.setAutoDelay(300);
        robot.waitForIdle();

        List<String> installedLookAndFeels =
                Arrays.stream(UIManager.getInstalledLookAndFeels())
                        .map(UIManager.LookAndFeelInfo::getClassName).collect(toList());

        for (String lookAndFeel : installedLookAndFeels) {
            try {
                invalidDecrementCountDownLatch = new CountDownLatch(1);
                invalidIncrementCountDownLatch = new CountDownLatch(1);
                validDecrementCountDownLatch = new CountDownLatch(1);
                validIncrementCountDownLatch = new CountDownLatch(1);
                currentJSliderValue = 0;
                jSliderInitialValue = 0;
                System.out.println("Testing JSliderAccessibleAction in " + lookAndFeel +
                        " look and feel");

                AtomicBoolean lafSetSuccess = new AtomicBoolean(false);
                SwingUtilities.invokeAndWait(() -> {
                    lafSetSuccess.set(setLookAndFeel(lookAndFeel));
                    if (lafSetSuccess.get()) {
                        createTestUI();
                    }
                });
                if (!lafSetSuccess.get()) continue;
                robot.waitForIdle();

                SwingUtilities.invokeAndWait(() -> {
                    jSliderInitialValue = jSlider.getValue();
                    currentJSliderValue = jSlider.getValue();
                });
                robot.waitForIdle();
                mouseAction(robot, invalidDecrementBtn);
                if (!invalidDecrementCountDownLatch.await(30,
                        TimeUnit.SECONDS)) {
                    throw new RuntimeException("Failed to perform action on Invalid " +
                            "Decrement button");
                }

                if (jSliderInitialValue != currentJSliderValue ) {
                    throw new RuntimeException("Expected that JSlider value is not " +
                            "changed when invalid decrement value 2 is passed to " +
                            "doAccessibleAction(2)  jSliderInitialValue = "
                            + jSliderInitialValue + "  currentJSliderValue = " + currentJSliderValue);
                }

                mouseAction(robot, invalidIncrementBtn);
                if (!invalidIncrementCountDownLatch.await(30,
                        TimeUnit.SECONDS)) {
                    throw new RuntimeException("Failed to perform action on Invalid " +
                            "Increment button");
                }
                if (jSliderInitialValue != currentJSliderValue) {
                    throw new RuntimeException("Expected that JSlider value is not " +
                            "changed when invalid decrement value -1 is passed to " +
                            "doAccessibleAction(-1)  jSliderInitialValue = "
                            + jSliderInitialValue + "  currentJSliderValue = " + currentJSliderValue);
                }

                // JSlider value is decremented
                mouseAction(robot, decrementBtn);
                if (!validDecrementCountDownLatch.await(30, TimeUnit.SECONDS)) {
                    throw new RuntimeException("Failed to perform action on valid " +
                            "decrement button");
                }
                if (jSliderInitialValue == currentJSliderValue ) {
                    throw new RuntimeException("Expected that JSlider value is  " +
                            "decremented when value 1 is passed to " +
                            "doAccessibleAction(1)  jSliderInitialValue = "
                            + jSliderInitialValue + "  currentJSliderValue = " + currentJSliderValue);
                }

                // JSlider value is incremented
                mouseAction(robot, incrementBtn);
                if (!validIncrementCountDownLatch.await(30, TimeUnit.SECONDS)) {
                    throw new RuntimeException("Failed to perform action on valid " +
                            "Increment button");
                }
                if (jSliderInitialValue != currentJSliderValue ) {
                    throw new RuntimeException("Expected that JSlider value is  " +
                            "incremented when value 0 is passed to " +
                            "doAccessibleAction(0)  jSliderInitialValue = "
                            + jSliderInitialValue + "  currentJSliderValue = " + currentJSliderValue);
                }
            } finally {
                SwingUtilities.invokeAndWait(() -> {
                    if (jFrame != null) {
                        jFrame.dispose();
                    }
                });
            }
        }
    }

    public static void mouseAction(Robot robot, JButton button) throws InterruptedException,
            InvocationTargetException {
        robot.waitForIdle();
        Point[] point = new Point[1];
        Rectangle[] rect = new Rectangle[1];

        SwingUtilities.invokeAndWait(() -> {
            point[0] = button.getLocationOnScreen();
            rect[0] = button.getBounds();
        });

        robot.mouseMove(point[0].x + rect[0].width / 2,
                point[0].y + rect[0].height / 2);
        robot.waitForIdle();
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
    }

    public static void main(String[] args) throws InterruptedException, InvocationTargetException, AWTException {
        testJSliderAccessibleAction();
    }
}
