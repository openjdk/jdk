/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/*
 * @test
 * @bug 4530952
 * @summary Tests that double mouse clicks invoke Event
 * @key headful
 * @run main bug4530952
 */

public class bug4530952 {
    static JFrame frame;
    static JButton btnAction;
    static JComboBox cmbAction;
    static volatile Point loc;
    static volatile Dimension btnSize;

    private static volatile boolean flag;

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            SwingUtilities.invokeAndWait(() -> createTestUI());
            robot.waitForIdle();
            robot.delay(1000);

            // enter some text in combo box
            robot.keyPress(KeyEvent.VK_A);
            robot.keyRelease(KeyEvent.VK_A);
            robot.keyPress(KeyEvent.VK_A);
            robot.keyRelease(KeyEvent.VK_A);
            robot.waitForIdle();
            robot.delay(250);

            // find and click action button
            SwingUtilities.invokeAndWait(() -> {
                loc = btnAction.getLocationOnScreen();
                btnSize = btnAction.getSize();
            });
            robot.waitForIdle();
            robot.delay(250);

            robot.mouseMove(loc.x + btnSize.width / 2,
                    loc.y + btnSize.height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            robot.delay(1000);

            if (!flag) {
                throw new RuntimeException("Failed: button action was not fired");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    public static void createTestUI() {
        frame = new JFrame("bug4530952");
        frame.setLayout(new FlowLayout());

        btnAction = new JButton("Action");
        cmbAction = new JComboBox();

        flag = false;

        ActionListener al = e -> flag = true;
        DocumentListener dl = new DocumentListener() {
            @Override
            public void changedUpdate(DocumentEvent evt) {
                resetButtons();
            }

            @Override
            public void insertUpdate(DocumentEvent evt) {
                resetButtons();
            }

            @Override
            public void removeUpdate(DocumentEvent evt) {
                resetButtons();
            }
        };

        // Add an editable combo box
        cmbAction.setEditable(true);
        frame.add(cmbAction);

        btnAction.setEnabled(false);
        frame.add(btnAction);

        btnAction.addActionListener(al);
        ((JTextField) cmbAction.getEditor().getEditorComponent()).
                getDocument().addDocumentListener(dl);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void resetButtons() {
        int length = ((JTextField) cmbAction.getEditor().getEditorComponent()).
                getDocument().getLength();
        btnAction.setEnabled(length > 0);
    }
}
