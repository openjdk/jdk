/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8041902
 * @key printer
 * @summary Test printing of wide poly lines.
 * @library /java/awt/regtesthelpers
 * @library /test/lib
 * @build PassFailJFrame
 * @build jtreg.SkippedException
 * @run main/manual PolylinePrintingTest
 */

import java.awt.BasicStroke;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import jtreg.SkippedException;

public class PolylinePrintingTest implements Printable {
    private static final String INSTRUCTIONS = """
              Press OK in the print dialog and collect the printed page.
              Passing test : Output should show two identical chevrons.
              Failing test : The line joins will appear different.
              """;

    public static void main(String[] args) throws Exception {
        PrinterJob job = PrinterJob.getPrinterJob();
        if (job.getPrintService() == null) {
            throw new SkippedException("Printer not configured or available.");
        }

        PassFailJFrame passFailJFrame = PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(45)
                .build();

        job.setPrintable(new PolylinePrintingTest());
        if (job.printDialog()) {
            job.print();
        }

        passFailJFrame.awaitAndCheck();
    }

    public int print(Graphics graphics, PageFormat pageFormat,
                     int pageIndex) throws PrinterException {
        if (pageIndex > 0) {
            return NO_SUCH_PAGE;
        }

        Graphics2D g2d = (Graphics2D) graphics;
        g2d.setStroke(new BasicStroke(25,
                                      BasicStroke.CAP_ROUND,
                                      BasicStroke.JOIN_MITER,
                                      10.0F, null, 1.0F));

        int[] x2Points = {100, 250, 400};
        int[] y2Points = {100, 400, 100};
        drawPolylineGOOD(g2d, x2Points, y2Points);
        drawPolylineBAD(g2d, x2Points, y2Points);

        return PAGE_EXISTS;
    }

    private void drawPolylineGOOD(Graphics2D g2d,
                                  int[] x2Points, int[] y2Points) {
        Path2D polyline =
            new Path2D.Float(Path2D.WIND_EVEN_ODD, x2Points.length);

        polyline.moveTo(x2Points[0], y2Points[0]);

        for (int index = 1; index < x2Points.length; index++) {
                polyline.lineTo(x2Points[index], y2Points[index]);
        }
        g2d.draw(polyline);
    }

    private void drawPolylineBAD(Graphics2D g, int[] xp, int[] yp) {
        int offset = 200;
        g.translate(0, offset);
        g.drawPolyline(xp, yp, xp.length);
    }
}
