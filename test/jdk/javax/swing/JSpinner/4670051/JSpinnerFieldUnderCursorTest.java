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
 * @bug 4670051
 * @summary Checks whether JSpinner with a SpinnerDateModel
 * exactly spins the field where cursor is there.
 * @run main JSpinnerFieldUnderCursorTest
 */
public class JSpinnerFieldUnderCursorTest {

    private static final Calendar cal1 = Calendar.getInstance();
    private static final Calendar cal2 = Calendar.getInstance();
    private static Robot robot;
    private static JSpinner spinner;
    private static Date initValue;
    private static Date upValue;
    private static Date downValue;
    private static JFrame frame;
    private static boolean passed = true;
    private static volatile Point spinnerUpButtonCenter;
    private static volatile Point spinnerDownButtonCenter;
    private static volatile Date spinnerValue;

    private static void createUI() {
        frame = new JFrame();
        JPanel panel = new JPanel();

        spinner = new JSpinner(new SpinnerDateModel());
        JSpinner.DateEditor editor = new JSpinner.DateEditor(spinner, " dd/MM/yy ");
        spinner.setEditor(editor);
        spinner.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        panel.add(spinner);

        frame.add(panel);
        frame.setUndecorated(true);
        frame.pack();
        frame.setAlwaysOnTop(true);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

    }

    private static void setLookAndFeel(final String laf) {
        try {
            UIManager.setLookAndFeel(laf);
            System.out.println("LookAndFeel: " + laf);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void runTest() throws Exception {
        robot = new Robot();
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

                // Cursor at Day field.
                updateSpinnerValue();
                // Increment Day
                initValue = spinnerValue;
                mousePressOnUpButton();
                updateSpinnerValue();
                upValue = spinnerValue;
                verifyDayIncrement();
                // Decrement Day
                updateSpinnerValue();
                initValue = spinnerValue;
                mousePressOnDownButton();
                updateSpinnerValue();
                downValue = spinnerValue;
                verifyDayDecrement();

                // Cursor at Month Field
                pressRightArrowKey();
                // Increment Month
                updateSpinnerValue();
                initValue = spinnerValue;
                mousePressOnUpButton();
                updateSpinnerValue();
                upValue = spinnerValue;
                verifyMonthIncrement();
                // Decrement Month
                updateSpinnerValue();
                initValue = spinnerValue;
                mousePressOnDownButton();
                updateSpinnerValue();
                downValue = spinnerValue;
                verifyMonthDecrement();

                // Cursor at Year Field
                pressRightArrowKey();
                // Increment Year
                updateSpinnerValue();
                initValue = spinnerValue;
                mousePressOnUpButton();
                updateSpinnerValue();
                upValue = spinnerValue;
                verifyYearIncrement();
                // Decrement Year
                updateSpinnerValue();
                initValue = spinnerValue;
                mousePressOnDownButton();
                updateSpinnerValue();
                downValue = spinnerValue;
                verifyYearDecrement();

                if (passed) {
                    System.out.println("Test Passed");
                } else {
                    throw new RuntimeException("Test Failed as one or more cases failed");
                }
            } finally {
                SwingUtilities.invokeAndWait(JSpinnerFieldUnderCursorTest::disposeFrame);
            }
        }
    }

    private static void updateSpinnerValue() throws Exception {
        SwingUtilities.invokeAndWait(() -> spinnerValue = (Date) spinner.getValue());
    }


    public static void pressRightArrowKey() {
        robot.keyPress(KeyEvent.VK_RIGHT);
        robot.keyRelease(KeyEvent.VK_RIGHT);
    }


    public static void mousePressOnUpButton() {
        robot.mouseMove(spinnerUpButtonCenter.x, spinnerUpButtonCenter.y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

    }

    public static void mousePressOnDownButton() {
        robot.mouseMove(spinnerDownButtonCenter.x, spinnerDownButtonCenter.y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    public static void main(String[] s) throws Exception {
        runTest();
    }

    public static boolean compareDates(Calendar d1, Calendar d2) {
        return (d1.get(Calendar.DATE) == d2.get(Calendar.DATE)) &&
                (d1.get(Calendar.DAY_OF_YEAR) == d2.get(Calendar.DAY_OF_YEAR)) &&
                (d1.get(Calendar.DAY_OF_MONTH) == d2.get(Calendar.DAY_OF_MONTH));
    }

    private static void disposeFrame() {
        if (frame != null) {
            frame.dispose();
            frame = null;
        }
    }


    private static void checkResult() {
        if (compareDates(cal1, cal2)) {
            System.out.println(" Case Passed");
        } else {
            passed = false;
            System.out.println(" Case Failed because the expected: " + cal1.getTime()
                    + " and actual: " + cal2.getTime() + " outputs do not match.");
        }
    }


    private static void updateCalendarObjects(Date finalValue) {
        cal1.setTime(initValue);
        cal2.setTime(finalValue);
    }


    /**
     * Verifying that JSpinner increments the date field when cursor is on date field
     */
    private static void verifyDayIncrement() {
        System.out.print("verifyDateIncrement");
        updateCalendarObjects(upValue);
        cal1.add(Calendar.DATE, 1);
        checkResult();
    }

    /**
     * Verifying that JSpinner decrements the date field when cursor is on date field
     */
    private static void verifyDayDecrement() {
        System.out.print("verifyDateDecrement");
        updateCalendarObjects(downValue);
        cal1.add(Calendar.DATE, -1);
        checkResult();
    }

    /**
     * Verifying that JSpinner increments the month field when cursor is on month field
     */
    private static void verifyMonthIncrement() {
        System.out.print("verifyMonthIncrement");
        updateCalendarObjects(upValue);
        cal1.add(Calendar.MONTH, 1);
        checkResult();
    }


    /**
     * Verifying that JSpinner decrements the month field when cursor is on month field
     */
    private static void verifyMonthDecrement() {
        System.out.print("verifyMonthDecrement");
        updateCalendarObjects(downValue);
        cal1.add(Calendar.MONTH, -1);
        checkResult();
    }

    /**
     * Verifying that, JSpinner decrements the year field when the cursor is on year field.
     */
    private static void verifyYearDecrement() {
        System.out.print("verifyYearDecrement");
        updateCalendarObjects(downValue);
        cal1.add(Calendar.YEAR, -1);
        checkResult();
    }

    /**
     * Verifying that JSpinner increments the year field when cursor is on year field
     */
    private static void verifyYearIncrement() {
        System.out.print("verifyYearIncrement");
        updateCalendarObjects(upValue);
        cal1.add(Calendar.YEAR, 1);
        checkResult();
    }

}
