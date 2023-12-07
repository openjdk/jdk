/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4517214
 * @summary Tests that comboBox is not doubleheight if editable and has TitledBorder
*/

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Robot;

public class TestComboBoxHeight {
    private static String[] data = { "Ten", "Twenty", "Forty-three" };
    private static JFrame jframe;
    private static int heightCombo1, heightCombo2;
    private static JComboBox combo1, combo2;

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(100);
            SwingUtilities.invokeAndWait(() -> {
                jframe = new JFrame();

                GridBagLayout gridBag = new GridBagLayout();
                GridBagConstraints c = new GridBagConstraints();
                JPanel p = new JPanel(gridBag);
                c.fill = GridBagConstraints.NONE;

                // fine-looking combo
                combo1 = new JComboBox(data);
                combo1.setEditable(true);
                gridBag.setConstraints(combo1, c);
                p.add(combo1);

                // combo has border
                combo2 = new JComboBox(data);
                combo2.setEditable(true);
                combo2.setBorder(BorderFactory.
                        createTitledBorder("Combo Border"));
                gridBag.setConstraints(combo2, c);
                p.add(combo2);

                jframe.setContentPane(p);
                jframe.setLocationRelativeTo(null);
                jframe.setSize(400, 200);
                jframe.setDefaultCloseOperation(
                        WindowConstants.DISPOSE_ON_CLOSE);
                jframe.setVisible(true);
            });

            robot.delay(1000);
            robot.waitForIdle();
            SwingUtilities.invokeAndWait(() -> {
                heightCombo1 = combo1.getHeight();
                heightCombo2 = combo2.getHeight();
            });

            if (heightCombo2 >= heightCombo1 * 2) {
                throw new RuntimeException("combo boxes with border " +
                 " should not have double height compared to normal combobox");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (jframe != null) {
                    jframe.dispose();
                }
            });
        }
    }
}
