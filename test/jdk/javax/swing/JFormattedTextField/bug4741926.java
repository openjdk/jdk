/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4741926
 * @summary JFormattedTextField/JSpinner always consumes certain key events
 * @key headful
 * @run main bug4741926
 */

import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.Date;
import javax.swing.AbstractAction;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

public class bug4741926 {

    static MyFormattedTextField ftf;
    static JFrame fr;
    static Robot robot;
    static volatile boolean passed_enter = false;
    static volatile boolean passed_escape = false;
    static volatile boolean ftfFocused = false;
    static volatile boolean keyProcessed = false;

    public static void main(String[] args) throws Exception {

        try {
            robot = new Robot();
            robot.setAutoDelay(100);
            SwingUtilities.invokeAndWait(() -> {
                fr = new JFrame("Test");
                ftf = new MyFormattedTextField();
                ftf.setValue("JFormattedTextField");
                JPanel p = (JPanel) fr.getContentPane();
                p.add(ftf);
                ftf.addFocusListener(new FocusAdapter() {
                    public void focusGained(FocusEvent e) {
                        ftfFocused = true;
                    }
                });
                InputMap map = p.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);

                map.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                        "enter-action");
                p.getActionMap().put("enter-action", new AbstractAction() {
                    public void actionPerformed(ActionEvent e) {
                        passed_enter = true;
                        keyProcessed = true;
                    }
                });
                map.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                        "escape-action");
                p.getActionMap().put("escape-action", new AbstractAction() {
                    public void actionPerformed(ActionEvent e) {
                        passed_escape = true;
                        keyProcessed = true;
                    }
                });
                fr.pack();
                fr.setLocationRelativeTo(null);
                fr.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);
            test();
            if (!(passed_enter && passed_escape)) {
                throw new RuntimeException("JFormattedTextField consume " +
                        "Enter/Escape key event");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (fr != null) {
                    fr.dispose();
                }
            });
        }
    }

    public static void test() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ftf.requestFocus();
        });
        robot.delay(500);
        doTest(KeyEvent.VK_ENTER);
        doTest(KeyEvent.VK_ESCAPE);
    }

    static void doTest(int keyCode) throws InterruptedException {
        keyProcessed = false;
        KeyEvent key = new KeyEvent(ftf, KeyEvent.KEY_PRESSED,
                                    new Date().getTime(), 0,
                                    keyCode,
                                    KeyEvent.CHAR_UNDEFINED);
        ftf.processKey(key);
    }

    static class MyFormattedTextField extends JFormattedTextField {
        public void processKey(KeyEvent e) {
            processKeyEvent(e);
        }
    }
}
