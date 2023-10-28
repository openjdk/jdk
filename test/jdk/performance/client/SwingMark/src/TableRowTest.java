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

import java.util.ResourceBundle;
import java.util.Vector;
import java.awt.Graphics;
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
  * 1) Remove rows
  * 2) Add rows
  * 3) Select rows
  *    -Single Selection mode
  *    -Single Selection Interval
  *    -Multiple Selection Intervas
  */

public class TableRowTest extends AbstractSwingTest {

    JTable table;
    DefaultTableModel dataModel;
    static boolean backingStoreEnabled = true;

    final String[] names = {"First Name", "Last Name", "Favorite Color",
        "Favorite Number", "Vegetarian"};

    Object[][] data = {{}};

    public JComponent getTestComponent() {
       loadBundle();
        JPanel panel = new JPanel();
        dataModel = new DefaultTableModel(data, names);

        table = new CountTable(dataModel);
        JScrollPane scrollPane = new JScrollPane(table);

        if (SwingMark.useBlitScrolling) {
           scrollPane.getViewport().putClientProperty("EnableWindowBlit", Boolean.TRUE);
        }

        panel.add(scrollPane);
        return panel;
    }

    private void loadBundle() {
      ResourceBundle bundle = ResourceBundle.getBundle("resources.TableRowTest");
      data = (Object[][])bundle.getObject("TableData");
    }

    public String getTestName() {
        return "Table Rows";
    }

    public void runTest() {
        testTable(table, 1);
    }

    public void testTable(JTable currentTable, int scrollBy) {
        // remove row
        TableRowAdder rowRemover = new TableRowAdder(currentTable, data, false);
        while (dataModel.getRowCount() > 0 ) {
            try {
                SwingUtilities.invokeLater(rowRemover);
                rest();
            }
            catch (Exception e) {e.printStackTrace();}
        }

        // add row
        TableRowAdder rowAdder = new TableRowAdder(currentTable, data, true);
        while (dataModel.getRowCount() < data.length ) {
            try {
                SwingUtilities.invokeAndWait(rowAdder);
            }
            catch (Exception e) {e.printStackTrace();}
        }



        // Selection Test
        for ( int m = 0 ; m < 3 ; m ++ ) { // m represents selection mode
            //  System.out.println("  --------- Selection Mode :" + m );

            TableScroller scroll = new TableScroller(currentTable, scrollBy, m);

            currentTable.clearSelection();
            for (int i = 0 ; i < currentTable.getRowCount()-1; i++ ) {
                try {
                    SwingUtilities.invokeAndWait(scroll);
                }
                catch (Exception e) {e.printStackTrace();}
            }
        }

    }

    public static void main(String[] args) {
       if (args.length > 0) {
          if (args[0].equals("-bs=off")) {
             backingStoreEnabled = false;
             System.out.println("BackingStore is off");
          }
       }
        runStandAloneTest(new TableRowTest());
    }

    class CountTable extends JTable {
        public CountTable(TableModel tm) {
            super(tm);
        }
        public void paint(Graphics g) {
            super.paint(g);
            paintCount++;
        }
    }

}


class TableScroller implements Runnable {
    JTable table;
    int scrollAmount = 1;
    int currentRowSelection = 0;


    public TableScroller(JTable tableToScroll, int scrollBy, int selectionMode) {
        table = tableToScroll;
        scrollAmount = scrollBy;
        table.setSelectionMode( selectionMode);
    }

    public void run() {
        int ensureToSeeRow = 0;

        switch ( table.getSelectionModel().getSelectionMode() ) {
            case ListSelectionModel.SINGLE_SELECTION:
                table.addRowSelectionInterval(currentRowSelection, currentRowSelection);
                currentRowSelection++;
                ensureToSeeRow = currentRowSelection;
                break;
            case ListSelectionModel.SINGLE_INTERVAL_SELECTION:
                currentRowSelection = Math.min(currentRowSelection, table.getRowCount()-1);
                int maxRow = table.getRowCount()-1;
                table.addRowSelectionInterval(currentRowSelection,
                           Math.min(currentRowSelection+5, maxRow));
                currentRowSelection++;
                ensureToSeeRow = table.getSelectionModel().getAnchorSelectionIndex() + 4;
                break;
            case ListSelectionModel.MULTIPLE_INTERVAL_SELECTION:
                table.addRowSelectionInterval(Math.min(currentRowSelection, table.getRowCount()-1),
                                              Math.min(currentRowSelection+3, table.getRowCount()-1));
                currentRowSelection = currentRowSelection + 5;
                ensureToSeeRow = table.getSelectionModel().getAnchorSelectionIndex() + 3;
                break;
            default:
                break;
        }

        Rectangle cellBound = table.getCellRect(ensureToSeeRow, 0, true);
        table.scrollRectToVisible(cellBound);
    }
}

class TableRowAdder implements Runnable {
    JTable table;
    int currentRowSelection = 0;
    Vector dataVector;
    int index = 0;
    Object [][] data;
    boolean add = true; // false for "remove"


    public TableRowAdder(JTable table, Object[][] data, boolean add) {
        this.table = table;
        this.data = data;
        this.add = add;
    }

    public void run() {
        DefaultTableModel model = (DefaultTableModel)table.getModel();
        Rectangle cellBound;

        if ( add ) {
            model.addRow(data[index]);
            index++;
            cellBound = table.getCellRect(table.getRowCount()-1, 0, true);
        }
        else {
            model.removeRow(0 );
            cellBound = table.getCellRect(0,0,true);
        }

        table.scrollRectToVisible(cellBound);
    }
}
