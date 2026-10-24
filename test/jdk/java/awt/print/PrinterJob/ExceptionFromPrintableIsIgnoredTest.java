/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8262731 8268675 8381208
 * @key printer
 * @summary Verify that "PrinterJob.print" throws the expected exception,
 *          if "Printable.print" throws an exception.
 * @run main ExceptionFromPrintableIsIgnoredTest MAIN PE
 * @run main ExceptionFromPrintableIsIgnoredTest MAIN RE
 * @run main ExceptionFromPrintableIsIgnoredTest EDT PE
 * @run main ExceptionFromPrintableIsIgnoredTest EDT RE
 */

import java.awt.Graphics;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.File;
import java.lang.reflect.InvocationTargetException;

import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Destination;
import javax.swing.SwingUtilities;

public class ExceptionFromPrintableIsIgnoredTest {
    private enum TestThreadType {MAIN, EDT}
    private enum TestExceptionType {PE, RE}

    private volatile Throwable printError;

    private volatile PrinterException thrownPE;
    private volatile RuntimeException thrownRE;

    public static void main(String[] args) {
        if (PrinterJob.lookupPrintServices().length == 0) {
            throw new RuntimeException("Printer not configured or available.");
        }

        if (args.length < 2) {
            throw new RuntimeException("Two arguments are expected:"
                    + " test thread type and test exception type.");
        }

        new ExceptionFromPrintableIsIgnoredTest(
            TestThreadType.valueOf(args[0]),
            TestExceptionType.valueOf(args[1]));
    }

    private ExceptionFromPrintableIsIgnoredTest(
            final TestThreadType threadType,
            final TestExceptionType exceptionType) {
        System.out.println(String.format(
                "Test started. threadType='%s', exceptionType='%s'",
                threadType, exceptionType));

        printError = null;

        if (threadType == TestThreadType.MAIN) {
            runTest(exceptionType);
        } else if (threadType == TestThreadType.EDT) {
            try {
                SwingUtilities.invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        runTest(exceptionType);
                    }
                });
            } catch (InterruptedException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }

        if (printError == null) {
            throw new RuntimeException("No exception was thrown.");
        } else if (!(printError instanceof PrinterException)) {
            throw new RuntimeException("Unexpected exception was thrown.");
        }

        if (exceptionType == TestExceptionType.PE
                && thrownPE != printError) {
            throw new RuntimeException(
                    "Expected the same instance of PrinterException");
        }

        if (exceptionType == TestExceptionType.RE
                && thrownRE != printError.getCause()) {
            throw new RuntimeException(
                    "Expected the cause of PrinterException to be the thrown exception");
        }

        System.out.println("Test passed.");
    }

    private void runTest(final TestExceptionType exceptionType) {
        PrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet();
        final File file = new File("out.prn");
        attrs.add(new Destination(file.toURI()));

        PrinterJob job = PrinterJob.getPrinterJob();

        job.setPrintable(new Printable() {
            @Override
            public int print(Graphics graphics, PageFormat pageFormat,
                    int pageIndex) throws PrinterException {
                if (pageIndex > 1) {
                    return NO_SUCH_PAGE;
                }
                if (exceptionType == TestExceptionType.PE) {
                    throw thrownPE = new PrinterException(
                        "Exception from 'Printable.print'.");
                } else if (exceptionType == TestExceptionType.RE) {
                    throw thrownRE = new RuntimeException(
                        "Exception from 'Printable.print'.");
                }
                return PAGE_EXISTS;
            }
        });

        try {
            job.print(attrs);
        } catch (Throwable t) {
            printError = t;

            System.out.println("'PrinterJob.print' threw the exception:");
            t.printStackTrace(System.out);
        } finally {
            file.delete();
        }
    }
}
