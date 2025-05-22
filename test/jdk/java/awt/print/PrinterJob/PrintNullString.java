/*
 * Copyright (c) 2007, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.File;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;

import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Destination;

/*
 * @test
 * @bug 4223328
 * @summary Printer graphics must throw expected exceptions
 * @key printer
 * @run main PrintNullString
 */
public class PrintNullString implements Printable {
    private final String nullStr = null;
    private final String emptyStr = "";
    private final AttributedString emptyAttStr = new AttributedString(emptyStr);
    private final AttributedCharacterIterator nullIterator = null;
    private final AttributedCharacterIterator emptyIterator = emptyAttStr.getIterator();

    public static void main(String[] args) throws Exception {
        if (PrinterJob.lookupPrintServices().length == 0) {
            throw new RuntimeException("Printer not configured or available.");
        }

        new PrintNullString();
    }

    public PrintNullString() throws PrinterException {
        PrinterJob pj = PrinterJob.getPrinterJob();
        pj.setPrintable(this, new PageFormat());
        PrintRequestAttributeSet pSet = new HashPrintRequestAttributeSet();
        File file = new File("out.prn");
        file.deleteOnExit();
        pSet.add(new Destination(file.toURI()));
        pj.print(pSet);
    }

    @Override
    public int print(Graphics g, PageFormat pgFmt, int pgIndex) {
        if (pgIndex > 0) {
            return NO_SUCH_PAGE;
        }

        Graphics2D g2d = (Graphics2D) g;
        g2d.translate(pgFmt.getImageableX(), pgFmt.getImageableY());

        // API 1: null & empty drawString(String, int, int);
        try {
            g2d.drawString(nullStr, 20, 40);
            throw new RuntimeException("FAILURE: No NPE for null String, int");
        } catch (NullPointerException e) {
            g2d.drawString("caught expected NPE for null String, int", 20, 40);
        }

        g2d.drawString(emptyStr, 20, 60);
        g2d.drawString("OK for empty String, int", 20, 60);

        // API 2: null & empty drawString(String, float, float);
        try {
            g2d.drawString(nullStr, 20.0f, 80.0f);
            throw new RuntimeException("FAILURE: No NPE for null String, float");
        } catch (NullPointerException e) {
            g2d.drawString("caught expected NPE for null String, float", 20, 80);
        }

        g2d.drawString(emptyStr, 20.0f, 100.0f);
        g2d.drawString("OK for empty String, float", 20.0f, 100.f);

        // API 3: null & empty drawString(Iterator, int, int);
        try {
            g2d.drawString(nullIterator, 20, 120);
            throw new RuntimeException("FAILURE: No NPE for null iterator, int");
        } catch (NullPointerException e) {
            g2d.drawString("caught expected NPE for null iterator, int", 20, 120);
        }

        try {
            g2d.drawString(emptyIterator, 20, 140);
            throw new RuntimeException("FAILURE: No IAE for empty iterator, int");
        } catch (IllegalArgumentException e) {
            g2d.drawString("caught expected IAE for empty iterator, int", 20, 140);
        }

        // API 4: null & empty drawString(Iterator, float, int);
        try {
            g2d.drawString(nullIterator, 20.0f, 160.0f);
            throw new RuntimeException("FAILURE: No NPE for null iterator, float");
        } catch (NullPointerException e) {
            g2d.drawString("caught expected NPE for null iterator, float", 20, 160);
        }

        try {
            g2d.drawString(emptyIterator, 20.0f, 180.0f);
            throw new RuntimeException("FAILURE: No IAE for empty iterator, float");
        } catch (IllegalArgumentException e) {
            g2d.drawString("caught expected IAE for empty iterator, float", 20, 180);
        }

        return PAGE_EXISTS;
    }
}
