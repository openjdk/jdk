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

import java.awt.ComponentOrientation;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;


import static javax.swing.UIManager.getInstalledLookAndFeels;

/*
 * @test
 * @key headful
 * @bug 4515999
 * @summary Check whether incrementing dates via the keyboard (up/down) gives
 * the same results as using mouse press on the arrow buttons in a JSpinner.
 * @run main JSpinnerMouseAndKeyPressTest
 */
public class JSpinnerMouseAndKeyPressTest {
    // 2 days in milliseconds
    private static final int EXPECTED_VALUE_2_DAYS = 2 * 24 * 60 * 60 * 1000;

    private static JFrame frame;
    private static JSpinner spinner;
    private static volatile Point spinnerUpButtonCenter;
    private static volatile Point spinnerDownButtonCenter;
    private static volatile Date spinnerValue;

    public static void main(String[] s) throws Exception {
        runTest();
    }

    private static void setLookAndFeel(final String laf) {
        try {
            UIManager.setLookAndFeel(laf);
            System.out.println("LookAndFeel: " + laf);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void createUI() {
        frame = new JFrame();
        JPanel panel = new JPanel();
        spinner = new JSpinner();
        spinner.setModel(new DateModel());
        JSpinner.DateEditor editor = new JSpinner.DateEditor(spinner, "dd/MM/yy");
        spinner.setEditor(editor);
        spinner.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        panel.add(spinner);
        frame.add(panel);
        frame.setUndecorated(true);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.pack();
        frame.setAlwaysOnTop(true);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }


    public static void runTest() throws Exception {
        Robot robot = new Robot();
        robot.setAutoWaitForIdle(true);
        robot.setAutoDelay(100);
        List<String> lafs = Arrays.stream(getInstalledLookAndFeels())
                                  .map(UIManager.LookAndFeelInfo::getClassName)
                                  .collect(Collectors.toList());
        for (final String laf : lafs) {
            try {
                SwingUtilities.invokeAndWait(() -> {
                    setLookAndFeel(laf);
                    createUI();
                });
                robot.waitForIdle();

                SwingUtilities.invokeAndWait(() -> {
                    Point loc = spinner.getLocationOnScreen();
                    int editorWidth = spinner.getEditor().getWidth();
                    int buttonWidth = spinner.getWidth() - editorWidth;
                    int quarterHeight = spinner.getHeight() / 4;

                    spinnerUpButtonCenter = new Point(loc.x + editorWidth
                            + (buttonWidth / 2),
                            loc.y + quarterHeight);
                    spinnerDownButtonCenter = new Point(spinnerUpButtonCenter.x,
                            loc.y + (3 * quarterHeight));
                });

                // Mouse press use-case
                // Move Mouse pointer to UP button center and click it
                robot.mouseMove(spinnerUpButtonCenter.x, spinnerUpButtonCenter.y);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

                updateSpinnerValue();
                long upValue = spinnerValue.getTime();

                // Move Mouse pointer to DOWN button center and click it
                robot.mouseMove(spinnerDownButtonCenter.x, spinnerDownButtonCenter.y);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

                updateSpinnerValue();
                long downValue = spinnerValue.getTime();

                long mouseIncrement = upValue - downValue;

                // Key press use-case
                // Up Key press
                robot.keyPress(KeyEvent.VK_UP);
                robot.keyRelease(KeyEvent.VK_UP);

                updateSpinnerValue();
                upValue = spinnerValue.getTime();

                // Down Key press
                robot.keyPress(KeyEvent.VK_DOWN);
                robot.keyRelease(KeyEvent.VK_DOWN);

                updateSpinnerValue();
                downValue = spinnerValue.getTime();

                long keyIncrement = upValue - downValue;

                if ((keyIncrement == EXPECTED_VALUE_2_DAYS) &&
                        (mouseIncrement == EXPECTED_VALUE_2_DAYS)) {
                    System.out.println("Test passed");
                } else {
                    throw new RuntimeException("Test failed because keyIncrement: " +
                            keyIncrement + " and mouseIncrement: " +
                            mouseIncrement + " should match with the expected value " +
                            EXPECTED_VALUE_2_DAYS + " for LnF " + laf);
                }

            } finally {
                SwingUtilities.invokeAndWait(JSpinnerMouseAndKeyPressTest::disposeFrame);
            }
        }
    }

    private static void updateSpinnerValue() throws Exception {
        SwingUtilities.invokeAndWait(() -> spinnerValue = (Date) spinner.getValue());
    }

    private static void disposeFrame() {
        if (frame != null) {
            frame.dispose();
            frame = null;
        }
    }

    private static class DateModel extends SpinnerDateModel {

        private final Calendar cal = Calendar.getInstance();

        @Override
        public Object getNextValue() {
            cal.setTime(getDate());
            cal.add(Calendar.DAY_OF_MONTH, 2); // Increment two days
            return cal.getTime();
        }

        @Override
        public Object getPreviousValue() {
            cal.setTime(getDate());
            cal.add(Calendar.DAY_OF_MONTH, -2); // Decrement two days
            return cal.getTime();
        }
    }
}
