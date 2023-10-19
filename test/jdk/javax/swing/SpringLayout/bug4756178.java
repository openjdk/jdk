/*
 * Copyright (c) 2002, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4756178
 * @summary SpringLayout:applyDefaults() discards size information when right-aligning.
 * @key headful
 */

import java.awt.Dimension;
import java.awt.Robot;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.Spring;
import javax.swing.SpringLayout;
import javax.swing.SwingUtilities;

public class bug4756178 {
    static JFrame fr;
    static JButton bt;
    static volatile Dimension buttonPreferredSize;
    static volatile Dimension actualSize;

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                fr = new JFrame("bug4756178");
                JPanel p = (JPanel) fr.getContentPane();
                SpringLayout layout = new SpringLayout();
                p.setLayout(layout);

                SpringLayout.Constraints cc = new SpringLayout.Constraints();
                cc.setConstraint("East",
                        Spring.sum(Spring.constant(-20),
                                layout.getConstraint("East", p)));
                cc.setConstraint("South",
                        Spring.sum(Spring.constant(-20),
                                layout.getConstraint("South", p)));

                bt = new JButton();

                buttonPreferredSize = new Dimension(20, 20);
                bt.setPreferredSize(buttonPreferredSize);
                p.add(bt, cc);

                fr.setSize(200, 200);
                fr.setLocationRelativeTo(null);
                fr.setVisible(true);
            });

            Robot robot = new Robot();
            robot.waitForIdle();
            robot.delay(1000);

            SwingUtilities.invokeAndWait(() -> {
                actualSize = bt.getSize();
            });

            if (!buttonPreferredSize.equals(actualSize)) {
                    throw new RuntimeException("Button size is " + actualSize +
                            ", should be " + buttonPreferredSize);
                }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (fr != null) {
                    fr.dispose();
                }
            });
        }
    }
}
