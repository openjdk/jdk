/*
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Button;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

/*
 * @test
 * @bug 4242639
 * @summary Printing quality problem on Canon and NEC
 * @key printer
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual RasterTest
 */
public class RasterTest extends Frame implements ActionListener {
    private final RasterCanvas c;
    private static final String INSTRUCTIONS =
            "This test uses rendering operations which force the implementation\n" +
            "to print the page as a raster\n" +
            "You should see two square images, the 1st containing overlapping\n" +
            "composited squares, the lower image shows a gradient paint.\n" +
            "The printed output should match the on-screen display, although\n" +
            "only colour printers will be able to accurately reproduce the\n" +
            "subtle color changes.";

    public static void main(String[] args) throws Exception {
        if (PrinterJob.lookupPrintServices().length == 0) {
            throw new RuntimeException("Printer not configured or available.");
        }

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .testUI(RasterTest::new)
                .rows((int) INSTRUCTIONS.lines().count() + 1)
                .columns(45)
                .build()
                .awaitAndCheck();
    }

    public RasterTest() {
        super("Java 2D Raster Printing");

        c = new RasterCanvas();
        add("Center", c);

        Button printButton = new Button("Print");
        printButton.addActionListener(this);
        add("South", printButton);
        pack();
        setBackground(Color.white);
    }

    public void actionPerformed(ActionEvent e) {
        PrinterJob pj = PrinterJob.getPrinterJob();

        if (pj.printDialog()) {
            pj.setPrintable(c);
            try {
                pj.print();
            } catch (PrinterException pe) {
                pe.printStackTrace();
                PassFailJFrame.forceFail("Test failed because of PrinterException");
            }
        }
    }

    private static class RasterCanvas extends Canvas implements Printable {
        @Override
        public int print(Graphics g, PageFormat pgFmt, int pgIndex) {
            if (pgIndex > 0) {
                return Printable.NO_SUCH_PAGE;
            }

            Graphics2D g2d = (Graphics2D) g;
            g2d.translate(pgFmt.getImageableX(), pgFmt.getImageableY());
            doPaint(g2d);

            return Printable.PAGE_EXISTS;
        }

        @Override
        public void paint(Graphics g) {
            doPaint(g);
        }

        private void doPaint(Graphics g) {
            BufferedImage bimg = new BufferedImage(200, 200,
                    BufferedImage.TYPE_INT_ARGB);
            Graphics ig = bimg.getGraphics();
            Color alphared = new Color(255, 0, 0, 128);
            Color alphagreen = new Color(0, 255, 0, 128);
            Color alphablue = new Color(0, 0, 255, 128);
            ig.setColor(alphared);
            ig.fillRect(0, 0, 200, 200);
            ig.setColor(alphagreen);
            ig.fillRect(25, 25, 150, 150);
            ig.setColor(alphablue);
            ig.fillRect(75, 75, 125, 125);
            g.drawImage(bimg, 10, 25, this);
            ig.dispose();

            GradientPaint gp =
                    new GradientPaint(10.0f, 10.0f, alphablue, 210.0f, 210.0f, alphared, true);

            Graphics2D g2 = (Graphics2D) g;
            g2.setPaint(gp);
            g2.fillRect(10, 240, 200, 200);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(500, 500);
        }
    }
}
