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
 * @bug 8257810
 * @summary  Verifies if all pages are printed if scrollRectToVisible is set.
 * @run main/manual PrintAllPagesTest
 */
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import javax.swing.WindowConstants;

public class PrintAllPagesTest {

    public static void main(String[] args) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        PrintAllPages test = new PrintAllPages(latch);
        Thread T1 = new Thread(test);
        T1.start();

        // wait for latch to complete
        boolean ret = false;
        try {
            ret = latch.await(5, TimeUnit.MINUTES);
        } catch (InterruptedException ie) {
            throw ie;
        }
        if (!ret) {
            test.dispose();
            throw new RuntimeException(" User has not executed the test");
        }

        if (test.testResult == false) {
            throw new RuntimeException("Only 1st page is printed out of multiple pages");
        }
    }
}

class PrintAllPages implements Runnable {
    static JFrame f;
    static JDialog dialog;
    public boolean testResult = false;
    private final CountDownLatch latch;

    public PrintAllPages(CountDownLatch latch) throws Exception {
        this.latch = latch;
    }

    @Override
    public void run() {
        try {
            createUI();
            printAllPagesTest();
        } catch (Exception ex) {
            if (f != null) {
                f.dispose();
            }
            latch.countDown();
            throw new RuntimeException("createUI Failed: " + ex.getMessage());
        }

    }

    public void dispose() {
        dialog.dispose();
        f.dispose();
    }

    private static void printAllPagesTest() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TableModel dataModel = new AbstractTableModel() {
                @Override
                public int getColumnCount() {
                    return 10;
                }

                @Override
                public int getRowCount() {
                    return 1000;
                }

                @Override
                public Object getValueAt(int row, int col) {
                    return Integer.valueOf(0 == col ? row + 1 : row * col);
                }
            };
            JTable table = new JTable(dataModel) {
                @Override
                public Rectangle getBounds() {
                    Rectangle bounds = super.getBounds();
                    return bounds;
                }
            };
            JScrollPane scrollpane = new JScrollPane(table);
            table.scrollRectToVisible(table.getCellRect(table.getRowCount() - 1, 0, false));	    

	    f = new JFrame("Table test");
            f.add(scrollpane);
            f.setSize(1000, 800);
            f.setLocationRelativeTo(null);
            f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            f.setVisible(true);
	    try { 
                table.print();
	    } catch (Exception e) {}
        });
    }

    private final void createUI() throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {

                String description
                        = " INSTRUCTIONS:\n"
                        + " A JTable will be shown.\n"
                        + " If only 1 page is printed,\n "
                        + " then press fail else press pass";

                dialog = new JDialog();
                dialog.setTitle("textselectionTest");
                JTextArea textArea = new JTextArea(description);
                textArea.setEditable(false);
                final JButton passButton = new JButton("PASS");
                passButton.addActionListener((e) -> {
                    testResult = true;
                    dispose();
                    latch.countDown();
                });
                final JButton failButton = new JButton("FAIL");
                failButton.addActionListener((e) -> {
                    testResult = false;
                    dispose();
                    latch.countDown();
                });
                JPanel mainPanel = new JPanel(new BorderLayout());
                mainPanel.add(textArea, BorderLayout.CENTER);
                JPanel buttonPanel = new JPanel(new FlowLayout());
                buttonPanel.add(passButton);
                buttonPanel.add(failButton);
                mainPanel.add(buttonPanel, BorderLayout.SOUTH);
                dialog.add(mainPanel);
                dialog.pack();
                dialog.setVisible(true);
            }
        });
    }
}
