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

public class PrintAfterEndTest implements Printable {

    static volatile Graphics peekgraphics;
    static volatile Graphics pathgraphics;

    public static void main(String args[]) throws PrinterException {

        PrinterJob pjob = PrinterJob.getPrinterJob();
        pjob.setPrintable(new PrintAfterEndTest());
        pjob.print();


        System.out.println("PeekGraphics= " + peekgraphics);
        System.out.println("PathGraphics= " + pathgraphics);

        if (peekgraphics != null) {
            peekgraphics.drawString("random string", 200, 200);
            peekgraphics.drawLine(100, 100, 300, 300);
            peekgraphics.dispose();
        }
        if (pathgraphics != null) {
            pathgraphics.drawString("random string", 200, 200);
            pathgraphics.drawLine(100, 100, 300, 300);
            pathgraphics.dispose();
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
