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

/**
 * @test
 * @bug 4869502 4869539
 * @key printer
 * @summary Confirm that ToPage is populated for argument = 2. Range is disabled for argument = 0.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual PrintDlgPageable 0
 * @run main/manual PrintDlgPageable 2
 */

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Pageable;
import java.awt.print.Printable;
import java.awt.print.PrinterJob;
import java.awt.print.PrinterException;

public class PrintDlgPageable implements Printable {

    public static int arg;
    public PrintDlgPageable() {
        super();
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("usage: java PrintDlgPageable { 0 | 2}");
            return;
        }
        arg = Integer.parseInt(args[0]);

        String INSTRUCTIONS = " A pagedialog will be shown.";

        if (arg == 0) {
            INSTRUCTIONS += "\n Confirm that page range is disabled.";
        } else if (arg == 2) {
            INSTRUCTIONS += "\n Confirm ToPage is populated with pagerange 2";
        }
        INSTRUCTIONS += "\nCancel the print dialog. Press PASS if it so seen else press FAIL.";

        PrinterJob pj = PrinterJob.getPrinterJob();
        PageableHandler handler = new PageableHandler();
        pj.setPageable(handler);

        PassFailJFrame passFailJFrame = PassFailJFrame.builder()
            .title("Instructions")
            .instructions(INSTRUCTIONS)
            .columns(35)
            .build();

        if (pj.printDialog()) {
            try {
                pj.print();
            } catch (PrinterException pe) {
                pe.printStackTrace();
            }
        }
        passFailJFrame.awaitAndCheck();
    }

    //printable interface
    public int print(Graphics g, PageFormat pf, int pi) throws PrinterException {

        // Simply draw two rectangles
        Graphics2D g2 = (Graphics2D) g;
        g2.setColor(Color.black);
        g2.translate(pf.getImageableX(), pf.getImageableY());
        g2.drawRect(1, 1, 200, 300);
        g2.drawRect(1, 1, 25, 25);
        return Printable.PAGE_EXISTS;
    }
}

class PageableHandler implements Pageable {

    PageFormat pf = new PageFormat();

    public int getNumberOfPages() {
        return PrintDlgPageable.arg;
    }

    public Printable getPrintable(int pageIndex) {
        return new PrintDlgPageable();
    }

    public PageFormat getPageFormat(int pageIndex) {
        if (pageIndex == 0) {
            pf.setOrientation(PageFormat.PORTRAIT);
            return pf;
        } else {
            pf.setOrientation(PageFormat.LANDSCAPE);
            return pf;
        }
    }

}
