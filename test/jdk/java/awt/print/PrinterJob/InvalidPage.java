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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;

/*
 * @test InvalidPage.java
 * @bug 4671634 6506286
 * @summary Invalid page format can crash win32 JRE
 * @key printer
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual InvalidPage
 */
public class InvalidPage implements Printable {
    private static JComponent createTestUI() {
        JButton b = new JButton("Print");
        b.addActionListener((ae) -> {
            try {
                PrinterJob job = PrinterJob.getPrinterJob();
                PageFormat pf = job.defaultPage();
                Paper p = pf.getPaper();
                p.setImageableArea(0, 0, p.getWidth(), p.getHeight());
                pf.setPaper(p);
                job.setPrintable(new InvalidPage(), pf);
                if (job.printDialog()) {
                    job.print();
                }
            } catch (PrinterException ex) {
                ex.printStackTrace();
                String msg = "PrinterException: " + ex.getMessage();
                JOptionPane.showMessageDialog(b, msg, "Error occurred",
                        JOptionPane.ERROR_MESSAGE);
                PassFailJFrame.forceFail(msg);
            }
        });

        Box main = Box.createHorizontalBox();
        main.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        main.add(Box.createHorizontalGlue());
        main.add(b);
        main.add(Box.createHorizontalGlue());
        return main;
    }

    @Override
    public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) {
        if (pageIndex > 1) {
            return Printable.NO_SUCH_PAGE;
        }

        Graphics2D g2d = (Graphics2D) graphics;
        g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
        g2d.drawString("ORIGIN", 30, 30);
        g2d.drawString("X THIS WAY", 200, 50);
        g2d.drawString("Y THIS WAY", 60, 200);
        g2d.drawRect(0, 0,
                (int) pageFormat.getImageableWidth(),
                (int) pageFormat.getImageableHeight());
        if (pageIndex == 0) {
            g2d.setColor(Color.black);
        } else {
            g2d.setColor(new Color(0, 0, 0, 128));
        }
        g2d.drawRect(1, 1,
                (int) pageFormat.getImageableWidth() - 2,
                (int) pageFormat.getImageableHeight() - 2);
        g2d.drawLine(0, 0,
                (int) pageFormat.getImageableWidth(),
                (int) pageFormat.getImageableHeight());
        g2d.drawLine((int) pageFormat.getImageableWidth(), 0,
                0, (int) pageFormat.getImageableHeight());

        return Printable.PAGE_EXISTS;
    }

    private static final String INSTRUCTIONS =
            " Press the print button, which brings up a print dialog.\n" +
            " In the dialog select a printer and press the print button.\n\n" +
            " Repeat for all the printers as you have installed\n" +
            " On Solaris and Linux just one printer is sufficient.\n\n" +
            " Collect the output and examine it, each print job has two pages\n" +
            " of very similar output, except that the 2nd page of the job may\n" +
            " appear in a different colour, and the output near the edge of\n" +
            " the page may be clipped. This is OK. Hold up both pieces of paper\n" +
            " to the light and confirm that the lines and text (where present)\n" +
            " are positioned identically on both pages\n\n" +
            " The test fails if the output from the two\n" +
            " pages of a job is aligned differently";

    public static void main(String[] args) throws Exception {
        if (PrinterJob.lookupPrintServices().length == 0) {
            throw new RuntimeException("Printer not configured or available.");
        }

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .testTimeOut(10)
                .splitUI(InvalidPage::createTestUI)
                .rows((int) INSTRUCTIONS.lines().count() + 1)
                .columns(45)
                .build()
                .awaitAndCheck();
    }
}
