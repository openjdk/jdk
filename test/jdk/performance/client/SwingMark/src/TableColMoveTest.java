/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import java.awt.Rectangle;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.swing.SwingUtilities;

/**
  * This test is mean to isolate the speed of the JTable.
  * It creates a JTable and moves columns.
  */

public class TableColMoveTest extends AbstractSwingTest {
   JTable table;

   public JComponent getTestComponent() {
       JPanel panel = new JPanel();
       TableModel dataModel = new DefaultTableModel() {
         public int getColumnCount(){ return 25; }
         public int getRowCount() { return 20;}
         public Object getValueAt(int row, int col) { return Integer.valueOf(col) ;}
       };

       table = new JTable(dataModel);
       JScrollPane scrollPane = new JScrollPane(table);
       panel.add(scrollPane);
       return panel;
    }

    public String getTestName() {
       return "Table Column Move Test";
    }

    public void runTest() {
       testTable(table, 1);
    }

    public void testTable(JTable currentTable, int scrollBy) {

      TableColMover colmover = new TableColMover(currentTable);
      // Column Selection Test
      currentTable.clearSelection();

      for (int i = 0 ; i < currentTable.getColumnCount(); i++) {
        try {
          SwingUtilities.invokeAndWait(colmover);
        } catch (Exception e) {System.out.println(e);}
      }
   }

   public static void main(String[] args) {
      runStandAloneTest(new TableColMoveTest());
   }
}


class TableColMover implements Runnable {
    JTable table;

    public TableColMover(JTable table) {
        this.table = table;
    }

    public void run() {
       table.moveColumn(0, table.getColumnCount()-1 );
       Rectangle cellBound = table.getCellRect(0, table.getColumnCount()-1, true);
       table.scrollRectToVisible(cellBound);
       table.repaint();
    }
}
