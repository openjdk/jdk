/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4904236
 * @key printer
 * @summary StreamPrintService ignores the PrintReqAttrSet when printing through 2D Printing
 * @run main StreamPrintingOrientation
 */

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import javax.print.attribute.Attribute;
import javax.print.PrintService;
import javax.print.StreamPrintServiceFactory;
import javax.print.attribute.standard.Copies;
import javax.print.attribute.standard.JobName;
import javax.print.attribute.standard.OrientationRequested;
import javax.print.attribute.HashPrintRequestAttributeSet;

public class StreamPrintingOrientation implements Printable {

    public static void main(String[] args) throws Exception {
        StreamPrintingOrientation pd = new StreamPrintingOrientation();
        PrinterJob pj = PrinterJob.getPrinterJob();
        HashPrintRequestAttributeSet prSet = new HashPrintRequestAttributeSet();
        PrintService service = null;

        FileOutputStream fos = null;
        String mType = "application/postscript";

        File fl = new File("stream_landscape.ps");
        fl.deleteOnExit();
        fos = new FileOutputStream(fl);
        StreamPrintServiceFactory[] factories = PrinterJob.lookupStreamPrintServices(mType);
        if (factories.length > 0) {
            service = factories[0].getPrintService(fos);
        }

        if (service != null) {
            System.out.println("Stream Print Service " + service);
            pj.setPrintService(service);
        } else {
            throw new RuntimeException("No stream Print Service available.");
        }

        pj.setPrintable(pd);
        prSet.add(OrientationRequested.LANDSCAPE);
        prSet.add(new Copies(1));
        prSet.add(new JobName("orientation test", null));
        System.out.println("open PrintDialog..");

        System.out.println("\nValues in attr set passed to print method");
        Attribute attr[] = prSet.toArray();
        for (int x = 0; x < attr.length; x++) {
            System.out.println("Name " + attr[x].getName() + "  " + attr[x]);
        }
        System.out.println("About to print the data ...");
        if (service != null) {
            System.out.println("TEST: calling Print");
            pj.print(prSet);
            System.out.println("TEST: Printed");
        }

        File fp = new File("stream_portrait.ps");
        fp.deleteOnExit();
        fos = new FileOutputStream(fp);
        if (factories.length > 0) {
            service = factories[0].getPrintService(fos);
        }

        pj.setPrintService(service);
        pj.setPrintable(pd);
        prSet.add(OrientationRequested.PORTRAIT);
        prSet.add(new Copies(1));
        prSet.add(new JobName("orientation test", null));
        if (service != null) {
            pj.print(prSet);
        }

        if (Files.mismatch(fl.toPath(), fp.toPath()) == -1) {
            throw new RuntimeException("Printing stream orientation is same " +
                                       "for both PORTRAIT and LANDSCAPE");
        }
    }

    //printable interface
    public int print(Graphics g, PageFormat pf, int pi) throws PrinterException {

        if (pi > 0) {
            return Printable.NO_SUCH_PAGE;
        }
        // Simply draw two rectangles
        Graphics2D g2 = (Graphics2D) g;
        g2.setColor(Color.black);
        g2.translate(pf.getImageableX(), pf.getImageableY());
        System.out.println("StreamPrinting Test Width " + pf.getWidth() + " Height " + pf.getHeight());
        g2.drawRect(1, 1, 200, 300);
        g2.drawRect(1, 1, 25, 25);
        return Printable.PAGE_EXISTS;
    }
}
