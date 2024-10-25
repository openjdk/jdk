/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 6275359
 * @summary Test to verify system menu of a dialog on win32
 * @requires (os.family == "windows")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @compile PrintToFileFrame.java
 * @compile PrintToFileGranted.java
 * @run main/manual/policy=granted/othervm PrintToFileGranted
 */

public class PrintToFileGranted {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS;
        if (isPrintSupport()) {
            INSTRUCTIONS = """
                    1. Click on 'Show file dialog' button A print dialog will come up
                    2. If checkbox 'Print to file' is enabled then the test passed
                       else the test failed
                    3. Close the print dialog before pressing PASS or FAIL buttons
                    """;
        } else {
            INSTRUCTIONS = """
                    1. The test requires printer installed in your system,
                       but there is no printers found
                       Please install one and re-run the test
                    """;
        }

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(new PrintToFileFrame())
                .build()
                .awaitAndCheck();
    }

    public static boolean isPrintSupport() {
        PrinterJob pj = PrinterJob.getPrinterJob();
        return pj.getPrintService() != null;
    }
}
