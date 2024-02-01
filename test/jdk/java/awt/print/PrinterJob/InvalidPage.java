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
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

import jtreg.SkippedException;

/*
 * @test InvalidPage.java
 * @bug 4671634 6506286
 * @summary Invalid page format can crash win32 JRE
 * @key printer
 * @library /test/lib /java/awt/regtesthelpers
 * @build PassFailJFrame jtreg.SkippedException
 * @run main/manual InvalidPage
 */
public class InvalidPage extends Frame implements Printable {

    PrinterJob pJob;
    PageFormat pf;

    public InvalidPage() {
        super("Validate Page Test");
        pJob = PrinterJob.getPrinterJob();
        pf = pJob.defaultPage();
        Paper p = pf.getPaper();
        p.setImageableArea(0, 0, p.getWidth(), p.getHeight());
        pf.setPaper(p);
        setLayout(new FlowLayout());
        Panel panel = new Panel();
        Button printButton = new Button("Print");
        printButton.addActionListener(e -> {
            if (pJob.printDialog()) {
                pJob.setPrintable(InvalidPage.this, pf);
                try {
                    pJob.print();
                } catch (PrinterException pe) {
                    pe.printStackTrace();
                }
            }
        });
        panel.add(printButton);
        add(panel);
        pack();
    }

    public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) {

        if (pageIndex > 1) {
            return Printable.NO_SUCH_PAGE;
        }

        Graphics2D g2d = (Graphics2D) graphics;

        g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
        g2d.drawString("ORIGIN", 30, 30);
        g2d.drawString("X THIS WAY", 200, 50);
        g2d.drawString("Y THIS WAY", 60, 200);
        g2d.drawRect(0, 0, (int) pageFormat.getImageableWidth(),
                (int) pageFormat.getImageableHeight());
        if (pageIndex == 0) {
            g2d.setColor(Color.black);
        } else {
            g2d.setColor(new Color(0, 0, 0, 128));
        }
        g2d.drawRect(1, 1, (int) pageFormat.getImageableWidth() - 2,
                (int) pageFormat.getImageableHeight() - 2);

        g2d.drawLine(0, 0,
                (int) pageFormat.getImageableWidth(),
                (int) pageFormat.getImageableHeight());
        g2d.drawLine((int) pageFormat.getImageableWidth(), 0,
                0, (int) pageFormat.getImageableHeight());
        g2d.dispose();
        return Printable.PAGE_EXISTS;
    }

    private static final String INSTRUCTIONS =
            " You must have a printer available to perform this test\n" +
            " Press the print button, which brings up a print dialog and\n" +
            " in the dialog select a printer and press the print button\n" +
            " in the dialog. Repeat for as many printers as you have installed\n" +
            " On solaris and linux just one printer is sufficient\n" +
            " Collect the output and examine it, each print job has two pages\n" +
            " of very similar output, except that the 2nd page of the job may\n" +
            " appear in a different colour, and the output near the edge of\n" +
            " the page may be clipped. This is OK. Hold up both pieces of paper\n" +
            " to the light and confirm that the lines and text (where present)\n" +
            " are positioned identically on both pages\n" +
            " The test fails if the JRE crashes, or if the output from the two\n" +
            " pages of a job is aligned differently";

    public static void main(String[] args) throws Exception {

        if (PrinterJob.lookupPrintServices().length == 0) {
            throw new SkippedException("Printer not configured or available."
                    + " Test cannot continue.");
        }

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .testUI(InvalidPage::new)
                .rows((int) INSTRUCTIONS.lines().count() + 1)
                .columns(45)
                .build()
                .awaitAndCheck();
    }
}
