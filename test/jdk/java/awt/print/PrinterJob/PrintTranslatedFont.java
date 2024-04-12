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
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Panel;
import java.awt.geom.AffineTransform;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

import javax.swing.JOptionPane;

/*
 * @test
 * @bug 6359734
 * @key printer
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Test that fonts with a translation print where they should.
 * @run main/manual PrintTranslatedFont
 */
public class PrintTranslatedFont extends Frame {
    private static final String INSTRUCTIONS =
            "This test should print a page which contains the same\n" +
            "content as the test window on the screen, in particular the lines\n" +
            "should be immediately under the text\n\n" +
            "If an exception is thrown, or the page doesn't print properly\n" +
            "then the test fails";

    public static void main(String[] args) throws Exception {
        if (PrinterJob.lookupPrintServices().length == 0) {
            throw new RuntimeException("Printer not configured or available.");
        }

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .testUI(PrintTranslatedFont::new)
                .rows((int) INSTRUCTIONS.lines().count() + 1)
                .columns(45)
                .build()
                .awaitAndCheck();
    }

    public PrintTranslatedFont() {
        super("PrintTranslatedFont");

        TextCanvas c = new TextCanvas();
        add("Center", c);

        Button b = new Button("Print");
        add("South", b);
        b.addActionListener(e -> {
            PrinterJob pj = PrinterJob.getPrinterJob();
            if (pj.printDialog()) {
                pj.setPrintable(c);
                try {
                    pj.print();
                } catch (PrinterException ex) {
                    ex.printStackTrace();
                    String msg = "PrinterException: " + ex.getMessage();
                    JOptionPane.showMessageDialog(b, msg, "Error occurred",
                            JOptionPane.ERROR_MESSAGE);
                    PassFailJFrame.forceFail(msg);
                }
            }
        });

        pack();
    }

    private static class TextCanvas extends Panel implements Printable {
        @Override
        public void paint(Graphics g) {
            Graphics2D g2d = (Graphics2D) g;
            paint(g2d);
        }

        @Override
        public int print(Graphics g, PageFormat pgFmt, int pgIndex) {
            if (pgIndex > 0) {
                return Printable.NO_SUCH_PAGE;
            }

            Graphics2D g2d = (Graphics2D) g;
            g2d.translate(pgFmt.getImageableX(), pgFmt.getImageableY());
            paint(g2d);
            return Printable.PAGE_EXISTS;
        }

        private void paint(Graphics2D g2d) {
            Font f = new Font("Dialog", Font.PLAIN, 20);
            int tx = 20;
            int ty = 20;
            AffineTransform at = AffineTransform.getTranslateInstance(tx, ty);
            f = f.deriveFont(at);
            g2d.setFont(f);

            FontMetrics fm = g2d.getFontMetrics();
            String str = "Basic ascii string";
            int sw = fm.stringWidth(str);
            int posx = 20;
            int posy = 40;
            g2d.drawString(str, posx, posy);
            g2d.drawLine(posx + tx, posy + ty + 2, posx + tx + sw, posy + ty + 2);

            posx = 20;
            posy = 70;
            str = "Test string compound printing \u2203\u2200";
            sw = fm.stringWidth(str);
            g2d.drawString(str, posx, posy);
            g2d.drawLine(posx + tx, posy + ty + 2, posx + tx + sw, posy + ty + 2);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(450, 250);
        }
    }
}
