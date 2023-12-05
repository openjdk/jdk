/*
 * Copyright (c) 2007, 2023, Oracle and/or its affiliates. All rights reserved.
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
 *
 * @test
 * @bug 4276227 8320443
 * @key printer
 * @summary Checks that the PrinterGraphics is for a Printer GraphicsDevice.
 * Test doesn't run unless there's a printer on the system.
 * @run main/othervm PrinterDevice
 */

import java.io.File;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Destination;
import javax.print.attribute.standard.OrientationRequested;

public class PrinterDevice implements Printable {

    static volatile boolean failed = false;

    public static void main(String args[]) throws PrinterException {
        System.setProperty("java.awt.headless", "true");

        PrinterJob pj = PrinterJob.getPrinterJob();
        if (pj.getPrintService() == null) {
            return; /* Need a printer to run this test */
        }

        PrintRequestAttributeSet aset = new HashPrintRequestAttributeSet();
        File f = new File("./out.prn");
        f.deleteOnExit();
        aset.add(new Destination(f.toURI()));
        aset.add(OrientationRequested.LANDSCAPE);
        pj.setPrintable(new PrinterDevice());
        pj.print(aset);
        if (failed) {
            throw new RuntimeException("Test failed but no exception propagated.");
        }
    }

    public int print(Graphics g, PageFormat pf, int pageIndex) {
        if (pageIndex > 0) {
            return Printable.NO_SUCH_PAGE;
        }

        Graphics2D g2 = (Graphics2D)g;
        GraphicsConfiguration gConfig = g2.getDeviceConfiguration();
        AffineTransform nt = null;
        try {
            /* Make sure calls to get DeviceConfig, its transforms,
             * etc all work without exceptions and as expected */
            System.out.println("GraphicsConfig="+gConfig);
            AffineTransform dt = gConfig.getDefaultTransform();
            System.out.println("Default transform = " + dt);
            nt = gConfig.getNormalizingTransform();
            System.out.println("Normalizing transform = " + nt);
            AffineTransform gt = g2.getTransform();
            System.out.println("Graphics2D transform = " + gt);
        } catch (Exception e) {
            failed = true;
            System.err.println("Unexpected exception getting transform.");
            e.printStackTrace();
            throw e;
        }

        Rectangle bounds = gConfig.getBounds();
        System.out.println("Bounds = " + bounds);
        if (!nt.isIdentity()) {
            failed = true;
            throw new RuntimeException("Expected Identity transform");
        }

        /* Make sure that device really is TYPE_PRINTER */
        GraphicsDevice gd = gConfig.getDevice();
        System.out.println("Printer Device ID = " + gd.getIDstring());
        if (gd.getType() != GraphicsDevice.TYPE_PRINTER) {
            failed = true;
            throw new RuntimeException("Expected printer device");
        }
        System.out.println(" *** ");
        System.out.println("");
        return Printable.PAGE_EXISTS;
    }
}
