/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6543815
 * @summary Image should be sent to printer, no exceptions thrown.
 *    The 2 printouts should have a rectangle which is the minimum
 *    possible margin.
 * @run main/manual Margins
 */

import java.awt.*;
import java.awt.print.*;

public class Margins implements Printable {

    public static void main(String args[]) {
        PrinterJob job = PrinterJob.getPrinterJob();
        PageFormat pageFormat = job.defaultPage();
        Paper paper = pageFormat.getPaper();
        double wid = paper.getWidth();
        double hgt = paper.getHeight();
        paper.setImageableArea(0, -10, wid, hgt);
        pageFormat = job.pageDialog(pageFormat);
        pageFormat.setPaper(paper);
        job.setPrintable(new Margins(), pageFormat);
        try {
            job.print();
        } catch (PrinterException e) {
        }

        paper.setImageableArea(0, 0, wid, hgt+72);
        pageFormat = job.pageDialog(pageFormat);
        pageFormat.setPaper(paper);

        job.setPrintable(new Margins(), pageFormat);
        try {
           job.print();
        } catch (PrinterException e) {
        }
   }

   public int print(Graphics g, PageFormat pf, int page)
       throws PrinterException {

       if (page > 0) {
           return NO_SUCH_PAGE;
       }
       int ix = (int)pf.getImageableX();
       int iy = (int)pf.getImageableY();
       int iw = (int)pf.getImageableWidth();
       int ih = (int)pf.getImageableHeight();
       System.out.println("ix="+ix+" iy="+iy+" iw="+iw+" ih="+ih);
       if ((ix < 0) || (iy < 0)) {
           throw new RuntimeException("Imageable x or y is a negative value.");
       }

       Paper paper = pf.getPaper();
       double wid = paper.getWidth();
       double hgt = paper.getHeight();

       if ((ix+iw > wid) || (iy+ih > hgt)) {
           throw new RuntimeException("Printable width or height exceeds paper width or height.");
       }

       Graphics2D g2d = (Graphics2D)g;
       g2d.translate(ix, iy);
       g2d.setColor(Color.black);
       g2d.drawRect(1, 1, iw-2, ih-2);

       return PAGE_EXISTS;
   }
}
