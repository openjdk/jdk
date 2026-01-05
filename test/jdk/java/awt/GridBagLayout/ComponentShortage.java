/*
 * Copyright (c) 2008, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4238932
 * @summary JTextField in gridBagLayout does not properly set MinimumSize
 * @key headful
 * @run main ComponentShortage
 */

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Robot;
import javax.swing.JFrame;
import javax.swing.JTextField;

public class ComponentShortage {
    static final int WIDTH_REDUCTION = 50;
    static JFrame frame;
    static JTextField jtf;
    static volatile Dimension size;
    static volatile Dimension fSize;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        try {
            EventQueue.invokeAndWait(() -> {
                frame = new JFrame();
                frame.setLayout(new GridBagLayout());
                GridBagConstraints gBC = new GridBagConstraints();

                gBC.gridx = 1;
                gBC.gridy = 0;
                gBC.gridwidth = 1;
                gBC.gridheight = 1;
                gBC.weightx = 1.0;
                gBC.weighty = 0.0;
                gBC.fill = GridBagConstraints.NONE;
                gBC.anchor = GridBagConstraints.NORTHWEST;
                jtf = new JTextField(16);
                frame.add(jtf, gBC);
                frame.pack();
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);

            EventQueue.invokeAndWait(() -> {
                size = jtf.getSize();
            });
            System.out.println("TextField size before Frame's width reduction : " + size);

            EventQueue.invokeAndWait(() -> {
                frame.setSize(frame.getSize().width - WIDTH_REDUCTION, frame.getSize().height);
            });
            frame.repaint();

            EventQueue.invokeAndWait(() -> {
                size = jtf.getSize();
                fSize = frame.getSize();
            });
            System.out.println("TextField size after Frame's width reduction : " + size);

            if (size.width < fSize.width - WIDTH_REDUCTION) {
                throw new RuntimeException("Width of JTextField is too small to be visible.");
            }
            System.out.println("Test passed.");
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
