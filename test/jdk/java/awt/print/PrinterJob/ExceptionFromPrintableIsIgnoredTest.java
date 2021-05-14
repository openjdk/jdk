/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

/* @test
   @bug 8262731
   @key headful printer
   @summary Verify that "PrinterJob.print" throws the exception, if
            "Printable.print" throws "PrinterException".
   @run main/manual ExceptionFromPrintableIsIgnoredTest MAIN
   @run main/manual ExceptionFromPrintableIsIgnoredTest EDT
 */

import java.awt.Graphics;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.lang.reflect.InvocationTargetException;
import javax.swing.SwingUtilities;

public class ExceptionFromPrintableIsIgnoredTest {
    private enum TestThreadType {MAIN, EDT}

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new RuntimeException("Test thread type is not specified.");
        }

        TestThreadType threadType = TestThreadType.valueOf(args[0]);
        new ExceptionFromPrintableIsIgnoredTest(threadType);
    }

    public ExceptionFromPrintableIsIgnoredTest(TestThreadType threadType) {
        System.out.println(String.format(
                "Test started. threadType='%s'", threadType));

        if (threadType == TestThreadType.MAIN) {
            runTest();
        } else if (threadType == TestThreadType.EDT) {
            try {
                SwingUtilities.invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        runTest();
                    }
                });
            } catch (InterruptedException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
        System.out.println("Test passed.");
    }

    private void runTest() {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintable(new Printable() {
            @Override
            public int print(Graphics graphics, PageFormat pageFormat,
                    int pageIndex) throws PrinterException {
                if (pageIndex > 1) {
                    return NO_SUCH_PAGE;
                }
                throw new PrinterException("Exception from Printable.print");
            }
        });
        if (job.printDialog()) {
            Exception printEx = null;
            try {
                job.print();
            } catch (PrinterException pe) {
                printEx = pe;
            }

            if (printEx != null) {
                System.out.println("'PrinterJob.print' threw the exception:");
                printEx.printStackTrace(System.out);
            } else {
                throw new RuntimeException(
                    "'PrinterJob.print' did not throw any exception.");
            }
        } else {
            throw new RuntimeException("User canceled the print dialog.");
        }
    }
}
