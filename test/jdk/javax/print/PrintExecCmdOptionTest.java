/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, BELLSOFT. All rights reserved.
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

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Media;
import javax.print.attribute.standard.MediaTray;
import javax.print.attribute.standard.OutputBin;
import java.awt.Graphics;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

/*
 * @test
 * @bug 8349350
 * @key printer
 * @summary no ArrayIndexOutOfBoundsException with multiple print options
 * @run main PrintExecCmdOptionTest
 */

public class PrintExecCmdOptionTest {

    public static void main(String[] args) throws PrinterException {
        PrintService[] printServices = PrintServiceLookup.lookupPrintServices(null, null);
        if (printServices.length == 0) {
            System.out.println("No print service found");
            return;
        }

        PrintService testPrintService = null;
        OutputBin outputBin = null;
        MediaTray mediaTray = null;
        for (PrintService ps : printServices) {
            mediaTray = null;
            Media[] medias = (Media[]) ps.
                    getSupportedAttributeValues(Media.class, null, null);
            if (medias != null) {
                for (Media m : medias) {
                    if (m instanceof MediaTray) {
                        mediaTray = (MediaTray) m;
                        break;
                    }
                }
            }
            if (mediaTray == null) {
                continue;
            }
            OutputBin[] outputBins = (OutputBin[]) ps.
                    getSupportedAttributeValues(OutputBin.class, null, null);
            if (outputBins != null && outputBins.length > 0) {
                outputBin = outputBins[0];
                testPrintService = ps;
                break;
            }
        }
        if (testPrintService == null) {
            System.out.println("Test print service not found");
            return;
        }

        PrinterJob printerJob = PrinterJob.getPrinterJob();
        printerJob.setPrintService(testPrintService);

        PrintRequestAttributeSet attributeSet = new HashPrintRequestAttributeSet();
        attributeSet.add(outputBin);
        attributeSet.add(mediaTray);

        printerJob.setPrintable(new Printable() {
            @Override
            public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) {
                return NO_SUCH_PAGE;
            }
        });
        printerJob.print(attributeSet);
    }
}
