/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @bug 4196100
  @summary Make sure findComponentAt() only returns visible components.
  @key headful
*/

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.JPanel;

public class FindComponentTest {

    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(() -> {
            FindComponentFrame findComponentAtTest = new FindComponentFrame();

            try {
                if (!findComponentAtTest.didItWork()) {
                    throw new RuntimeException(
                            "findComponentAt() returned non-visible component");
                }
            } finally {
                findComponentAtTest.dispose();
            }
        });
    }
}


class FindComponentFrame extends JFrame {
        public FindComponentFrame() {
            super("FindComponentFrame");
        }

        public boolean didItWork() {
            setTitle("FindComponentTest");
            setSize(new Dimension(200, 200));

            JTabbedPane tabbedpane = new JTabbedPane();
            setContentPane(tabbedpane);

            JPanel panel1 = new JPanel();
            panel1.setName("Panel 1");
            panel1.setLayout(new BorderLayout());
            tabbedpane.add(panel1);
            JPanel subPanel = new JPanel();
            subPanel.setName("Sub-Panel");
            subPanel.setBackground(Color.green);
            panel1.add(subPanel); // add sub panel to 1st tab

            JPanel panel2 = new JPanel();
            panel2.setName("Panel 2");
            tabbedpane.add(panel2);

            tabbedpane.setSelectedIndex(1); // display 2nd tab
            setVisible(true);

            boolean success = tabbedpane.findComponentAt(50,50)
                                        .getName().equals("Panel 2");
            return success;
        }
}
