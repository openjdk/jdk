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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

import jtreg.SkippedException;

/*
 * @test
 * @bug 4399442
 * @summary Brackets should be quoted in Postscript output
 * @key printer
 * @library /java/awt/regtesthelpers
 * @library /test/lib
 * @build PassFailJFrame
 * @build jtreg.SkippedException
 * @run main/manual PrintParenString
 */
public class PrintParenString extends Frame implements ActionListener {

    private final TextCanvas c;

    private static final String INSTRUCTIONS =
            "You must have a printer available to perform this test\n" +
            "This test should print a page which contains the same\n" +
            "text message as in the test window on the screen\n" +
            "You should also monitor the command line to see if any exceptions\n" +
            "were thrown\n" +
            "If an exception is thrown, or the page doesn't print properly\n" +
            "then the test fails";

    public static void main(String[] args) throws Exception {

        if (PrinterJob.lookupPrintServices().length == 0) {
            throw new SkippedException("Printer not configured or available."
                    + " Test cannot continue.");
        }

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .testUI(PrintParenString::new)
                .rows((int) INSTRUCTIONS.lines().count() + 1)
                .columns(45)
                .build()
                .awaitAndCheck();
    }

    public PrintParenString() {
        super("JDK 1.2 drawString Printing");

        c = new TextCanvas();
        add("Center", c);

        Button printButton = new Button("Print");
        printButton.addActionListener(this);
        add("South", printButton);

        pack();
    }

    public void actionPerformed(ActionEvent e) {

        PrinterJob pj = PrinterJob.getPrinterJob();

        if (pj != null && pj.printDialog()) {

            pj.setPrintable(c);
            try {
                pj.print();
            } catch (PrinterException pe) {
                pe.printStackTrace();
            } finally {
                System.out.println("PRINT RETURNED");
            }
        }
    }

    class TextCanvas extends Panel implements Printable {

        public int print(Graphics g, PageFormat pgFmt, int pgIndex) {

            if (pgIndex > 0)
                return Printable.NO_SUCH_PAGE;

            Graphics2D g2d = (Graphics2D) g;
            g2d.translate(pgFmt.getImageableX(), pgFmt.getImageableY());

            paint(g);

            return Printable.PAGE_EXISTS;
        }

        public void paint(Graphics g1) {
            Graphics2D g = (Graphics2D) g1;

            String str = "String containing unclosed parenthesis (.";
            g.drawString(str, 20, 40);

        }

        public Dimension getPreferredSize() {
            return new Dimension(450, 250);
        }
    }
}