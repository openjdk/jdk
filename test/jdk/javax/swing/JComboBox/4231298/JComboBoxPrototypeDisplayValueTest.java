/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Color;
import java.awt.Component;
import java.awt.Robot;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;

import static javax.swing.UIManager.getInstalledLookAndFeels;

/*
 * @test
 * @key headful
 * @bug 4231298
 * @summary This testcase tests the RFE 4231298 request, JComboBox Custom
 *          Renderer should not be called for non displaying elements if
 *          setPrototypeDisplayValue() has been invoked.
 * @run main JComboBoxPrototypeDisplayValueTest
 */
public class JComboBoxPrototypeDisplayValueTest {

    private static Robot robot;
    private static JFrame frame;
    private static JComboBox buttonComboBox;
    private static volatile int count;

    public static void main(String[] args) throws Exception {
        robot = new Robot();
        robot.setAutoWaitForIdle(true);
        robot.setAutoDelay(200);
        List<String> lafs = Arrays.stream(getInstalledLookAndFeels())
                                  .map(LookAndFeelInfo::getClassName)
                                  .collect(Collectors.toList());
        for (final String laf : lafs) {
            try {
                count = 0;
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

                SwingUtilities
                        .invokeAndWait(() -> buttonComboBox.getPreferredSize());

                robot.waitForIdle();
                if (count > 6) {
                    System.out.println("Test Failed");
                    throw new RuntimeException(
                            "Custom Renderer got called " + count + " times, " +
                            "even after calling setPrototypeDisplayValue(), " +
                            "but the expected maximum is 6 times for " + laf);
                } else {
                    System.out.println("Test Passed for " + laf);
                }
            } finally {
                SwingUtilities.invokeAndWait(
                        JComboBoxPrototypeDisplayValueTest::disposeFrame);
            }
        }
    }

    public static void createUI() {
        Vector data = new Vector(IntStream.rangeClosed(1, 100).boxed()
                                          .map(i -> new JButton("" + i))
                                          .collect(Collectors.toList()));
        buttonComboBox = new JComboBox(data);
        ButtonRenderer renderer = new ButtonRenderer();
        buttonComboBox.setRenderer(renderer);
        buttonComboBox.setMaximumRowCount(25);

        // New method introduced in Java 1.4
        buttonComboBox.setPrototypeDisplayValue(new JButton("111111111"));

        frame = new JFrame();
        JPanel panel = new JPanel();
        panel.add(buttonComboBox);
        frame.getContentPane().add(panel);
        frame.setSize(200, 100);
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

    /**
     * Custom ListCellRenderer used for the drop down portion and the text
     * portion of the ComboBox.
     */
    private static class ButtonRenderer implements ListCellRenderer {
        private final Color selectedBackground;
        private final Color selectedForeground;
        private final Color background;
        private final Color foreground;

        public ButtonRenderer() {
            selectedBackground = Color.BLUE;
            selectedForeground = Color.YELLOW;
            background = Color.GRAY;
            foreground = Color.RED;
        }

        public Component getListCellRendererComponent(JList list, Object value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            JButton button = (JButton) value;
            System.out.println(
                    "getListCellRendererComponent index = " + index + ", " +
                    "isSelected = " + isSelected + ", cellHasFocus = " +
                    cellHasFocus);

            button.setBackground(isSelected ? selectedBackground : background);
            button.setForeground(isSelected ? selectedForeground : foreground);

            count++;
            System.out.println("Value of the Counter is " + count);

            return button;
        }

    }

}
