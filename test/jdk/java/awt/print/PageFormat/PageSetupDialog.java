/*
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Color;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

/*
 * @test
 * @bug 4197377 4299145 6358747 6574633
 * @key printer
 * @summary Page setup dialog settings
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual PageSetupDialog
 */
public class PageSetupDialog extends Frame implements Printable {
    PrinterJob myPrinterJob;
    PageFormat myPageFormat;
    Label pw, ph, pglm, pgiw, pgrm, pgtm, pgih, pgbm;
    Label myWidthLabel;
    Label myHeightLabel;
    Label myImageableXLabel;
    Label myImageableYLabel;
    Label myImageableRightLabel;
    Label myImageableBottomLabel;
    Label myImageableWidthLabel;
    Label myImageableHeightLabel;
    Label myOrientationLabel;
    Checkbox reverseCB;
    boolean alpha = false;
    boolean reverse = false;

    private static final String INSTRUCTIONS =
            " This test is very flexible and requires much interaction.\n" +
            " If the platform print dialog supports it, adjust orientation\n" +
            " and margins and print pages and compare the results with the request.";

    protected void displayPageFormatAttributes() {
        myWidthLabel.setText("Format Width = " + myPageFormat.getWidth());
        myHeightLabel.setText("Format Height = " + myPageFormat.getHeight());
        myImageableXLabel.setText("Format Left Margin = "
                + myPageFormat.getImageableX());
        myImageableRightLabel.setText("Format Right Margin = "
                + (myPageFormat.getWidth()
                        - (myPageFormat.getImageableX() + myPageFormat.getImageableWidth())));
        myImageableWidthLabel.setText("Format ImageableWidth = "
                + myPageFormat.getImageableWidth());
        myImageableYLabel.setText("Format Top Margin = "
                + myPageFormat.getImageableY());
        myImageableBottomLabel.setText("Format Bottom Margin = "
                + (myPageFormat.getHeight()
                        - (myPageFormat.getImageableY() + myPageFormat.getImageableHeight())));
        myImageableHeightLabel.setText("Format ImageableHeight = "
                + myPageFormat.getImageableHeight());
        int o = myPageFormat.getOrientation();
        if (o == PageFormat.LANDSCAPE && reverse) {
            o = PageFormat.REVERSE_LANDSCAPE;
            myPageFormat.setOrientation(PageFormat.REVERSE_LANDSCAPE);
        } else if (o == PageFormat.REVERSE_LANDSCAPE && !reverse) {
            o = PageFormat.LANDSCAPE;
            myPageFormat.setOrientation(PageFormat.LANDSCAPE);
        }
        myOrientationLabel.setText
                ("Format Orientation = " +
                        (switch (o) {
                            case PageFormat.PORTRAIT -> "PORTRAIT";
                            case PageFormat.LANDSCAPE -> "LANDSCAPE";
                            case PageFormat.REVERSE_LANDSCAPE -> "REVERSE_LANDSCAPE";
                            default -> "<invalid>";
                        }));
        Paper p = myPageFormat.getPaper();
        pw.setText("Paper Width = " + p.getWidth());
        ph.setText("Paper Height = " + p.getHeight());
        pglm.setText("Paper Left Margin = " + p.getImageableX());
        pgiw.setText("Paper Imageable Width = " + p.getImageableWidth());
        pgrm.setText("Paper Right Margin = "
                + (p.getWidth()
                        - (p.getImageableX() + p.getImageableWidth())));
        pgtm.setText("Paper Top Margin = " + p.getImageableY());
        pgih.setText("Paper Imageable Height = " + p.getImageableHeight());
        pgbm.setText("Paper Bottom Margin = "
                + (p.getHeight()
                        - (p.getImageableY() + p.getImageableHeight())));
    }

    public PageSetupDialog() {
        super("Page Dialog Test");
        myPrinterJob = PrinterJob.getPrinterJob();
        myPageFormat = new PageFormat();
        Paper p = new Paper();
        double margin = 1.5 * 72;
        p.setImageableArea(margin, margin,
                p.getWidth() - 2 * margin, p.getHeight() - 2 * margin);
        myPageFormat.setPaper(p);
        Panel c = new Panel();
        c.setLayout(new GridLayout(9, 2, 0, 0));
        c.add(reverseCB = new Checkbox("reverse if landscape"));
        c.add(myOrientationLabel = new Label());
        c.add(myWidthLabel = new Label());
        c.add(pw = new Label());
        c.add(myImageableXLabel = new Label());
        c.add(pglm = new Label());
        c.add(myImageableRightLabel = new Label());
        c.add(pgrm = new Label());
        c.add(myImageableWidthLabel = new Label());
        c.add(pgiw = new Label());
        c.add(myHeightLabel = new Label());
        c.add(ph = new Label());
        c.add(myImageableYLabel = new Label());
        c.add(pgtm = new Label());
        c.add(myImageableHeightLabel = new Label());
        c.add(pgih = new Label());
        c.add(myImageableBottomLabel = new Label());
        c.add(pgbm = new Label());

        reverseCB.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                reverse = e.getStateChange() == ItemEvent.SELECTED;
                int o = myPageFormat.getOrientation();
                if (o == PageFormat.LANDSCAPE ||
                        o == PageFormat.REVERSE_LANDSCAPE) {
                    displayPageFormatAttributes();
                }
            }
        });

        add("Center", c);
        displayPageFormatAttributes();
        Panel panel = new Panel();
        Button pageButton = new Button("Page Setup...");
        pageButton.addActionListener(e -> {
            myPageFormat = myPrinterJob.pageDialog(myPageFormat);
            displayPageFormatAttributes();
        });
        Button printButton = new Button("Print ...");
        printButton.addActionListener(e -> {
            if (myPrinterJob.printDialog()) {
                myPrinterJob.setPrintable(PageSetupDialog.this, myPageFormat);
                alpha = false;
                try {
                    myPrinterJob.print();
                } catch (PrinterException pe) {
                    pe.printStackTrace();
                    PassFailJFrame.forceFail("Test failed because of PrinterException");
                }
            }
        });
        Button printAlphaButton = new Button("Print w/Alpha...");
        printAlphaButton.addActionListener(e -> {
            if (myPrinterJob.printDialog()) {
                myPrinterJob.setPrintable(PageSetupDialog.this, myPageFormat);
                alpha = true;
                try {
                    myPrinterJob.print();
                } catch (PrinterException pe) {
                    pe.printStackTrace();
                    PassFailJFrame.forceFail("Test failed because of PrinterException");
                }
            }
        });
        panel.add(pageButton);
        panel.add(printButton);
        panel.add(printAlphaButton);
        add("South", panel);
        pack();
    }

    @Override
    public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) {
        if (pageIndex > 0) {
            return Printable.NO_SUCH_PAGE;
        }

        Graphics2D g2d = (Graphics2D) graphics;
        g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
        g2d.drawString("ORIGIN(" + pageFormat.getImageableX() + "," +
                pageFormat.getImageableY() + ")", 20, 20);
        g2d.drawString("X THIS WAY", 200, 50);
        g2d.drawString("Y THIS WAY", 60, 200);
        g2d.drawString("Graphics is " + g2d.getClass().getName(), 100, 100);
        g2d.drawRect(0, 0,
                (int) pageFormat.getImageableWidth(),
                (int) pageFormat.getImageableHeight());
        if (alpha) {
            g2d.setColor(new Color(0, 0, 255, 192));
        } else {
            g2d.setColor(Color.blue);
        }
        g2d.drawRect(1, 1,
                (int) pageFormat.getImageableWidth() - 2,
                (int) pageFormat.getImageableHeight() - 2);

        return Printable.PAGE_EXISTS;
    }

    public static void main(String[] args) throws Exception {
        if (PrinterJob.lookupPrintServices().length == 0) {
            throw new RuntimeException("Printer not configured or available.");
        }

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .testTimeOut(10)
                .testUI(PageSetupDialog::new)
                .rows((int) INSTRUCTIONS.lines().count() + 1)
                .columns(45)
                .build()
                .awaitAndCheck();
    }
}
