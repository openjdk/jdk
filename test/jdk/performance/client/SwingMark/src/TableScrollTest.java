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

import java.awt.Graphics;
import java.awt.Rectangle;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.SwingUtilities;

public class TableScrollTest extends AbstractSwingTest {

   static JTable table;
   TableScroller tableScroller;
   JScrollPane scroller;
   static boolean backingStoreEnabled = true;
   static int rendererCount = 0;

   public JComponent getTestComponent() {

      TableModel model = new AbstractTableModel() {
         String[] data = { "1", "2", "3", "4", "5", "6",
            "8", "9", "10", "11" };

         public int getColumnCount() { return 10;}
         public int getRowCount() { return 1000;}
         public Object getValueAt(int row, int col) {
            return data[(row*col)%data.length];
         }
      };

      table = new CountTable(model);
      scroller = new JScrollPane(table);
      tableScroller = new TableScroller(table, 1);

      return scroller;
   }

   public String getTestName() {
      return "Table Scroll";
   }

   public void runTest() {
      for (int i = 0; i < 200; i++) {
         try {
            SwingUtilities.invokeLater( tableScroller );
            rest();
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
   }

   @SuppressWarnings("deprecation")
   public static void main(String[] args) {
       if (args.length > 0) {
          if (args[0].equals("-bs=off")) {
              backingStoreEnabled = false;
              System.out.println("BackingStore is off");
          }
       }

       runStandAloneTest(new TableScrollTest());
       System.out.println("Renderer painted :" + rendererCount);
       System.out.println( "Backing store is:" + ((JViewport)table.getParent()).isBackingStoreEnabled() );
   }

   class TableScroller implements Runnable {

      JTable table;
      int scrollAmount = 1;
      int currentVis = 10;

      public TableScroller(JTable tableToScroll, int scrollBy) {
         table =  tableToScroll;
         scrollAmount = scrollBy;
      }

      public void run() {
         int ensureToSeeRow = currentVis += scrollAmount;
         Rectangle cellBound = table.getCellRect(ensureToSeeRow, 0, true);
         table.scrollRectToVisible(cellBound);
      }
   }

   static class CountRenderer extends DefaultTableCellRenderer {

      public void paint(Graphics g) {
          super.paint(g);
          TableScrollTest.rendererCount++;
      }
   }

   class CountTable extends JTable {
      TableCellRenderer rend = new CountRenderer();

      public CountTable(TableModel tm) {
         super(tm);
      }

      public void paint(Graphics g) {
         super.paint(g);
         paintCount++;
      }

      public TableCellRenderer getCellRenderer(int row, int column) {
         return rend;
      }

      @SuppressWarnings("deprecation")
      public void addNotify() {
         super.addNotify();
         ((JViewport)getParent()).setBackingStoreEnabled(backingStoreEnabled);
      }
   }
}
