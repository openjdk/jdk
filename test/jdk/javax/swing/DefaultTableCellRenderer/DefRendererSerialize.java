/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4337898
 * @summary Verifies Serializing DefaultTableCellRenderer doesn't change colors
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual DefRendererSerialize
 */

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

import javax.swing.JFrame;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;

public class DefRendererSerialize {

    static final String INSTRUCTIONS = """
        A JTable is shown
        If the table text is black on white and not black on gray,
        then test passed, otherwise it failed.""";

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        PassFailJFrame.builder()
                .title("DefRendererSerialize Instructions")
                .instructions(INSTRUCTIONS)
                .columns(20)
                .testUI(DefRendererSerialize::createTestUI)
                .build()
                .awaitAndCheck();
    }

    static JFrame createTestUI() {
      String[][] rowData = { {"1-1","1-2","1-3"},
                             {"2-1","2-2","2-3"},
                             {"3-1","3-2","3-3"} };

      String[] columnData = {"Column 1", "Column 2", "Column 3"};

      JTable table = new JTable(rowData, columnData);

      table.setDefaultRenderer(table.getColumnClass(1),
                               new DefaultTableCellRenderer());

      // If this try block is removed, table text remains black on white.

      try {
         ObjectOutputStream ostream = new ObjectOutputStream(new ByteArrayOutputStream());
         ostream.writeObject(table.getDefaultRenderer(table.getColumnClass(1)));
      } catch (IOException ioex) {
         ioex.printStackTrace();
      }

      JFrame frame = new JFrame("DefRendererSerialize");
      frame.add(table);

      frame.pack();
      return frame;
   }
}
