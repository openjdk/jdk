/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.print;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.PrintJob;
import java.awt.JobAttributes;
import java.awt.PageAttributes;
import java.util.Properties;
import sun.java2d.Disposer;
import sun.java2d.DisposerRecord;

/**
 * A class which initiates and executes a print job using
 * the underlying PrinterJob graphics conversions.
 *
 * @see java.awt.Toolkit#getPrintJob
 *
 */
public class PrintJob2D extends PrintJob {

    private final PrintJobDelegate printJobDelegate;

    public PrintJob2D(Frame frame,  String doctitle,
                      final Properties props) {
        printJobDelegate = new PrintJobDelegate(frame, doctitle, props);
        Disposer.addRecord(this, new PrintJobDisposerRecord(printJobDelegate));
    }

    public PrintJob2D(Frame frame,  String doctitle,
                      JobAttributes jobAttributes,
                      PageAttributes pageAttributes) {
        printJobDelegate = new PrintJobDelegate(frame, doctitle, jobAttributes, pageAttributes);
        Disposer.addRecord(this, new PrintJobDisposerRecord(printJobDelegate));
    }


    // PrintJob2D API, not PrintJob
    public boolean printDialog() {
        return printJobDelegate.printDialog();
    }

    /**
     * Gets a Graphics object that will draw to the next page.
     * The page is sent to the printer when the graphics
     * object is disposed.  This graphics object will also implement
     * the PrintGraphics interface.
     * @see java.awt.PrintGraphics
     */
    public Graphics getGraphics() {
        /* The caller wants a Graphics instance but we do
         * not want them to make 2D calls. We can't hand
         * back a Graphics2D. The returned Graphics also
         * needs to implement PrintGraphics, so we wrap
         * the Graphics2D instance.
         */
        Graphics g = printJobDelegate.getGraphics();
        if (g == null) { // PrintJob.end() has been called.
            return null;
        } else {
            return new ProxyPrintGraphics(g, this);
        }
    }

    /**
     * Returns the dimensions of the page in pixels.
     * The resolution of the page is chosen so that it
     * is similar to the screen resolution.
     * Except (since 1.3) when the application specifies a resolution.
     * In that case it is scaled accordingly.
     */
    public Dimension getPageDimension() {
        return printJobDelegate.getPageDimension();
    }

    /**
     * Returns the resolution of the page in pixels per inch.
     * Note that this doesn't have to correspond to the physical
     * resolution of the printer.
     */
    public int getPageResolution() {
        return printJobDelegate.getPageResolution();
    }

    /**
     * Returns true if the last page will be printed first.
     */
    public boolean lastPageFirst() {
        return printJobDelegate.lastPageFirst();
    }

    /**
     * Ends the print job and does any necessary cleanup.
     */
    public synchronized void end() {
        printJobDelegate.end();
    }

    private static class PrintJobDisposerRecord implements DisposerRecord {
        private final PrintJobDelegate printJobDelegate;

        PrintJobDisposerRecord(PrintJobDelegate delegate) {
            printJobDelegate = delegate;
        }

        public void dispose() {
            printJobDelegate.end();
        }
    }
}
