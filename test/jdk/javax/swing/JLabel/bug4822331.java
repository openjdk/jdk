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
 * @bug 4822331
 * @summary setLaberFor does not transfer focus to the JSpinner editor
 * @library /test/lib
 * @key headful
 * @run main bug4822331
 */

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.FlowLayout;
import java.awt.Robot;
import javax.swing.JButton;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import jdk.test.lib.Platform;

public class bug4822331 {

    static JFrame fr;
    static JButton button;
    static JSpinner spinner;
    static volatile boolean tfFocused = false;
    static volatile boolean passed = false;

    public static void main(String []args) throws Exception {
        bug4822331 test = new bug4822331();
        test.init();
    }

    public void init() throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                fr = new JFrame("Test");
                fr.getContentPane().setLayout(new FlowLayout());

                button = new JButton("Button");
                fr.getContentPane().add(button);

                spinner = new JSpinner();
                JLabel spinnerLabel = new JLabel("spinner");
                spinnerLabel.setDisplayedMnemonic(KeyEvent.VK_S);
                spinnerLabel.setLabelFor(spinner);
                fr.getContentPane().add(spinnerLabel);
                fr.getContentPane().add(spinner);

                JSpinner.DefaultEditor editor =
                        (JSpinner.DefaultEditor) spinner.getEditor();
                JFormattedTextField ftf = editor.getTextField();
                ftf.addFocusListener(new FocusAdapter() {
                    public void focusGained(FocusEvent e) {
                        passed = true;
                    }
                });
                fr.pack();
                fr.setVisible(true);
            });
            start();
            if ( !passed ) {
                throw new RuntimeException("The activation of spinner's " +
                        "mnemonic didn't focus the editor component.");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (fr != null) {
                    fr.dispose();
                }
            });
        }
    }

    public void start() throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(50);
        robot.delay(1000);
        robot.waitForIdle();
        button.requestFocus();
        if (Platform.isOSX()) {
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_ALT);
            robot.keyPress(KeyEvent.VK_S);
            robot.keyRelease(KeyEvent.VK_S);
            robot.keyRelease(KeyEvent.VK_ALT);
            robot.keyRelease(KeyEvent.VK_CONTROL);
        } else {
            robot.keyPress(KeyEvent.VK_ALT);
            robot.keyPress(KeyEvent.VK_S);
            robot.keyRelease(KeyEvent.VK_S);
            robot.keyRelease(KeyEvent.VK_ALT);
        }
    }
}
