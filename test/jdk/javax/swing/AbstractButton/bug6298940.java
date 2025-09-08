/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6298940
 * @key headful
 * @summary Tests that mnemonic keystroke fires an action
 * @library /javax/swing/regtesthelpers
 * @build Util
 * @run main bug6298940
 */

import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.util.concurrent.CountDownLatch;

import javax.swing.ButtonModel;
import javax.swing.DefaultButtonModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import static java.util.concurrent.TimeUnit.SECONDS;

public final class bug6298940 {
    private static JFrame frame;

    private static final CountDownLatch actionEvent = new CountDownLatch(1);

    private static void createAndShowGUI() {
        ButtonModel model = new DefaultButtonModel();
        model.addActionListener(event -> {
            System.out.println("ActionEvent");
            actionEvent.countDown();
        });
        model.setMnemonic('T');

        JButton button = new JButton("Test");
        button.setModel(model);

        frame = new JFrame("bug6298940");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(button);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.toFront();
    }

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();

        SwingUtilities.invokeAndWait(bug6298940::createAndShowGUI);

        robot.waitForIdle();
        robot.delay(500);

        Util.hitMnemonics(robot, KeyEvent.VK_T);

        try {
            if (!actionEvent.await(1, SECONDS)) {
                throw new RuntimeException("Mnemonic didn't fire an action");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
