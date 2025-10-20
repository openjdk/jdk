/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @bug 8370141
  @summary  Test no crash printing to Graphics after job is ended.
  @key headful printer
  @run main PrintJobAfterEndTest
*/

import java.awt.Frame;
import java.awt.JobAttributes;
import java.awt.PageAttributes;
import java.awt.PrintJob;
import java.awt.Graphics;
import java.awt.JobAttributes.DialogType;

public class PrintJobAfterEndTest {

    public static void main(String[] args) {

        JobAttributes jobAttributes = new JobAttributes();
        jobAttributes.setDialog(DialogType.NONE);
        PageAttributes pageAttributes = new PageAttributes();

        Frame f = new Frame();
        PrintJob job = f.getToolkit().getPrintJob(f, "Portrait Test", jobAttributes,
                                          pageAttributes);
        if (job != null) {
            Graphics g = job.getGraphics();
            job.end();
            g.drawLine(0, 200, 200, 200);
            g.dispose();
        }
    }
}
