/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8223788
 * @summary JSpinner buttons in JColorChooser dialog may capture focus
 *          using TAB Key
 * @run main JSpinnerButtonFocusTest
 */

import java.awt.Robot;
import java.awt.BorderLayout;
import java.awt.ContainerOrderFocusTraversalPolicy;
import java.awt.event.KeyEvent;
import javax.swing.JFrame;
import javax.swing.JSpinner;
import javax.swing.JSpinner.DefaultEditor;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class JSpinnerButtonFocusTest {
    static JFrame frame;
    static Robot robot;
    static JSpinner spinner1, spinner2;
    static DefaultEditor editor2;
    static boolean jTextFieldFocusStatus;

    public static void main(String args[]) throws Exception {

        for (UIManager.LookAndFeelInfo LF : UIManager.getInstalledLookAndFeels()) {
            try {
                UIManager.setLookAndFeel(LF.getClassName());
                robot = new Robot();
                robot.setAutoDelay(50);
                robot.setAutoWaitForIdle(true);

                SwingUtilities.invokeAndWait(() -> {
                    frame = new JFrame();
                    spinner1 = new JSpinner();
                    spinner2 = new JSpinner();

                    frame.setLayout(new BorderLayout());
                    frame.getContentPane().add(spinner1, BorderLayout.NORTH);
                    frame.getContentPane().add(spinner2, BorderLayout.SOUTH);

                    ((DefaultEditor)spinner1.getEditor()).setFocusable(false);
                    spinner1.setFocusable(false);

                    editor2 = (DefaultEditor) spinner2.getEditor();
                    editor2.setFocusable(false);
                    spinner2.setFocusable(false);

                    frame.setFocusTraversalPolicy(
                            new ContainerOrderFocusTraversalPolicy());
                    frame.setFocusTraversalPolicyProvider(true);

                    frame.pack();
                    frame.setVisible(true);
                });

                robot.waitForIdle();
                pressTab(5);
                robot.waitForIdle();

                SwingUtilities.invokeAndWait(() -> {
                    jTextFieldFocusStatus = editor2.getTextField().isFocusOwner();
                });

                if (!jTextFieldFocusStatus) {
                    throw new RuntimeException(
                            "Spinner's Text Field doesn't have focus ");
                }
            } finally {
                if(frame != null){
                    SwingUtilities.invokeAndWait(frame::dispose);
                }
            }
        }
    }

    public static void pressTab(int n) {
        for (int i = 0; i < n; i++) {
            robot.keyPress(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_TAB);
        }
    }
}
