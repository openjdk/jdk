/*
 * Copyright (c) 2004, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Robot;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

/*
 * @test
 * @key headful
 * @bug 4529206
 * @summary JToolBar - setFloating does not work correctly
 * @run main bug4529206
 */

public class bug4529206 {
    static JFrame frame;
    static JToolBar jToolBar1;
    static JButton jButton1;

    private static void test() {
        frame = new JFrame();
        JPanel jPanFrame = (JPanel) frame.getContentPane();
        jPanFrame.setLayout(new BorderLayout());
        frame.setSize(new Dimension(200, 100));
        frame.setTitle("Test Floating Toolbar");
        jToolBar1 = new JToolBar();
        jButton1 = new JButton("Float");
        jPanFrame.add(jToolBar1, BorderLayout.NORTH);
        JTextField tf = new JTextField("click here");
        jPanFrame.add(tf);
        jToolBar1.add(jButton1, null);
        jButton1.addActionListener(e -> buttonPressed());

        frame.setUndecorated(true);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void makeToolbarFloat() {
        javax.swing.plaf.basic.BasicToolBarUI ui = (javax.swing.plaf.basic.BasicToolBarUI) jToolBar1.getUI();
        if (!ui.isFloating()) {
            ui.setFloatingLocation(100, 100);
            ui.setFloating(true, jToolBar1.getLocation());
        }
    }

    private static void buttonPressed() {
        makeToolbarFloat();
    }

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> test());
            Robot robot = new Robot();
            robot.setAutoWaitForIdle(true);
            robot.delay(1000);

            SwingUtilities.invokeAndWait(() -> makeToolbarFloat());
            robot.delay(300);

            SwingUtilities.invokeAndWait(() -> {
                if (frame.isFocused()) {
                    throw
                      new RuntimeException("setFloating does not work correctly");
                }
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
