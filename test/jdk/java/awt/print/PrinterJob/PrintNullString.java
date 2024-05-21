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
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Panel;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;

import javax.swing.JOptionPane;

/*
 * @test
 * @bug 4223328
 * @summary Printer graphics must behave the same as screen graphics
 * @key printer
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual PrintNullString
 */
public class PrintNullString extends Frame {
    private static final String INSTRUCTIONS =
            "This test should print a page which contains the same\n" +
            "text messages as in the test window on the screen.\n" +
            "\n" +
            "The messages should contain only 'OK' and 'expected' messages.\n" +
            "Press Pass if it's the case; otherwise press Fail.\n" +
            "\n" +
            "If the page fails to print, but there were no exceptions\n" +
            "then the problem is likely elsewhere (i.e. your printer)";

    public static void main(String[] args) throws Exception {
        if (PrinterJob.lookupPrintServices().length == 0) {
            throw new RuntimeException("Printer not configured or available.");
        }

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .testUI(PrintNullString::new)
                .rows((int) INSTRUCTIONS.lines().count() + 1)
                .columns(45)
                .build()
                .awaitAndCheck();
    }

    public PrintNullString() {
        super("PrintNullString");

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
        private final String nullStr = null;
        private final String emptyStr = "";
        private final AttributedString emptyAttStr = new AttributedString(emptyStr);
        private final AttributedCharacterIterator nullIterator = null;
        private final AttributedCharacterIterator emptyIterator = emptyAttStr.getIterator();

        @Override
        public void paint(Graphics g) {
            Graphics2D g2d = (Graphics2D) g;
            paint(g2d);
        }

        @Override
        public int print(Graphics g, PageFormat pgFmt, int pgIndex) {
            if (pgIndex > 0) {
                return NO_SUCH_PAGE;
            }

            Graphics2D g2d = (Graphics2D) g;
            g2d.translate(pgFmt.getImageableX(), pgFmt.getImageableY());
            paint(g2d);

            return PAGE_EXISTS;
        }

        private void paint(Graphics2D g2d) {
            // API 1: null & empty drawString(String, int, int);
            try {
                g2d.drawString(nullStr, 20, 40);
                g2d.drawString("FAILURE: No NPE for null String, int", 20, 40);
            } catch (NullPointerException e) {
                g2d.drawString("caught expected NPE for null String, int", 20, 40);
            }

            g2d.drawString(emptyStr, 20, 60);
            g2d.drawString("OK for empty String, int", 20, 60);

            // API 2: null & empty drawString(String, float, float);
            try {
                g2d.drawString(nullStr, 20.0f, 80.0f);
                g2d.drawString("FAILURE: No NPE for null String, float", 20, 80);
            } catch (NullPointerException e) {
                g2d.drawString("caught expected NPE for null String, float", 20, 80);
            }

            g2d.drawString(emptyStr, 20.0f, 100.0f);
            g2d.drawString("OK for empty String, float", 20.0f, 100.f);

            // API 3: null & empty drawString(Iterator, int, int);
            try {
                g2d.drawString(nullIterator, 20, 120);
                g2d.drawString("FAILURE: No NPE for null iterator, float", 20, 120);
            } catch (NullPointerException e) {
                g2d.drawString("caught expected NPE for null iterator, int", 20, 120);
            }

            try {
                g2d.drawString(emptyIterator, 20, 140);
                g2d.drawString("FAILURE: No IAE for empty iterator, int", 20, 140);
            } catch (IllegalArgumentException e) {
                g2d.drawString("caught expected IAE for empty iterator, int", 20, 140);
            }

            // API 4: null & empty drawString(Iterator, float, int);
            try {
                g2d.drawString(nullIterator, 20.0f, 160.0f);
                g2d.drawString("FAILURE: No NPE for null iterator, float", 20, 160);
            } catch (NullPointerException e) {
                g2d.drawString("caught expected NPE for null iterator, float", 20, 160);
            }

            try {
                g2d.drawString(emptyIterator, 20, 180);
                g2d.drawString("FAILURE: No IAE for empty iterator, float", 20, 180);
            } catch (IllegalArgumentException e) {
                g2d.drawString("caught expected IAE for empty iterator, float", 20, 180);
            }
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(450, 250);
        }
    }
}
