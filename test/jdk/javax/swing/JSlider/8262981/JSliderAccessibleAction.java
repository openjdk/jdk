/*
 * Copyright (c) 2022 Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.atomic.AtomicInteger;
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
    private static AtomicInteger currentJSliderValue;
    private static AtomicInteger jSliderInitialValue;
    private static AtomicBoolean actionDescriptionValue;
    private static CountDownLatch invalidDecrementCountDownLatch;
    private static CountDownLatch invalidIncrementCountDownLatch;
    private static CountDownLatch validDecrementCountDownLatch;
    private static CountDownLatch validIncrementCountDownLatch;
    private static final int INVALID_DECREMENT = 2;
    private static final int INVALID_INCREMENT = -1;
    private static final int VALID_DECREMENT = 1;
    private static final int VALID_INCREMENT = 0;

    private void createTestUI() {
        jFrame = new JFrame("Test JSlider Accessible Action");
        jSlider = new JSlider();
        AccessibleContext ac = jSlider.getAccessibleContext();
        ac.setAccessibleName("JSlider Accessible Test");

        AccessibleContext accessibleContext = jSlider.getAccessibleContext();
        AccessibleAction accessibleAction = accessibleContext.getAccessibleAction();

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

        invalidDecrementBtn = new JButton("Invalid Decrement");
        invalidDecrementBtn.addActionListener((actionEvent) -> {
            invalidDecrementCountDownLatch.countDown();
            updateJSliderValue(accessibleAction, jSliderValueLbl, INVALID_DECREMENT);
            actionDescriptionValue.getAndSet(accessibleAction.getAccessibleActionDescription(INVALID_DECREMENT) == null);
        });

        invalidIncrementBtn = new JButton("Invalid Increment");
        invalidIncrementBtn.addActionListener((actionEvent) -> {
            invalidIncrementCountDownLatch.countDown();
            updateJSliderValue(accessibleAction, jSliderValueLbl, INVALID_INCREMENT);
            actionDescriptionValue.getAndSet(accessibleAction.getAccessibleActionDescription(INVALID_INCREMENT) == null);
        });

        decrementBtn = new JButton("Decrement");
        decrementBtn.addActionListener((actionEvent) -> {
            validDecrementCountDownLatch.countDown();
            updateJSliderValue(accessibleAction, jSliderValueLbl, VALID_DECREMENT);
            actionDescriptionValue.getAndSet(accessibleAction.getAccessibleActionDescription(VALID_DECREMENT).equals(AccessibleAction.DECREMENT));
        });

        incrementBtn = new JButton("Increment");
        incrementBtn.addActionListener((actionEvent) -> {
            validIncrementCountDownLatch.countDown();
            updateJSliderValue(accessibleAction, jSliderValueLbl, VALID_INCREMENT);
            actionDescriptionValue.getAndSet(accessibleAction.getAccessibleActionDescription(VALID_INCREMENT).equals(AccessibleAction.INCREMENT));
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

    private void updateJSliderValue(AccessibleAction accessibleAction,
                                    JLabel lbl, int value) {
        accessibleAction.doAccessibleAction(value);
        currentJSliderValue.getAndSet(jSlider.getValue());
        lbl.setText("JSlider value : " + currentJSliderValue.get() + "%");
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

    public void test() throws AWTException,
            InterruptedException, InvocationTargetException {
        Robot robot = new Robot();
        robot.setAutoDelay(300);
        robot.waitForIdle();

        List<String> instLookAndFeels =
                Arrays.stream(UIManager.getInstalledLookAndFeels())
                        .map(UIManager.LookAndFeelInfo::getClassName).collect(toList());

        for (String laf : instLookAndFeels) {
            try {
                invalidDecrementCountDownLatch = new CountDownLatch(1);
                invalidIncrementCountDownLatch = new CountDownLatch(1);
                validDecrementCountDownLatch = new CountDownLatch(1);
                validIncrementCountDownLatch = new CountDownLatch(1);
                currentJSliderValue = new AtomicInteger();
                jSliderInitialValue = new AtomicInteger();
                actionDescriptionValue = new AtomicBoolean(false);
                System.out.println("Testing JSliderAccessibleAction in " + laf +
                        " look and feel");

                AtomicBoolean lafSetSuccess = new AtomicBoolean(false);
                SwingUtilities.invokeAndWait(() -> {
                    lafSetSuccess.set(setLookAndFeel(laf));
                    if (lafSetSuccess.get()) {
                        createTestUI();
                    }
                });
                if (!lafSetSuccess.get()) continue;
                robot.waitForIdle();

                SwingUtilities.invokeAndWait(() -> jSliderInitialValue.getAndSet(jSlider.getValue()));

                mouseAction(robot, invalidDecrementBtn);
                if (!invalidDecrementCountDownLatch.await(30,
                        TimeUnit.SECONDS)) {
                    throw new RuntimeException("Failed to perform action on Invalid " +
                            "Decrement button");
                }
                if (jSliderInitialValue.get() != currentJSliderValue.get()) {
                    throw new RuntimeException("Expected that JSlider value is not " +
                            "changed when invalid decrement value 2 is passed to " +
                            "doAccessibleAction(2)  jSliderInitialValue = "
                            + jSliderInitialValue + "  currentJSliderValue = " + currentJSliderValue);
                }

                if (!actionDescriptionValue.get()) {
                    throw new RuntimeException("Expected that JSlider's " +
                            "AccessibleAction getAccessibleActionDescription(2) " +
                            "return null");
                }

                mouseAction(robot, invalidIncrementBtn);
                if (!invalidIncrementCountDownLatch.await(30,
                        TimeUnit.SECONDS)) {
                    throw new RuntimeException("Failed to perform action on Invalid " +
                            "Increment button");
                }
                if (jSliderInitialValue.get() != currentJSliderValue.get()) {
                    throw new RuntimeException("Expected that JSlider value is not " +
                            "changed when invalid decrement value -1 is passed to " +
                            "doAccessibleAction(-1)  jSliderInitialValue = "
                            + jSliderInitialValue + "  currentJSliderValue = " + currentJSliderValue);
                }

                if (!actionDescriptionValue.get()) {
                    throw new RuntimeException("Expected that JSlider's " +
                            "AccessibleAction getAccessibleActionDescription(-1) " +
                            "return null");
                }

                // JSlider value is decremented
                mouseAction(robot, decrementBtn);
                if (!validDecrementCountDownLatch.await(30, TimeUnit.SECONDS)) {
                    throw new RuntimeException("Failed to perform action on valid " +
                            "decrement button");
                }
                if (jSliderInitialValue.get() == currentJSliderValue.get()) {
                    throw new RuntimeException("Expected that JSlider value is  " +
                            "decremented when value 1 is passed to " +
                            "doAccessibleAction(1)  jSliderInitialValue = "
                            + jSliderInitialValue + "  currentJSliderValue = " + currentJSliderValue);
                }

                if (!actionDescriptionValue.get()) {
                    throw new RuntimeException("Expected that JSlider's " +
                            "AccessibleAction getAccessibleActionDescription(1) " +
                            "return AccessibleAction.DECREMENT");
                }

                // JSlider value is incremented
                mouseAction(robot, incrementBtn);
                if (!validIncrementCountDownLatch.await(30, TimeUnit.SECONDS)) {
                    throw new RuntimeException("Failed to perform action on valid " +
                            "Increment button");
                }
                if (jSliderInitialValue.get() != currentJSliderValue.get()) {
                    throw new RuntimeException("Expected that JSlider value is  " +
                            "incremented when value 0 is passed to " +
                            "doAccessibleAction(0)  jSliderInitialValue = "
                            + jSliderInitialValue + "  currentJSliderValue = " + currentJSliderValue);
                }

                if (!actionDescriptionValue.get()) {
                    throw new RuntimeException("Expected that JSlider's " +
                            "AccessibleAction getAccessibleActionDescription(0) " +
                            "return AccessibleAction.INCREMENT");
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
        final Point[] point = new Point[1];
        final Rectangle[] rect = new Rectangle[1];

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

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException {
        JSliderAccessibleAction jSliderAccessibleAction =
                new JSliderAccessibleAction();
        jSliderAccessibleAction.test();
    }
}
