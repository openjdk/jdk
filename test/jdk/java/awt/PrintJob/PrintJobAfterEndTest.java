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
 * @test
 * @bug 8370141 8370637
 * @summary  Test no crash printing to Graphics after job is ended.
 * @key headful printer
 * @run main PrintJobAfterEndTest
 */

import java.awt.Frame;
import java.awt.Graphics;
import java.awt.JobAttributes;
import java.awt.JobAttributes.DialogType;
import java.awt.JobAttributes.DestinationType;
import java.awt.PageAttributes;
import java.awt.PrintJob;
import java.awt.Toolkit;
import java.util.concurrent.CountDownLatch;

public class PrintJobAfterEndTest {

    public static void main(String[] args) throws Exception {

        JobAttributes jobAttributes = new JobAttributes();
        jobAttributes.setDialog(DialogType.NONE);
        jobAttributes.setDestination(DestinationType.FILE);
        jobAttributes.setFileName("out.prn");

        PageAttributes pageAttributes = new PageAttributes();

        Frame f = new Frame();
        Toolkit tk = f.getToolkit();

        for (int i = 0; i < 500; i++) {
            PrintJob job = tk.getPrintJob(f, "Print Crash Test", jobAttributes, pageAttributes);
            if (job != null) {
                Graphics g = job.getGraphics();
                CountDownLatch latch = new CountDownLatch(1);

                Thread endThread = new Thread(() -> {
                    try {
                        latch.await();
                        job.end();
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                });

                Thread drawThread = new Thread(() -> {
                    try {
                        latch.await();
                        g.clearRect(10, 10, 100, 100);
                        g.drawRect(0, 300, 200, 400);
                        g.fillRect(0, 300, 200, 400);
                        g.drawLine(0, 100, 200, 100);
                        g.drawString("Hello", 200, 200);
                        g.drawOval(200, 200, 200, 200);
                        int[] pts = new int[] { 10, 200, 100 };
                        g.drawPolyline(pts, pts, pts.length);
                        g.drawPolygon(pts, pts, pts.length);
                        g.fillPolygon(pts, pts, pts.length);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                });

                if ( i % 2 == 0) {
                    drawThread.start();
                    endThread.start();
                } else {
                    endThread.start();
                    drawThread.start();
                }
                latch.countDown();

                endThread.join();
                drawThread.join();
            }
        }
    }
}
