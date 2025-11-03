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

/**
 * @test
 * @bug 6568874
 * @key printer
 * @summary Verify the native dialog works with attribute sets.
 * @library /java/awt/regtesthelpers /test/lib
 * @build PassFailJFrame
 * @run main/manual DialogType
 */

import java.awt.print.PrinterJob;

import javax.print.attribute.Attribute;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.DialogTypeSelection;

import jtreg.SkippedException;

public class DialogType {
    private static PrinterJob job;

    private static final String INSTRUCTIONS = """
        Two print dialogs are shown in succession.
        Click Cancel in the dialogs to close them.

        On macOS & on Windows, the first dialog is a native
        dialog provided by the OS, the second dialog is
        implemented in Swing, the dialogs differ in appearance.

        The test passes as long as no exceptions are thrown.
        (If there's an exception, the test will fail automatically.)

        The test verifies that the dialogs behave properly when using new API
        to optionally select a native dialog where one is present.
    """;

    public static void main(String[] args) throws Exception {
        job = PrinterJob.getPrinterJob();
        if (job.getPrintService() == null) {
            throw new SkippedException("Test skipped, printer is unavailable");
        }
        PassFailJFrame passFailJFrame = PassFailJFrame.builder()
            .instructions(INSTRUCTIONS)
            .columns(40)
            .build();
        testDialogType();
        passFailJFrame.awaitAndCheck();
    }

    private static void testDialogType() {
        setPrintDialogAttributes(DialogTypeSelection.NATIVE);
        setPrintDialogAttributes(DialogTypeSelection.COMMON);
    }

    private static void setPrintDialogAttributes(DialogTypeSelection selection) {
        PrintRequestAttributeSet aset = new HashPrintRequestAttributeSet();
        aset.add(selection);
        job.printDialog(aset);
        Attribute[] attrs = aset.toArray();
        for (int i = 0; i < attrs.length; i++) {
            System.out.println(attrs[i]);
        }
    }
}
