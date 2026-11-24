/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @key printer
 * @bug 8276027
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @requires (os.family == "mac")
 * @summary Verifies that PDF to Preview opens Preview without spooling to a printer.
 * @run main/manual PrintToPreviewTest
 */

import java.awt.Graphics2D;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

public class PrintToPreviewTest {

    private static final String PAGE_TEXT = "Preview regression test " + System.currentTimeMillis();

    private static final String INSTRUCTIONS = """
            This test requires a configured printer on macOS.

            1. Open the selected printer's queue so you can monitor new jobs.
            2. In the print dialog, open the PDF menu and choose Open in Preview
               (or Preview, depending on the macOS version).
            3. Verify that Preview opens a one-page document containing:
               %s
            4. Verify that no job for this document reaches the printer queue
               and no page is printed.
            5. You can also repeat this process for Send in Mail, Save to iCloud
               Drive and Save to Web Receipts.

            Click Pass only if both the preview and printer-queue checks pass.
            Click Fail if Preview does not open, the page is incorrect, or the
            document is sent to the printer. Close the test document in Preview
            when finished.
            """.formatted(PAGE_TEXT);

    public static void main(String[] args) throws Exception {
        PrinterJob job = PrinterJob.getPrinterJob();

        job.setJobName(PAGE_TEXT);
        job.setPrintable((graphics, pageFormat, pageIndex) -> {
            if (pageIndex != 0) {
                return Printable.NO_SUCH_PAGE;
            }

            Graphics2D g = (Graphics2D) graphics.create();

            try {
                g.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
                g.drawString(PAGE_TEXT, 20, 40);
                g.drawRect(20, 60, 200, 100);
            } finally {
                g.dispose();
            }

            return Printable.PAGE_EXISTS;
        });

        PassFailJFrame passFailJFrame = PassFailJFrame.builder()
                .title("PDF to Preview instructions")
                .instructions(INSTRUCTIONS)
                .columns(65)
                .build();

        try {
            if (job.printDialog()) {
                job.print();
            } else {
                PassFailJFrame.forceFail("Print dialog was cancelled.");
            }
        } catch (PrinterException e) {
            PassFailJFrame.forceFail("Printing failed: " + e);
        }

        passFailJFrame.awaitAndCheck();
    }
}
