/*
 * Copyright (c) 2009, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.InputStream;
import java.io.Reader;
import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.DocAttributeSet;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.SheetCollate;

/*
 * @test
 * @bug 6362683 8012381
 * @summary Collation should work.
 * @key printer
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual Collate2DPrintingTest
 */
public class Collate2DPrintingTest
        extends Frame implements Doc, Printable, ActionListener {

    Button print2D = new Button("2D Print");
    Button printMerlin = new Button("PrintService");
    PrinterJob pj = PrinterJob.getPrinterJob();
    PrintService defService = PrintServiceLookup.lookupDefaultPrintService();
    HashPrintRequestAttributeSet prSet = new HashPrintRequestAttributeSet();

    public Collate2DPrintingTest() {
        Panel butPanel = new Panel();
        butPanel.add(print2D);
        butPanel.add(printMerlin);
        print2D.addActionListener(this);
        printMerlin.addActionListener(this);
        add("South", butPanel);

        prSet.add(SheetCollate.COLLATED);
        prSet.add(new Copies(2));
        pj.setPrintable(Collate2DPrintingTest.this);
        setSize(300, 200);
    }

    public int print(Graphics g, PageFormat pf, int pageIndex)
            throws PrinterException {
        g.drawString("Page: " + pageIndex, 100, 100);
        if (pageIndex == 2) {
            return Printable.NO_SUCH_PAGE;
        } else {
            return Printable.PAGE_EXISTS;
        }
    }

    public void actionPerformed(ActionEvent ae) {
        try {
            if (ae.getSource() == print2D) {
                if (pj.printDialog(prSet)) {
                    pj.print(prSet);
                }
            } else {
                DocPrintJob pj = defService.createPrintJob();
                pj.print(this, prSet);
            }
        } catch (Exception e) {
            PassFailJFrame.forceFail( "Test Failed");
            e.printStackTrace();
        }
    }

    public DocAttributeSet getAttributes() {
        return null;
    }

    public DocFlavor getDocFlavor() {
        return DocFlavor.SERVICE_FORMATTED.PRINTABLE;
    }

    public Object getPrintData() {
        return this;
    }

    public Reader getReaderForText() {
        return null;
    }

    public InputStream getStreamForBytes() {
        return null;
    }

    private static final String INSTRUCTIONS =
            "Click on the '2D Print' button.\n" +
            "Choose copies as '2' with 'Collated' checkbox and Print\n" +
            "\n" +
            "Click on the 'PrintService', should get a print from default printer\n" +
            "\n" +
            "If you get only one copy or non 'Collated' prints from any of the above cases, " +
            "test failed";

    public static void main(String[] args) throws Exception {

        if (PrinterJob.lookupPrintServices().length == 0) {
            throw new RuntimeException("Printer not configured or available.");
        }

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .testUI(Collate2DPrintingTest::new)
                .rows((int) INSTRUCTIONS.lines().count() + 1)
                .columns(45)
                .build()
                .awaitAndCheck();
    }
}
