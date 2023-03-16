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

/*
 * @test
 * @key headful
 * @bug 8302558
 * @summary Tests if the Popup from an editable ComboBox with a border
 *          is in the correct position
 * @run main EditableComboBoxPopupPos
 */

import java.awt.AWTException;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public class EditableComboBoxPopupPos {
    private static Robot robot;
    private static JFrame frame;
    private static JPanel panel;
    private static JComboBox cb1, cb2, cb3, cb4;
    private static String lafName, cb1Str, cb2Str, cb3Str, cb4Str;
    private static Point cb1Point, cb2Point, cb3Point, cb4Point;
    private static int cb1Width, cb1Height, cb2Width, cb2Height,
            cb3Width, cb3Height, cb4Width, cb4Height;

    private static final int BUTTON_OFFSET = 8;
    private static final int POPUP_OFFSET = 6;

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException, IOException {
        robot = new Robot();
        robot.setAutoDelay(100);
        robot.setAutoWaitForIdle(true);

        for (UIManager.LookAndFeelInfo laf : UIManager.getInstalledLookAndFeels()) {
            try {
                lafName = laf.getName();
                SwingUtilities.invokeAndWait(() -> {
                    setLookAndFeel(laf);
                    panel = new JPanel();
                    GridLayout gridLayout = new GridLayout(2, 2);
                    panel.setLayout(gridLayout);
                    String[] comboStrings = {"One", "Two", "Three"};

                    cb1 = new JComboBox(comboStrings);
                    cb1.setEditable(true);
                    cb1.setBorder(BorderFactory.createTitledBorder(
                            "Editable JComboBox"));

                    cb2 = new JComboBox(comboStrings);
                    cb2.setEditable(true);

                    cb3 = new JComboBox(comboStrings);
                    cb3.setEditable(false);
                    cb3.setBorder(BorderFactory.createTitledBorder(
                            "Non-editable JComboBox"));

                    cb4 = new JComboBox(comboStrings);
                    cb4.setEditable(false);

                    if (lafName.contains("Mac")) {
                        // By default, non-editable ComboBoxes don't appear
                        // underneath unless this is explicitly set
                        cb3.putClientProperty("JComboBox.isPopDown", Boolean.TRUE);
                        cb4.putClientProperty("JComboBox.isPopDown", Boolean.TRUE);
                    }

                    panel.add(cb1);
                    panel.add(cb2);
                    panel.add(cb3);
                    panel.add(cb4);

                    // Change starting selection to check if the position of the
                    // first selection item is in the correct position on screen.
                    cb1.setSelectedIndex(1);
                    cb2.setSelectedIndex(1);
                    cb3.setSelectedIndex(1);
                    cb4.setSelectedIndex(1);

                    frame = new JFrame();
                    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    frame.add(panel);
                    frame.pack();
                    frame.setLocationRelativeTo(null);
                    frame.setVisible(true);
                });

                robot.delay(1000);
                robot.waitForIdle();

                SwingUtilities.invokeAndWait(() -> {
                    cb1Point = cb1.getLocationOnScreen();
                    cb1Width = cb1.getWidth();
                    cb1Height = cb1.getHeight();
                    cb2Point = cb2.getLocationOnScreen();
                    cb2Width = cb2.getWidth();
                    cb2Height = cb2.getHeight();
                    cb3Point = cb3.getLocationOnScreen();
                    cb3Width = cb3.getWidth();
                    cb3Height = cb3.getHeight();
                    cb4Point = cb4.getLocationOnScreen();
                    cb4Width = cb4.getWidth();
                    cb4Height = cb4.getHeight();
                });

                runTestOnComboBox(cb1Point, cb1Width, cb1Height);
                runTestOnComboBox(cb2Point, cb2Width, cb2Height);
                runTestOnComboBox(cb3Point, cb3Width, cb3Height);
                runTestOnComboBox(cb4Point, cb4Width, cb4Height);

                SwingUtilities.invokeAndWait(() -> {
                    cb1Str = cb1.getSelectedItem().toString();
                    cb2Str = cb2.getSelectedItem().toString();
                    cb3Str = cb3.getSelectedItem().toString();
                    cb4Str = cb4.getSelectedItem().toString();
                });

                checkSelection(cb1Str, cb2Str, cb3Str, cb4Str);
            } finally {
                SwingUtilities.invokeAndWait(() -> frame.dispose());
            }
        }
    }

    private static void setLookAndFeel(UIManager.LookAndFeelInfo laf) {
        try {
            UIManager.setLookAndFeel(laf.getClassName());
        } catch (UnsupportedLookAndFeelException ignored){
            System.out.println("Unsupported LookAndFeel: " + laf.getClassName());
        } catch (ClassNotFoundException | InstantiationException |
                IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static void runTestOnComboBox(Point p, int width, int height) {
        if (lafName.equals("Mac OS X")) {
            robot.mouseMove(p.x + width - BUTTON_OFFSET,
                    p.y + (height / 2) + POPUP_OFFSET);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            robot.mouseMove(p.x + (width / 2) - BUTTON_OFFSET,
                    p.y + height + POPUP_OFFSET - 8);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        } else {
            robot.mouseMove(p.x + width - BUTTON_OFFSET,
                    p.y + (height / 2) + POPUP_OFFSET);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            robot.mouseMove(p.x + (width / 2) - BUTTON_OFFSET,
                    p.y + height + POPUP_OFFSET);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        }

    }

    private static void checkSelection(String s1, String s2,
                                       String s3, String s4) {
        if (s1.equals("One") && s2.equals("One")
                && s3.equals("One") && s4.equals("One")) {
            System.out.println(lafName + " Passed");
        } else {
            throw new RuntimeException(lafName + " Failed");
        }
    }
}
