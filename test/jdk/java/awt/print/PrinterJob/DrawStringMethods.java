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

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.print.Book;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterJob;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;

/*
 * @test
 * @bug 4185019
 * @key printer
 * @summary Confirm that all of the drawString methods on Graphics2D
 *          work for printer graphics objects.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual DrawStringMethods
 */
public class DrawStringMethods implements Printable {
    private static final String INSTRUCTIONS =
            " This test will automatically initiate a print.\n" +
            "\n" +
            " Confirm that the following methods are printed:\n" +
            " For Graphics: drawString, drawString, drawChars, drawBytes\n" +
            " For Graphics2D: drawString, drawString, drawGlyphVector";

    public static void main(String[] args) throws Exception {
        if (PrinterJob.lookupPrintServices().length == 0) {
            throw new RuntimeException("Printer not configured or available.");
        }

        PassFailJFrame passFailJFrame = PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 1)
                .columns(45)
                .build();

        PrinterJob pjob = PrinterJob.getPrinterJob();
        PageFormat pf = pjob.defaultPage();
        Book book = new Book();

        book.append(new DrawStringMethods(), pf);
        pjob.setPageable(book);
        pjob.print();

        passFailJFrame.awaitAndCheck();
    }

    private static AttributedCharacterIterator getIterator(String s) {
        return new AttributedString(s).getIterator();
    }

    @Override
    public int print(Graphics g, PageFormat pf, int pageIndex) {
        int ix = (int) pf.getImageableX();
        int iy = (int) pf.getImageableY();
        String s;

        g.setColor(Color.black);

        iy += 50;
        s = "--- Graphics methods: ---";
        g.drawString(s, ix, iy);

        iy += 30;
        s = "drawString(String str, int x, int y)";
        g.drawLine(ix, iy, ix+10, iy);
        g.drawString(s, ix+20, iy);

        iy += 30;
        s = "drawString(AttributedCharacterIterator iterator, int x, int y)";
        g.drawLine(ix, iy, ix+10, iy);
        g.drawString(getIterator(s), ix+20, iy);

        iy += 30;
        s = "drawChars(char data[], int offset, int length, int x, int y)";
        g.drawLine(ix, iy, ix+10, iy);
        g.drawChars(s.toCharArray(), 0, s.length(), ix+20, iy);

        iy += 30;
        s = "drawBytes(byte data[], int offset, int length, int x, int y)";
        byte[] data = new byte[s.length()];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) s.charAt(i);
        }
        g.drawLine(ix, iy, ix+10, iy);
        g.drawBytes(data, 0, data.length, ix+20, iy);

        iy += 50;
        s = "--- Graphics2D methods: ---";
        g.drawString(s, ix, iy);

        if (g instanceof Graphics2D) {
            Graphics2D g2d = (Graphics2D) g;
            Font f = g2d.getFont();
            FontRenderContext frc = g2d.getFontRenderContext();

            iy += 30;
            s = "drawString(String s, float x, float y)";
            g.drawLine(ix, iy, ix+10, iy);
            g2d.drawString(s, (float) ix+20, (float) iy);

            iy += 30;
            s = "drawString(AttributedCharacterIterator iterator, "+
                "float x, float y)";
            g.drawLine(ix, iy, ix+10, iy);
            g2d.drawString(getIterator(s), (float) ix+20, (float) iy);

            iy += 30;
            s = "drawGlyphVector(GlyphVector g, float x, float y)";
            g.drawLine(ix, iy, ix+10, iy);
            g2d.drawGlyphVector(f.createGlyphVector(frc, s), ix+20, iy);
        } else {
            iy += 30;
            s = "Graphics object does not support Graphics2D methods";
            g.drawString(s, ix+20, iy);
        }

        return PAGE_EXISTS;
    }
}
