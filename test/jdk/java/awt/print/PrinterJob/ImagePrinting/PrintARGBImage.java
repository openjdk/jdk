/*
 * Copyright (c) 2007, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.lang.reflect.InvocationTargetException;

/*
 * @test
 * @bug 6581756
 * @key printer
 * @library ../../../regtesthelpers
 * @build PassFailJFrame
 * @summary Test printing of images which need to have src area clipped
 * @run main/manual PrintARGBImage
 */

public class PrintARGBImage implements Printable {

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        if (PrinterJob.lookupPrintServices().length > 0) {

            String instruction = """
                    This is a manual test which needs a printer installed.
                    If you have no printer installed the test passes automatically.
                    The test runs automatically and sends one page to the default printer.
                    The test passes if the text shows through the rectangular image.
                    """;

            PassFailJFrame passFailJFrame = new PassFailJFrame(instruction, 10);
            PassFailJFrame.positionTestWindow(null, PassFailJFrame.Position.HORIZONTAL);
            try {
                PrinterJob pj = PrinterJob.getPrinterJob();
                pj.setPrintable(new PrintARGBImage());
                pj.print();
            } catch (Exception ex) {
                ex.printStackTrace();
                throw new RuntimeException("Exception whilst printing.");
            }

            passFailJFrame.awaitAndCheck();

        } else {
            System.out.println("Printer not configured or available."
                    + " Test cannot continue.");
            PassFailJFrame.forcePass();
        }
    }

    public int print(Graphics g, PageFormat pf, int pageIndex)
               throws PrinterException {

        if (pageIndex != 0) {
            return NO_SUCH_PAGE;
        }
        Graphics2D g2 = (Graphics2D)g;
        g2.translate(pf.getImageableX(), pf.getImageableY());
        g2.setColor( Color.BLACK );
        g2.drawString("This text should be visible through the image", 0, 20);
        BufferedImage bi = new BufferedImage(100, 100,
                                              BufferedImage.TYPE_INT_ARGB );
        Graphics ig = bi.createGraphics();
        ig.setColor( new Color( 192, 192, 192, 80 ) );
        ig.fillRect( 0, 0, 100, 100 );
        ig.setColor( Color.BLACK );
        ig.drawRect( 0, 0, 99, 99 );
        ig.dispose();
        g2.drawImage(bi, 10, 0, 90, 90, null );
        return PAGE_EXISTS;
    }
}


