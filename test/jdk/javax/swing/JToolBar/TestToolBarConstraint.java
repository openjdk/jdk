/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 6664309
 * @key headful
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Verifies docking point of a floating toolbar changes after closing
 * @run main/manual TestToolBarConstraint
*/

import java.awt.BorderLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.GroupLayout;
import javax.swing.SwingUtilities;
import javax.swing.SwingConstants;

public class TestToolBarConstraint {
    static String instructions = "INSTRUCTIONS: \n" +
                "\tA toolbar is docked at EAST side.\n" +
                " \tPlease drag the toolbar in any direction\n" +
                " \tthen release the mouse to make it floating \n" +
                " \tand close the floating toolbar.\n" +
                " \tIf it retains the docking position at EAST, press Pass\n" +
                " \telse if it gets docked at NORTH press Fail.";

    static PassFailJFrame passFailJFrame;
    static JFrame frame;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                createUI();
                passFailJFrame = new PassFailJFrame(instructions);
                PassFailJFrame.addTestWindow(frame);
                PassFailJFrame.positionTestWindow(frame, PassFailJFrame.Position.HORIZONTAL);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        passFailJFrame.awaitAndCheck();
    }

    private static void createUI() {
        frame = new JFrame();
        JToolBar jToolBar1 = new JToolBar();
        JButton jButton1 = new JButton();
        JPanel jPanel1 = new JPanel();

        jToolBar1.setRollover(true);

        jButton1.setText("jButton1");
        jButton1.setFocusable(false);

        jButton1.setHorizontalTextPosition(SwingConstants.CENTER);
        jButton1.setVerticalTextPosition(SwingConstants.BOTTOM);
        jToolBar1.add(jButton1);

        frame.getContentPane().add(jToolBar1, BorderLayout.EAST);

        GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
               jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                            .addGap(0, 340, Short.MAX_VALUE));
        jPanel1Layout.setVerticalGroup(
               jPanel1Layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                            .addGap(0, 300, Short.MAX_VALUE));

        frame.getContentPane().add(jPanel1, BorderLayout.CENTER);

        frame.pack();
        frame.setVisible(true);
    }
}

