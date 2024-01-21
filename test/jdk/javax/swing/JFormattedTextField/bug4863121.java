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
 * @bug 4863121
 * @summary JFormattedTextField's NotifyAction should invoke invalidEdit if
   commit fails
 * @key headful
 * @run main bug4863121
 */

import java.awt.Robot;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.text.Format;
import java.text.DecimalFormat;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class bug4863121 {

    static TestFormattedTextField ftf;
    static JFrame fr;
    static Robot robot;

    private static volatile boolean focused = false;
    private static volatile boolean passed = false;

    public static void main(String[] args) throws Exception {
        try {
            robot = new Robot();
            robot.setAutoDelay(100);
            SwingUtilities.invokeAndWait(() -> {
                fr = new JFrame("Test");
                ftf = new TestFormattedTextField(new DecimalFormat("####"));
                ftf.setText("q");
                fr.getContentPane().add(ftf);

                ftf.addFocusListener(new FocusAdapter() {
                    public void focusGained(FocusEvent e) {
                        focused = true;
                    }
                });
                fr.pack();
                fr.setLocationRelativeTo(null);
                fr.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(() -> {
                ftf.requestFocus();
            });
            robot.waitForIdle();
            robot.delay(500);
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);
            if (!passed) {
                throw new RuntimeException("JFormattedTextField's NotifyAction " +
                        "should invoke invalidEdit if commit fails");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (fr != null) {
                    fr.dispose();
                }
            });
        }
    }

    public static class TestFormattedTextField extends JFormattedTextField {
        public TestFormattedTextField(Format f) {
            super(f);
        }
        protected void invalidEdit() {
            passed = true;
        }
    }
}
