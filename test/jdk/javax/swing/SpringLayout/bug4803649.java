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
 * @bug 4803649
 * @summary setWidth() doesn't work on the container in a SpringLayout
 * @key headful
 */

import java.awt.Dimension;
import java.awt.Robot;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.Spring;
import javax.swing.SpringLayout;
import javax.swing.SwingUtilities;

public class bug4803649 {
    static JFrame fr;
    static JPanel panel;

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                fr = new JFrame("bug4803649");

                panel = new JPanel();
                SpringLayout layout = new SpringLayout();
                panel.setLayout(layout);

                JTextArea textArea = new JTextArea("asasddsa");
                textArea.setPreferredSize(new Dimension(200, 200));
                panel.add(textArea);
                SpringLayout.Constraints cCons = layout.getConstraints(textArea);
                cCons.setX(Spring.constant(10));
                cCons.setY(Spring.constant(10));

                SpringLayout.Constraints pCons = layout.getConstraints(panel);
                pCons.setWidth(Spring.sum(Spring.constant(10),
                        cCons.getConstraint("East")));
                pCons.setHeight(Spring.sum(Spring.constant(10),
                        cCons.getConstraint("South")));

                fr.getContentPane().add(panel);

                fr.setLocationRelativeTo(null);
                fr.pack();
                fr.setVisible(true);
            });

            Robot r = new Robot();
            r.waitForIdle();
            r.delay(1000);

            SwingUtilities.invokeAndWait(() -> {
                Dimension d = panel.getSize();
                if (d.width < 220 || d.height < 220) {
                    throw new RuntimeException("JPanel with the SpringLayout is too small");
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
