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

import java.awt.Robot;
import java.awt.event.InputEvent;
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
    private static JComboBox cb1, cb2;
    private static String lafName;

    private static final int BUTTON_OFFSET = 10;
    private static final int POPUP_OFFSET = 5;

    public static void main(String[] args) throws InterruptedException, InvocationTargetException {
        try {
            robot = new Robot();
            robot.setAutoDelay(100);
            robot.setAutoWaitForIdle(true);

            for (UIManager.LookAndFeelInfo laf : UIManager.getInstalledLookAndFeels()) {
                lafName = laf.getName().equals("CDE/Motif") ? "Motif" : laf.getName();
                SwingUtilities.invokeAndWait(() -> {
                    setLookAndFeel(laf);
                    panel = new JPanel();
                    String[] comboStrings = {"One", "Two", "Three"};
                    cb1 = new JComboBox(comboStrings);
                    cb1.setEditable(true);
                    cb1.setBorder(BorderFactory.createTitledBorder(
                            "Editable JComboBox"));

                    cb2 = new JComboBox(comboStrings);
                    cb2.setEditable(true);

                    panel.add(cb1);
                    panel.add(cb2);

                    frame = new JFrame();
                    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    frame.add(panel);
                    frame.pack();
                    frame.setLocationRelativeTo(null);
                    frame.setVisible(true);
                });

                // Change starting selection to check if the position of the
                // first selection item is in the correct position on screen.
                cb1.setSelectedIndex(1);
                cb2.setSelectedIndex(1);

                runTestOnComboBox(cb1);
                runTestOnComboBox(cb2);

                checkSelection(cb1, cb2);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            SwingUtilities.invokeAndWait(() -> frame.dispose());
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

    private static void runTestOnComboBox(JComboBox cb) {
        robot.mouseMove(cb.getLocationOnScreen().x + cb.getWidth()
                - BUTTON_OFFSET, cb.getLocationOnScreen().y
                + POPUP_OFFSET);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseMove(cb.getLocationOnScreen().x
                        + (cb.getWidth() / 2) - BUTTON_OFFSET,
                cb.getLocationOnScreen().y + cb.getHeight()
                        + POPUP_OFFSET);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    private static void checkSelection(JComboBox c1, JComboBox c2)
            throws InterruptedException, InvocationTargetException {
        if (c1.getSelectedItem().toString().equals("One")
                && c2.getSelectedItem().toString().equals("One")) {
            System.out.println(lafName + " Passed");
            SwingUtilities.invokeAndWait(() -> frame.dispose());
        } else {
            SwingUtilities.invokeAndWait(() -> frame.dispose());
            throw new RuntimeException(lafName + " Failed");
        }
    }
}