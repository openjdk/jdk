/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8334366
 * @key headful printer
 * @summary Verifies original pageobject is returned unmodified
 *          on cancelling pagedialog
 * @requires (os.family == "windows")
 * @run main PageDialogCancelTest
 */

import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.print.PageFormat;
import java.awt.print.PrinterJob;

public class PageDialogCancelTest {

    public static void main(String[] args) throws Exception {
        PrinterJob pj = PrinterJob.getPrinterJob();
        PageFormat oldFormat = new PageFormat();
        Robot robot = new Robot();
        Thread t1 = new Thread(() -> {
            robot.delay(2000);
            robot.keyPress(KeyEvent.VK_ESCAPE);
            robot.keyRelease(KeyEvent.VK_ESCAPE);
            robot.waitForIdle();
        });
        t1.start();
        PageFormat newFormat = pj.pageDialog(oldFormat);
        if (!newFormat.equals(oldFormat)) {
            throw new RuntimeException("Original PageFormat not returned on cancelling PageDialog");
        }
    }
}

