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
import java.awt.FlowLayout;
import java.awt.print.PageFormat;
import java.awt.print.PrinterJob;

import javax.swing.JFrame;

/*
 * @test
 * @bug 4784285 4785920 5024549
 * @key printer
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary check whether Print- and Page- dialogs are modal and correct window
 *  activated after their closing
 * @run main/manual PrinterDialogsModalityTest
 */

public class PrinterDialogsModalityTest {
    private static final String INSTRUCTIONS =
            """
             After the test starts, you will see a frame titled "Test Frame"
             with two buttons: "Page Dialog" and "Print Dialog".
             1. Make the "Test Frame" active by clicking on title.
             2. Press "Page Dialog" button and a page dialog should popup.
             3. Make sure page dialog is modal. (Modal in this case means that
                it blocks the user from interacting with other windows in the
                same application, like this instruction window. You may still be
                able to interact with unrelated applications on the desktop.).
             4. Close the dialog (either cancel it or press ok).
             5. Make sure the frame is still active.
             6. Press "Print Dialog" button, print dialog should popup.
             7. Repeat steps 3-5.

             If you are able to execute all steps successfully then the test
             passes, otherwise it fails.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame
                .builder()
                .title("PrinterDialogsModalityTest Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(40)
                .testUI(PrinterDialogsModalityTest::createAndShowGUI)
                .build()
                .awaitAndCheck();
    }

    public static JFrame createAndShowGUI() {
        JFrame frame = new JFrame("Test Frame");
        frame.setLayout(new FlowLayout());

        Button page = new Button("Page Dialog");
        page.addActionListener(e -> {
            PrinterJob prnJob = PrinterJob.getPrinterJob();
            prnJob.pageDialog(new PageFormat());
        });
        Button print = new Button("Print Dialog");
        print.addActionListener(e -> {
            PrinterJob prnJob = PrinterJob.getPrinterJob();
            prnJob.printDialog();
        });
        frame.add(page);
        frame.add(print);
        frame.pack();
        return frame;
    }
}
