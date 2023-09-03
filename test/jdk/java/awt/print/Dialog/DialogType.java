/*
 * Copyright (c) 2007, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.print.PrinterJob;
import java.lang.reflect.InvocationTargetException;
import javax.print.attribute.Attribute;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.DialogTypeSelection;
import jtreg.SkippedException;

/*
 * @test
 * @bug 6568874
 * @key printer
 * @library /java/awt/regtesthelpers
 * @library /test/lib
 * @build PassFailJFrame
 * @build jtreg.SkippedException
 * @summary Verify the native dialog works with attribute sets.
 * @run main/manual DialogType
 */

public class DialogType {

    static String instruction = """
            This test assumes and requires that you have a printer installed.
            It verifies that the dialogs behave properly when using new API,
            to optionally select a native dialog where one is present.
            Two dialogs are shown in succession.,
            The test passes as long as no exceptions are thrown, *AND*,
            if running on Windows only, the first dialog is a native windows,
            control which differs in appearance from the second dialog.
            Note: You can either press 'ESCAPE' button or click on the 'Cancel'
            to close print dialog.
            """;

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {

        if (PrinterJob.lookupPrintServices().length == 0) {
            throw new SkippedException("Printer not configured or available."
                    + " Test cannot continue.");
        }

        PassFailJFrame passFailJFrame = new PassFailJFrame.Builder()
                .title("Test Instructions Frame")
                .instructions(instruction)
                .testTimeOut(10)
                .rows(8)
                .columns(45)
                .build();
        PassFailJFrame.positionTestWindow(null,
                PassFailJFrame.Position.HORIZONTAL);

        PrinterJob job = PrinterJob.getPrinterJob();
        PrintRequestAttributeSet aset = new HashPrintRequestAttributeSet();
        aset.add(DialogTypeSelection.NATIVE);
        job.printDialog(aset);

        aset.add(DialogTypeSelection.COMMON);
        job.printDialog(aset);

        passFailJFrame.awaitAndCheck();
    }
}

