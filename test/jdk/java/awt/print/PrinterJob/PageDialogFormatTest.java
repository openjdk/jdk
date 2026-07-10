/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8334366 8334868
 * @key headful printer
 * @summary Verifies PageFormat object returned from PrinterJob.pageDialog
 *          changes to landscape orientation when "Landscape" is selected
 * @requires (os.family == "windows")
 * @run main PageDialogFormatTest
 */

import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.print.PageFormat;
import java.awt.print.PrinterJob;

public class PageDialogFormatTest {

    public static void main(String[] args) throws Exception {
        PrinterJob pj = PrinterJob.getPrinterJob();
        PageFormat oldFormat = new PageFormat();
        Robot robot = new Robot();
        Thread t1 = new Thread(() -> {
            robot.delay(2000);
            // Select Landscape orientation
            robot.keyPress(KeyEvent.VK_ALT);
            robot.keyPress(KeyEvent.VK_A);
            robot.keyRelease(KeyEvent.VK_A);
            robot.keyRelease(KeyEvent.VK_ALT);
            // Press OK
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);
            robot.waitForIdle();
        });
        t1.start();
        PageFormat newFormat = pj.pageDialog(oldFormat);
        if (newFormat.getOrientation() != PageFormat.LANDSCAPE) {
            throw new RuntimeException("PageFormat didn't change to landscape");
        }
    }
}
