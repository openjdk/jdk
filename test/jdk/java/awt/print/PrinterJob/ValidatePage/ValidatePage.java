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
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

import jtreg.SkippedException;

/*
 * @test
 * @bug 4252108 6229507
 * @key printer
 * @summary PrinterJob.validatePage() is unimplemented.
 * @library /java/awt/regtesthelpers
 * @library /test/lib
 * @build PassFailJFrame
 * @build jtreg.SkippedException
 * @run main/manual ValidatePage
 */
public class ValidatePage extends Frame implements Printable {

    PrinterJob myPrinterJob;
    PageFormat myPageFormat;
    Label pw, ph, pglm, pgrm, pgiw, pgih, pgtm, pgbm;
    TextField tpw, tph, tpglm, tpgtm, tpgiw, tpgih;
    Label myWidthLabel;
    Label myHeightLabel;
    Label myImageableXLabel;
    Label myImageableYLabel;
    Label myImageableRightLabel;
    Label myImageableBottomLabel;
    Label myImageableWidthLabel;
    Label myImageableHeightLabel;
    Label myOrientationLabel;

    protected void displayPageFormatAttributes() {
        myWidthLabel.setText("Format Width = " + drnd(myPageFormat.getWidth()));
        myHeightLabel.setText("Format Height = " + drnd(myPageFormat.getHeight()));
        myImageableXLabel.setText
                ("Format Left Margin = " + drnd(myPageFormat.getImageableX()));
        myImageableRightLabel.setText
                ("Format Right Margin = " + drnd(myPageFormat.getWidth() -
                        (myPageFormat.getImageableX() + myPageFormat.getImageableWidth())));
        myImageableWidthLabel.setText
                ("Format ImageableWidth = " + drnd(myPageFormat.getImageableWidth()));
        myImageableYLabel.setText
                ("Format Top Margin = " + drnd(myPageFormat.getImageableY()));
        myImageableBottomLabel.setText
                ("Format Bottom Margin = " + drnd(myPageFormat.getHeight() -
                        (myPageFormat.getImageableY() + myPageFormat.getImageableHeight())));
        myImageableHeightLabel.setText
                ("Format ImageableHeight = " + drnd(myPageFormat.getImageableHeight()));
        int o = myPageFormat.getOrientation();
        myOrientationLabel.setText
                ("Format Orientation = " +
                        (o == PageFormat.PORTRAIT ? "PORTRAIT" :
                                o == PageFormat.LANDSCAPE ? "LANDSCAPE" :
                                        o == PageFormat.REVERSE_LANDSCAPE ? "REVERSE_LANDSCAPE" :
                                                "<invalid>"));
        Paper p = myPageFormat.getPaper();
        pw.setText("Paper Width = " + drnd(p.getWidth()));
        ph.setText("Paper Height = " + drnd(p.getHeight()));
        pglm.setText("Paper Left Margin = " + drnd(p.getImageableX()));
        pgiw.setText("Paper Imageable Width = " + drnd(p.getImageableWidth()));
        pgih.setText("Paper Imageable Height = " + drnd(p.getImageableHeight()));

        pgrm.setText("Paper Right Margin = " +
                drnd(p.getWidth() - (p.getImageableX() + p.getImageableWidth())));
        pgtm.setText("Paper Top Margin = " + drnd(p.getImageableY()));
        pgbm.setText("Paper Bottom Margin = " +
                drnd(p.getHeight() - (p.getImageableY() + p.getImageableHeight())));
    }

    static String drnd(double d) {
        d = d * 10.0 + 0.5;
        d = Math.floor(d) / 10.0;
        String ds = Double.toString(d);
        int decimal_pos = ds.indexOf(".");
        int len = ds.length();
        if (len > decimal_pos + 2) {
            return ds.substring(0, decimal_pos + 2);
        } else {
            return ds;
        }
    }

    public ValidatePage() {
        super("Validate Page Test");
        myPrinterJob = PrinterJob.getPrinterJob();
        myPageFormat = new PageFormat();
        Paper p = new Paper();
        p.setSize(28 * 72, 21.5 * 72);
        myPageFormat.setPaper(p);
        setLayout(new FlowLayout());
        Panel pfp = new Panel();
        pfp.setLayout(new GridLayout(9, 1, 0, 0));
        pfp.add(myOrientationLabel = new Label());
        pfp.add(myWidthLabel = new Label());
        pfp.add(myImageableXLabel = new Label());
        pfp.add(myImageableRightLabel = new Label());
        pfp.add(myImageableWidthLabel = new Label());
        pfp.add(myHeightLabel = new Label());
        pfp.add(myImageableYLabel = new Label());
        pfp.add(myImageableBottomLabel = new Label());
        pfp.add(myImageableHeightLabel = new Label());

        add(pfp);

        Panel pp = new Panel();
        pp.setLayout(new GridLayout(8, 1, 0, 0));
        pp.add(pw = new Label());
        pp.add(pglm = new Label());
        pp.add(pgtm = new Label());
        pp.add(ph = new Label());
        pp.add(pgiw = new Label());
        pp.add(pgih = new Label());
        pp.add(pgrm = new Label());
        pp.add(pgbm = new Label());

        add(pp);

        Panel epp = new Panel();
        epp.setLayout(new GridLayout(6, 2, 0, 0));

        epp.add(new Label("Page width:"));
        epp.add(tpw = new TextField());
        epp.add(new Label("Page height:"));
        epp.add(tph = new TextField());
        epp.add(new Label("Left Margin:"));
        epp.add(tpglm = new TextField());
        epp.add(new Label("Top margin:"));
        epp.add(tpgtm = new TextField());
        epp.add(new Label("Imageable Wid:"));
        epp.add(tpgiw = new TextField());
        epp.add(new Label("Imageable Hgt:"));
        epp.add(tpgih = new TextField());

        add(epp);
        displayPageFormatAttributes();

        Panel panel = new Panel();
        Button defButton = new Button("Default Page");
        defButton.addActionListener(e -> {
            myPageFormat = myPrinterJob.defaultPage();
            displayPageFormatAttributes();
        });

        Button pageButton = new Button("Page Setup..");
        pageButton.addActionListener(e -> {
            myPageFormat = myPrinterJob.pageDialog(myPageFormat);
            displayPageFormatAttributes();
        });
        Button printButton = new Button("Print");
        printButton.addActionListener(e -> {
            try {
                myPrinterJob.setPrintable(ValidatePage.this, myPageFormat);
                myPrinterJob.print();
            } catch (PrinterException pe) {
                pe.printStackTrace();
            }
        });

        Button chooseButton = new Button("Printer..");
        chooseButton.addActionListener(e -> myPrinterJob.printDialog());

        Button validateButton = new Button("Validate Page");
        validateButton.addActionListener(e -> {
            myPageFormat = myPrinterJob.validatePage(myPageFormat);
            displayPageFormatAttributes();
        });
        Button setButton = new Button("Set Paper");
        setButton.addActionListener(e -> {
            try {
                Paper p1 = new Paper();
                double pwid = Double.parseDouble(tpw.getText());
                double phgt = Double.parseDouble(tph.getText());
                double pimx = Double.parseDouble(tpglm.getText());
                double pimy = Double.parseDouble(tpgtm.getText());
                double pimwid = Double.parseDouble(tpgiw.getText());
                double pimhgt = Double.parseDouble(tpgih.getText());
                p1.setSize(pwid, phgt);
                p1.setImageableArea(pimx, pimy, pimwid, pimhgt);
                myPageFormat.setPaper(p1);
                displayPageFormatAttributes();
            } catch (NumberFormatException nfe) {
                nfe.printStackTrace();
            }
        });
        panel.add(setButton);
        panel.add(defButton);
        panel.add(pageButton);
        panel.add(chooseButton);
        panel.add(validateButton);
        panel.add(printButton);
        add(panel);
        TextArea ta = new TextArea(10, 45);
        String ls = System.getProperty("line.Separator", "\n");
        ta.setText(
                "When validating a page, the process is 1st to find the closest matching " + ls +
                "paper size, next to make sure the requested imageable area fits within " + ls +
                "the printer's imageable area for that paper size. Finally the top and " + ls +
                "left margins will be shrunk if they are too great for the adjusted " + ls +
                "imageable area to fit at that position. They will shrink by the minimum" + ls +
                "needed to accomodate the imageable area." + ls + ls +
                "To test 6229507, put the minimum margins (all 0s) in Page Setup dialog." + ls +
                "Compare Imageable width, height, and margins of portrait against landscape.");

        ta.setEditable(false);
        add(ta);
        setSize(500, 650);
    }

    public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) {

        if (pageIndex > 0) {
            return Printable.NO_SUCH_PAGE;
        }

        Graphics2D g2d = (Graphics2D) graphics;

        int o = pageFormat.getOrientation();

        System.out.println("Format Orientation = " +
                (o == PageFormat.PORTRAIT ? "PORTRAIT" :
                        o == PageFormat.LANDSCAPE ? "LANDSCAPE" :
                                o == PageFormat.REVERSE_LANDSCAPE ? "REVERSE_LANDSCAPE" :
                                        "<invalid>"));
        System.out.println(g2d.getTransform());
        System.out.println("ix=" + pageFormat.getImageableX() +
                " iy=" + pageFormat.getImageableY());
        g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
        g2d.drawString("ORIGIN", 20, 20);
        g2d.drawString("X THIS WAY", 200, 50);
        g2d.drawString("Y THIS WAY", 60, 200);
        g2d.drawRect(0, 0, (int) pageFormat.getImageableWidth(),
                (int) pageFormat.getImageableHeight());
        g2d.setColor(Color.blue);
        g2d.drawRect(1, 1, (int) pageFormat.getImageableWidth() - 2,
                (int) pageFormat.getImageableHeight() - 2);

        return Printable.PAGE_EXISTS;
    }

    private static final String INSTRUCTIONS =
            "You must have a printer available to perform this test\n" +
            "This test is very flexible and requires much interaction.\n" +
            "There are several buttons.\n" +
            "Set Paper: if all fields are valid numbers it sets the Paper object.\n" +
            "This is used to create arbitrary nonsensical paper sizes to help\n" +
            "test validatePage.\n" +
            "Default Page: sets a default page. This should always be valid.\n" +
            "Page Setup: brings up the page dialog. You must OK this dialog\n" +
            "for it to have any effect. You can use this to set different size,\n" +
            "orientation and margins - which of course affect imageable area.\n" +
            "Printer: Used to set the current printer. Useful because current\n" +
            "printer affects the choice of paper sizes available.\n" +
            "You must OK this dialog for it to have any effect.\n" +
            "Validate Page:\n" +
            "The most important button in the test. By setting nonsensical\n" +
            "or valid papers with varying margins etc, this should always find\n" +
            "the closest\n" +
            "match within the limits of what is possible on the current printer.\n" +
            "Print: to the current printer. Not vital for this test.\n" +
            "request.";

    public static void main(String[] args) throws Exception {

        if (PrinterJob.lookupPrintServices().length == 0) {
            throw new SkippedException("Printer not configured or available."
                    + " Test cannot continue.");
        }

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .testUI(ValidatePage::new)
                .rows((int) INSTRUCTIONS.lines().count() + 1)
                .columns(45)
                .build()
                .awaitAndCheck();
    }
}
