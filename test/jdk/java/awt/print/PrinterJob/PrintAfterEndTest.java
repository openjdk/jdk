/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @key printer
 * @bug 8370141
 * @summary No crash when printing after job completed.
 * @run main PrintAfterEndTest
 */

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.Pageable;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.standard.Destination;
import java.io.File;
import java.util.concurrent.CountDownLatch;

public class PrintAfterEndTest implements Printable {

    volatile Graphics peekgraphics;
    volatile Graphics pathgraphics;

    public static void main(String args[]) throws Exception {

        for (int i = 0; i < 500; i++) {
            PrintAfterEndTest paet = new PrintAfterEndTest();
            paet.print();
        }
    }

    void print() throws Exception {
        PrinterJob pjob = PrinterJob.getPrinterJob();
        if (pjob == null || pjob.getPrintService() == null) {
            System.out.println("Unable to create a PrintJob");
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        HashPrintRequestAttributeSet aset = new HashPrintRequestAttributeSet();
        File file = new File("out.prn");
        Destination destination = new Destination(file.toURI());
        aset.add(destination);
        pjob.setPrintable(this);
        pjob.print(aset);

        DrawRunnable tpeek = new DrawRunnable(peekgraphics, latch);
        DrawRunnable tpath = new DrawRunnable(pathgraphics, latch);
        tpeek.start();
        tpath.start();
        latch.countDown();
        tpeek.join();
        tpath.join();
   }

    static class DrawRunnable extends Thread {

        Graphics g;
        CountDownLatch latch;
        DrawRunnable(Graphics g, CountDownLatch latch) {
            this.g = g;
            this.latch = latch;
        }

        public void run() {
            if (g == null) {
                return;
            }
            try {
                latch.await();
                g.clearRect(10, 10, 100, 100);
                g.drawRect(0, 300, 200, 400);
                g.fillRect(0, 300, 200, 400);
                g.drawLine(0, 100, 200, 100);
                g.drawString("Hello", 200, 200);
                g.drawOval(200, 200, 200, 200);
                int[] pts = new int[] { 10, 200, 100 };
                g.drawPolyline(pts, pts, pts.length);
                g.drawPolygon(pts, pts, pts.length);
                g.fillPolygon(pts, pts, pts.length);
                g.dispose();
            } catch (Throwable t) {
            }
        }
    }

    public int print(Graphics g, PageFormat pf, int pageIndex) {
        if (pageIndex > 0) {
            return NO_SUCH_PAGE;
        }
        if (peekgraphics == null) {
            peekgraphics = g.create();
        } else if (pathgraphics == null) {
            pathgraphics = g.create();
        }
        Graphics2D g2d = (Graphics2D)g;
        g2d.translate(pf.getImageableX(),  pf.getImageableY());
        g.drawString("random string", 100,20);
        return PAGE_EXISTS;
    }

}
