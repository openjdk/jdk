/*
 * Copyright (c) 2006, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6444688
 * @key printer
 * @summary Print an image with an IndexedColorModel with transparent pixel.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual BitmaskImage
 */

import java.awt.Button;
import java.awt.Color;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.awt.print.PageFormat;
import java.awt.print.PrinterJob;
import java.awt.print.Printable;
import java.awt.print.PrinterException;

import static java.awt.print.Printable.NO_SUCH_PAGE;
import static java.awt.print.Printable.PAGE_EXISTS;

public class BitmaskImage implements Printable, ActionListener {

    static int sz = 1000;
    BufferedImage bi;

    public BitmaskImage() {
        int i = 0;
        int[] cmap = new int[256];
        for (int r = 0; r < 256; r += 51) {
            for (int g = 0; g < 256; g += 51) {
                for (int b = 0; b < 256; b += 51) {
                    cmap[i++] = (0xff << 24) | (r << 16) | (g << 8) | b;
                }
            }
        }

        IndexColorModel icm = new
            IndexColorModel(8, 256, cmap, 0, true, 253, DataBuffer.TYPE_BYTE);
        bi = new BufferedImage(sz, sz, BufferedImage.TYPE_BYTE_INDEXED, icm);
        Graphics g = bi.getGraphics();
        Graphics2D g2d = (Graphics2D)g;
        g.setColor(Color.white);
        g.fillRect(0, 0, sz, sz);
        g.setColor(Color.black);
        int off = sz / 20;
        int wh = sz / 10;
        for (int x = off; x < sz; x += wh * 2) {
           for (int y = off; y < sz; y += wh * 2) {
               g.fillRect(x, y, wh, wh);
           }
        }
    }

    public int print(Graphics g, PageFormat pf, int page) throws
                                                        PrinterException {

        if (page > 0) { /* We have only one page, and 'page' is zero-based */
            return NO_SUCH_PAGE;
        }

        Graphics2D g2d = (Graphics2D)g;
        AffineTransform tx = g2d.getTransform();
        double sx = tx.getScaleX();
        double sy = tx.getScaleY();
        g2d.translate(pf.getImageableX(), pf.getImageableY());
        g2d.scale(1/sx, 1/sx);
        g.drawImage(bi, 10, 10, null);

        /* tell the caller that this page is part of the printed document */
        return PAGE_EXISTS;
    }

    public void actionPerformed(ActionEvent e) {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintable(this);
        boolean ok = job.printDialog();
        if (ok) {
            try {
                 job.print();
            } catch (PrinterException ex) {
             /* The job did not successfully complete */
            }
        }
        System.out.println("done");
    }

    static String INSTRUCTIONS = """
        Press the "Print Simple ICM Image" button and if a printer is available,
        choose one in the dialog and click OK to start printing.
        This test will print an image which contains a grid of black squares.
        If it prints so, press Pass otherwise press Fail.""";

    public static Frame initTest() {
        Frame f = new Frame("Image Printer");
        Button printButton = new Button("Print Simple ICM image...");
        printButton.addActionListener(new BitmaskImage());
        f.add(printButton);
        f.pack();
        return f;
    }

    public static void main(String[] args) throws Exception {
         PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .testUI(BitmaskImage::initTest)
                .columns(35)
                .build()
                .awaitAndCheck();
    }
}
