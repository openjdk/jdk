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

/*
 * @test
 * @bug 4979794
 * @summary A component is sometimes the next component for itself in focus policy.
 * @key headful
 */

import java.awt.Component;
import java.awt.Container;
import java.awt.FocusTraversalPolicy;
import java.awt.Robot;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class bug4979794 {
    static JFrame fr;
    static JButton btn1;
    static JButton btn2;

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                fr = new JFrame("bug4979794");
                fr.getContentPane().setLayout(null);

                JPanel p = new JPanel();
                p.setLayout(null);
                fr.getContentPane().add(p);

                btn1 = new JButton("Button 1");
                btn1.setBounds(0, 0, 200, 200);

                btn2 = new JButton("Button 2");
                btn2.setBounds(0, 0, 200, 200);

                p.add(btn1);
                p.add(btn2);
                p.setSize(200, 200);

                fr.setLocationRelativeTo(null);
                fr.setSize(300, 300);
                fr.setVisible(true);
            });

            Robot r = new Robot();
            r.waitForIdle();
            r.delay(1000);

            SwingUtilities.invokeAndWait(() -> {
                Container root = btn1.getFocusCycleRootAncestor();
                FocusTraversalPolicy policy = root.getFocusTraversalPolicy();
                Component next1 = policy.getComponentAfter(fr, btn1);
                Component next2 = policy.getComponentAfter(fr, btn2);
                if (next1 == next2) {
                    throw new RuntimeException("btn1 and btn2 have the same next Component.");
                }
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (fr != null) {
                    fr.dispose();
                }
            });
        }
    }
}
