/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4331392
 * @summary Tests if BasicToolBarUI has bogus logic that prevents vertical
 *          toolbars from docking
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4331392
 */

import java.awt.BorderLayout;
import java.awt.Container;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JToolBar;

public class bug4331392 {
   static final String INSTRUCTIONS = """
        Try to dock the toolbar across all the edges of frame. If you succeed,
        then the test PASSES. Otherwise, it FAILS.
    """;

   public static void main(String[] args) throws Exception {
      PassFailJFrame.builder()
              .title("bug4331392 Test Instructions")
              .instructions(INSTRUCTIONS)
              .columns(40)
              .testUI(bug4331392::createUI)
              .build()
              .awaitAndCheck();
   }

   static JFrame createUI() {
      JFrame frame = new JFrame("JToolBar Docking Test");
      Container c = frame.getContentPane();

      JToolBar tbar = new JToolBar(JToolBar.VERTICAL);

      tbar.add(new JButton("A"));
      tbar.add(new JButton("B"));
      tbar.add(new JButton("C"));

      JButton b = new JButton("Hello");
      c.add(b, BorderLayout.CENTER);
      c.add(tbar, BorderLayout.EAST);
      frame.setSize(300, 300);
      return frame;
   }
}
