/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @key headful
 * @bug 8154043 8172509
 * @summary Fields not reachable anymore by tab-key, because of new tabbing
 * behaviour of radio button groups.
 * @run main ButtonGroupLayoutTraversalTest
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.Robot;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.JPanel;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JRadioButton;
import javax.swing.JToggleButton;
import javax.swing.LayoutFocusTraversalPolicy;
import javax.swing.UnsupportedLookAndFeelException;

public class ButtonGroupLayoutTraversalTest {

    static int nx = 3;
    static int ny = 3;

    static int focusCnt[] = new int[nx * ny];
    private static JFrame window;

    public static void main(String[] args) throws Exception {

        try {
            SwingUtilities.invokeAndWait(() -> changeLAF());
            SwingUtilities.invokeAndWait(() -> initLayout(nx, ny));
            Robot robot = new Robot();
            robot.setAutoDelay(100);
            robot.waitForIdle();
            robot.delay(1000);

            for (int i = 0; i < nx * ny - nx * ny / 2 - 1; i++) {
                robot.keyPress(KeyEvent.VK_RIGHT);
                robot.keyRelease(KeyEvent.VK_RIGHT);
                robot.waitForIdle();
            }

            for (int i = 0; i < nx * ny / 2; i++) {
                robot.keyPress(KeyEvent.VK_TAB);
                robot.keyRelease(KeyEvent.VK_TAB);
                robot.waitForIdle();
            }

            robot.delay(200);

            for (int i = 0; i < nx * ny; i++) {
                if (focusCnt[i] < 1) {
                    throw new RuntimeException("Component " + i
                        + " is not reachable in the forward focus cycle");
                } else if (focusCnt[i] > 1) {
                    throw new RuntimeException("Component " + i
                        + " got focus more than once in the forward focus cycle");
                }
            }

            for (int i = 0; i < nx * ny / 2; i++) {
                robot.keyPress(KeyEvent.VK_SHIFT);
                robot.keyPress(KeyEvent.VK_TAB);
                robot.keyRelease(KeyEvent.VK_TAB);
                robot.keyRelease(KeyEvent.VK_SHIFT);
                robot.waitForIdle();
            }

            for (int i = 0; i < nx * ny - nx * ny / 2 - 1; i++) {
                robot.keyPress(KeyEvent.VK_LEFT);
                robot.keyRelease(KeyEvent.VK_LEFT);
                robot.waitForIdle();
            }

            robot.keyPress(KeyEvent.VK_SHIFT);
            robot.keyPress(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_SHIFT);
            robot.waitForIdle();

            robot.delay(200);

            for (int i = 0; i < nx * ny; i++) {
                if (focusCnt[i] < 2) {
                    throw new RuntimeException("Component " + i
                        + " is not reachable in the backward focus cycle");
                } else if (focusCnt[i] > 2) {
                    throw new RuntimeException("Component " + i
                        + " got focus more than once in the backward focus cycle");
                }
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (window != null) {
                    window.dispose();
                }
            });
        }
    }

    private static void changeLAF() {
        String currentLAF = UIManager.getLookAndFeel().toString();
        currentLAF = currentLAF.toLowerCase();
        if (currentLAF.contains("aqua") || currentLAF.contains("nimbus")) {
            try {
                UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
            } catch (ClassNotFoundException
                    | IllegalAccessException
                    | InstantiationException
                    | UnsupportedLookAndFeelException ex) {
                ex.printStackTrace();
            }
        }
    }

    public static void initLayout(int nx, int ny) {
        window = new JFrame("Test");
        window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        JPanel rootPanel = new JPanel();
        rootPanel.setLayout(new BorderLayout());
        JPanel formPanel = new JPanel(new GridLayout(nx, ny));
        formPanel.setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());
        formPanel.setFocusCycleRoot(true);
        ButtonGroup radioButtonGroup = new ButtonGroup();
        for (int i = 0; i < nx * ny; i++) {
            JToggleButton comp;
            if (i % 2 == 0) {
                comp = new JRadioButton("Grouped component");
                radioButtonGroup.add(comp);
            } else {
                comp = new JRadioButton("Single component");
            }
            formPanel.add(comp);
            int fi = i;
            comp.setBackground(Color.red);
            comp.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    focusCnt[fi]++;
                    if (focusCnt[fi] == 1) {
                        ((JComponent) e.getSource())
                                .setBackground(Color.yellow);
                    } else if (focusCnt[fi] == 2) {
                        ((JComponent) e.getSource())
                                .setBackground(Color.green);
                    } else {
                        ((JComponent) e.getSource())
                                .setBackground(Color.red);
                    }
                }
            });
        }
        rootPanel.add(formPanel, BorderLayout.CENTER);
        window.add(rootPanel);
        window.setLocationRelativeTo(null);
        window.pack();
        window.setVisible(true);
    }
}
