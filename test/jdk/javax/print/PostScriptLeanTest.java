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

import java.awt.Font;
import java.awt.Graphics;
import java.awt.Polygon;
import java.awt.geom.AffineTransform;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

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
 * @bug 8349932
 * @summary Verifies that generated PostScript omits unnecessary graphics state commands.
 */
public class PostScriptLeanTest {

    public static void main(String[] args) throws Exception {

        DocFlavor flavor = DocFlavor.SERVICE_FORMATTED.PRINTABLE;
        String mime = "application/postscript";
        StreamPrintServiceFactory[] factories = StreamPrintServiceFactory.lookupStreamPrintServiceFactories(flavor, mime);
        if (factories.length == 0) {
            throw new RuntimeException("Unable to find PostScript print service factory");
        }

        StreamPrintServiceFactory factory = factories[0];
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        StreamPrintService service = factory.getPrintService(output);
        DocPrintJob job = service.createPrintJob();

        PrintJobMonitor monitor = new PrintJobMonitor();
        job.addPrintJobListener(monitor);

        Printable printable = new TestPrintable();
        Doc doc = new SimpleDoc(printable, flavor, null);
        job.print(doc, null);
        monitor.waitForJobToFinish();

        byte[] separator = System.lineSeparator().getBytes(StandardCharsets.UTF_8);
        byte prefix = separator[separator.length - 1];
        byte postfix = separator[0];

        int paths = 0;
        byte[] ps = output.toByteArray();
        for (int i = 1; i + 1 < ps.length; i++) {
            if (ps[i - 1] == prefix && ps[i] == 'N' && ps[i + 1] == postfix) {
                paths++; // found a "newpath" command (aliased to "N")
            }
        }

        if (paths != 1) {
            throw new RuntimeException("Expected 1 path, but found " + paths + " paths");
        }
    }

    private static final class TestPrintable implements Printable {
        @Override
        public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
            if (pageIndex > 0) {
                return NO_SUCH_PAGE;
            }
            Font font1 = new Font("SansSerif", Font.PLAIN, 20);
            Font font2 = font1.deriveFont(AffineTransform.getQuadrantRotateInstance(1));
            graphics.setFont(font1);
            graphics.drawString("XX", 300, 300); // not ignored, adds a path
            graphics.setFont(font2);
            graphics.drawString("\r", 300, 350); // ignored, nothing to draw, no path added
            graphics.drawString("\n", 300, 400); // ignored, nothing to draw, no path added
            graphics.drawString("\t", 300, 450); // ignored, nothing to draw, no path added
            graphics.drawPolygon(new Polygon()); // empty polygon, nothing to draw, no path added
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
