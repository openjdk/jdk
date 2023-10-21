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
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.SwingUtilities;

/**
  * This test is mean to isolate the speed of the JTable.
  * It creates a JTable and performs the following scenarios :
  * 1) Remove columns
  * 2) Add columns
  * 3) Select columns
  *    -Single Selection mode
  *    -Single Selection Interval
  *    -Multiple Selection Intervas
  */

public class TableColTest extends AbstractSwingTest {
   JTable table;
   TableModel dataModel;

   public JComponent getTestComponent() {
       JPanel panel = new JPanel();
       dataModel = new DefaultTableModel() {
         public int getColumnCount(){ return 30; }
         public int getRowCount() { return 40;}
         public Object getValueAt(int row, int col) { return Integer.valueOf(col) ;}
       };

       table = new JTable(dataModel);
       table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
       JScrollPane scrollPane = new JScrollPane(table);
       panel.add(scrollPane);
       return panel;
    }

    public String getTestName() {
       return "Table Column Test";
    }

    public void runTest() {
       testTable(table, 1);
    }

    public void testTable(JTable currentTable, int scrollBy) {
      currentTable.setColumnSelectionAllowed(true);

      // remove column
      System.out.println("1)Removing Columns");
      TableColAdder colRemover = new TableColAdder(currentTable,false);
      while(currentTable.getColumnCount() > 0 ) {
        try {
            SwingUtilities.invokeAndWait(colRemover);
        } catch (Exception e) {System.out.println(e);}
      }

      // add row
      System.out.println("2)Adding Columns");
      TableColAdder colAdder = new TableColAdder(currentTable,true);
      for ( int i = 0 ; i < dataModel.getColumnCount(); i++ ) {

        try {
            colAdder.setModelIndex(i);
            SwingUtilities.invokeAndWait(colAdder);
        } catch (Exception e) {System.out.println(e);}
      }



      // Selection Mode test
      System.out.println("3)Selection Mode Test");
      for ( int m = 0 ; m < 3 ; m ++ ) { // m represents selection mode
          System.out.println("  --------- Selection Mode :" + m );

          TableColScroller colScroll = new TableColScroller(currentTable, scrollBy, m);

          // Column Selection Test
          currentTable.clearSelection();

          for (int i = 0 ;
               i <= currentTable.getColumnCount();
               i= currentTable.getSelectedColumn()) {
              try {
                  SwingUtilities.invokeAndWait(colScroll);
              } catch (Exception e) {System.out.println(e);}
          }
      }
   }

   public static void main(String[] args) {
      runStandAloneTest(new TableColTest());
   }

}


class TableColScroller implements Runnable {
    JTable table;
    int scrollAmount = 1;
    int currentColSelection = 0;

    public TableColScroller(JTable tableToScroll, int scrollBy, int selectionMode) {
      table =  tableToScroll;
      scrollAmount = scrollBy;
      table.setSelectionMode( selectionMode);
    }

    public void run() {

        int endInterval = 0;

        switch ( table.getSelectionModel().getSelectionMode() ) {

           case ListSelectionModel.SINGLE_SELECTION:
               endInterval = currentColSelection;
               table.addColumnSelectionInterval(currentColSelection, endInterval);
               currentColSelection ++;
               break;

           case ListSelectionModel.SINGLE_INTERVAL_SELECTION:
               endInterval = ((currentColSelection + 2) >= table.getColumnCount()-1 ) ?
                 table.getColumnCount()-1 : currentColSelection+2 ;
               table.addColumnSelectionInterval(currentColSelection, endInterval);
               currentColSelection++;
               break;

           case ListSelectionModel.MULTIPLE_INTERVAL_SELECTION:
               endInterval = (currentColSelection >= table.getColumnCount()-1 ) ?
                 table.getColumnCount()-1 :currentColSelection+1;

               table.addColumnSelectionInterval(currentColSelection, endInterval);
               currentColSelection += 3;
               break;

           default:
               break;
     }

     Rectangle cellBound = table.getCellRect(0, endInterval, true);
     table.scrollRectToVisible(cellBound);
     table.repaint();
    }
}

class TableColAdder implements Runnable {
  JTable table;
  int currentRowSelection = 0;
  int index = 0;
  boolean add = true; // false for "remove"
  int rowCount=40;


  public TableColAdder(JTable table, boolean add) {
     this.table =  table;
     this.add = add;
  }

  public void setModelIndex(int i) {
    this.index = i ;
  }

  public void run() {
     Rectangle cellBound;

     if (add) {
       table.addColumn( new TableColumn(index));
       cellBound = table.getCellRect(0,table.getColumnCount()-1,true);
     } else {
       table.removeColumn( table.getColumn( table.getColumnName(0)));
       cellBound = table.getCellRect(0,0,true);
     }
     table.scrollRectToVisible(cellBound);
  }
}
