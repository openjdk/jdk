/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4122109
 * @summary Ensure SwingUtilities.getDeepestComponentAt() works correctly
 *    (in this particular case, with JTabbedPane)
 * @key headful
 * @run main GetComponentAtTest
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Robot;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

public class GetComponentAtTest {
   static JFrame f;

   public static void main(String[] args) throws Exception {
      try {
         Robot robot = new Robot();
         SwingUtilities.invokeAndWait(() -> {
            f = new JFrame("GetComponentAtTest");
            JTabbedPane tabbedpane = new JTabbedPane();
            f.getContentPane().add(tabbedpane, BorderLayout.CENTER);

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

            f.setSize(150, 150);
            f.setVisible(true);

            robot.delay(1000);

            tabbedpane.setSelectedIndex(1); // display 2nd tab
            tabbedpane.invalidate();
            tabbedpane.validate();
            if (SwingUtilities.getDeepestComponentAt(tabbedpane, 50, 50) != panel2) {
               throw new RuntimeException("SwingUtilities.getDeepestComponentAt() " +
                       "returned incorrect component! (1)");
            }

            tabbedpane.setSelectedIndex(0); // display 1st tab
            tabbedpane.invalidate();
            tabbedpane.validate();
            if (SwingUtilities.getDeepestComponentAt(tabbedpane, 50, 50) != subPanel) {
               throw new RuntimeException("SwingUtilities.getDeepestComponentAt() " +
                       "returned incorrect component! (2)");
            }
         });
      } finally {
         SwingUtilities.invokeAndWait(() -> {
            if (f != null) {
               f.dispose();
            }
         });
      }

   }
}
