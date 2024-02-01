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
import java.awt.print.Printable;
import java.awt.print.PrinterAbortException;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

import jtreg.SkippedException;

/*
 * @test
 * @bug 4245280
 * @key printer
 * @summary PrinterJob not cancelled when PrinterJob.cancel() is used
 * @library /test/lib /java/awt/regtesthelpers
 * @build PassFailJFrame jtreg.SkippedException
 * @run main/manual PrinterJobCancel
 */
public class PrinterJobCancel extends Thread implements Printable {

    PrinterJob pj;
    boolean okayed;

    private static final String INSTRUCTIONS =
             "Test that print job cancellation works.\n" +
             "You must have a printer available to perform this test.\n" +
             "This test silently starts a print job and while the job is\n" +
             "still being printed, cancels the print job\n" +
             "You should see a message on System.out that the job\n" +
             "was properly cancelled.\n" +
             "You will need to kill the application manually since regression\n" +
             "tests apparently aren't supposed to call System.exit()";

    public static void main(String[] args) throws Exception {

        if (PrinterJob.lookupPrintServices().length == 0) {
            throw new SkippedException("Printer not configured or available."
                    + " Test cannot continue.");
        }

        PassFailJFrame passFailJFrame = new PassFailJFrame.Builder()
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 1)
                .columns(45)
                .build();

        PrinterJobCancel pjc = new PrinterJobCancel();
        if (pjc.okayed) {
            pjc.start();
            try {
                Thread.sleep(5000);
                pjc.pj.cancel();
            } catch (InterruptedException e) {
            }
        }

        passFailJFrame.awaitAndCheck();
    }

    public PrinterJobCancel() {

        pj = PrinterJob.getPrinterJob();
        pj.setPrintable(this);
        okayed = pj.printDialog();
    }

    public void run() {
        boolean cancelWorked = false;
        try {
            pj.print();
        } catch (PrinterAbortException paex) {
            cancelWorked = true;
            System.out.println("Job was properly cancelled and we");
            System.out.println("got the expected PrintAbortException");
        } catch (PrinterException prex) {
            System.out.println("This is wrong .. we shouldn't be here");
            System.out.println("Looks like a test failure");
            prex.printStackTrace();
            //throw prex;
        } finally {
            System.out.println("DONE PRINTING");
            if (!cancelWorked) {
                System.out.println("Looks like the test failed - we didn't get");
                System.out.println("the expected PrintAbortException ");
            }
        }
    }

    public int print(Graphics g, PageFormat pagef, int pidx) {

        if (pidx > 5) {
            return (Printable.NO_SUCH_PAGE);
        }

        Graphics2D g2d = (Graphics2D) g;
        g2d.translate(pagef.getImageableX(), pagef.getImageableY());
        g2d.setColor(Color.black);

        g2d.drawString(("This is page" + (pidx + 1)), 60, 80);
        g2d.dispose();
        // Need to slow things down a bit .. important not to try this
        // on the event dispathching thread of course.
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
        }

        return Printable.PAGE_EXISTS;
    }
}
