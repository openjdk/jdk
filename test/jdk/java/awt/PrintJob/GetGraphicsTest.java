/*
 * Copyright (c) 2004, 2025, Oracle and/or its affiliates. All rights reserved.
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
  @bug 5051056 8367702
  @key headful printer
  @summary  PrintJob.getGraphics() should return null after PrintJob.end() is called.
  @run main GetGraphicsTest
*/

import java.awt.Frame;
import java.awt.Graphics;
import java.awt.JobAttributes;
import java.awt.PageAttributes;
import java.awt.PrintJob;
import java.awt.Toolkit;

public class GetGraphicsTest {

    public static void main(String[] args) {

        JobAttributes ja = new JobAttributes();
        ja.setDialog(JobAttributes.DialogType.NONE);
        PageAttributes pa = new PageAttributes();
        pa.setOrigin( PageAttributes.OriginType.PRINTABLE);

        Toolkit tk = Toolkit.getDefaultToolkit();
        PrintJob pjob = tk.getPrintJob(new Frame(),"Printing Test", ja,pa);
        if (pjob != null) {
            pjob.end();
            Graphics pg = pjob.getGraphics();
            if (pg == null) {
                System.out.println("Graphics is null, TEST PASSES");
            }
            else {
               throw new RuntimeException("Graphics is NOT null, TEST FAILED");
            }
        }
    }
}
