/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Font;
import java.awt.Graphics;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.SimpleDoc;
import javax.print.StreamPrintService;
import javax.print.StreamPrintServiceFactory;
import javax.print.event.PrintJobAdapter;
import javax.print.event.PrintJobEvent;

/*
 * @test
 * @bug 8339974
 * @summary Verifies that text prints correctly using scaled and rotated fonts.
 */
public class PostScriptRotatedScaledFontTest {

    public static void main(String[] args) throws Exception {
        test(0);
        test(1);
        test(2);
        test(3);
        test(4);
    }

    private static void test(int quadrants) throws Exception {

        DocFlavor flavor = DocFlavor.SERVICE_FORMATTED.PRINTABLE;
        String mime = "application/postscript";
        StreamPrintServiceFactory[] factories = StreamPrintServiceFactory.lookupStreamPrintServiceFactories(flavor, mime);
        if (factories.length == 0) {
            throw new RuntimeException("Unable to find PostScript print service factory");
        }

        StreamPrintServiceFactory factory = factories[0];

        // required to trigger "text-as-shapes" code path in
        // PSPathGraphics.drawString(String, float, float, Font, FontRenderContext, float)
        // for *all* text, not just text that uses a transformed font
        String shapeText = "sun.java2d.print.shapetext";
        System.setProperty(shapeText, "true");

        try {
            for (int scale = 1; scale <= 100; scale++) {

                ByteArrayOutputStream output = new ByteArrayOutputStream();
                StreamPrintService service = factory.getPrintService(output);
                DocPrintJob job = service.createPrintJob();

                PrintJobMonitor monitor = new PrintJobMonitor();
                job.addPrintJobListener(monitor);

                Printable printable = new TestPrintable(scale, quadrants);
                Doc doc = new SimpleDoc(printable, flavor, null);
                job.print(doc, null);
                monitor.waitForJobToFinish();

                byte[] ps = output.toByteArray();
                Rectangle2D.Double bounds = findTextBoundingBox(ps);
                if (bounds == null) {
                    throw new RuntimeException("Text missing: scale=" + scale
                        + ", quadrants=" + quadrants);
                }

                boolean horizontal = (bounds.width > bounds.height);
                boolean expectedHorizontal = (quadrants % 2 == 0);
                if (horizontal != expectedHorizontal) {
                    throw new RuntimeException("Wrong orientation: scale=" + scale
                        + ", quadrants=" + quadrants + ", bounds=" + bounds
                        + ", expectedHorizontal=" + expectedHorizontal
                        + ", horizontal=" + horizontal);
                }
            }
        } finally {
            System.clearProperty(shapeText);
        }
    }

    // very basic, uses moveto ("x y M"), lineto ("x y L"), and curveto ("x1 y1 x2 y2 x3 y3 C")
    private static Rectangle2D.Double findTextBoundingBox(byte[] ps) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;
        boolean pastPageClip = false;
        List< String > lines = new String(ps, StandardCharsets.ISO_8859_1).lines().toList();
        for (String line : lines) {
            if (!pastPageClip) {
                pastPageClip = "WC".equals(line);
                continue;
            }
            String[] values = line.split(" ");
            if (values.length == 3 || values.length == 7) {
                String cmd = values[values.length - 1];
                if ("M".equals(cmd) || "L".equals(cmd) || "C".equals(cmd)) {
                    String sx = values[values.length - 3];
                    String sy = values[values.length - 2];
                    double x = Double.parseDouble(sx);
                    double y = Double.parseDouble(sy);
                    if (x < minX) {
                        minX = x;
                    }
                    if (y < minY) {
                        minY = y;
                    }
                    if (x > maxX) {
                        maxX = x;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                }
            }
        }
        if (minX != Double.MAX_VALUE) {
            return new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
        } else {
            return null;
        }
    }

    private static final class TestPrintable implements Printable {
        private final int scale;
        private final int quadrants;
        public TestPrintable(int scale, int quadrants) {
            this.scale = scale;
            this.quadrants = quadrants;
        }
        @Override
        public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
            if (pageIndex > 0) {
                return NO_SUCH_PAGE;
            }
            AffineTransform at = AffineTransform.getQuadrantRotateInstance(quadrants);
            at.scale(scale, scale);
            Font base = new Font("SansSerif", Font.PLAIN, 10);
            Font font = base.deriveFont(at);
            graphics.setFont(font);
            graphics.drawString("TEST", 300, 300);
            return PAGE_EXISTS;
        }
    }

    private static class PrintJobMonitor extends PrintJobAdapter {
        private boolean finished;
        @Override
        public void printJobCanceled(PrintJobEvent pje) {
            finished();
        }
        @Override
        public void printJobCompleted(PrintJobEvent pje) {
            finished();
        }
        @Override
        public void printJobFailed(PrintJobEvent pje) {
            finished();
        }
        @Override
        public void printJobNoMoreEvents(PrintJobEvent pje) {
            finished();
        }
        private synchronized void finished() {
            finished = true;
            notify();
        }
        public synchronized void waitForJobToFinish() {
            try {
                while (!finished) {
                    wait();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
