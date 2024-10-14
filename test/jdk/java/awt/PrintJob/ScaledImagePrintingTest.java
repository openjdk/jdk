/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.PrintJob;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/*
 * @test
 * @bug 4257962
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary tests that scaled images are printed at resolution greater than 72dpi
 * @run main/manual ScaledImagePrintingTest
 */

public class ScaledImagePrintingTest {

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                Press 'Print' button from the test UI.

                The test will bring up a print dialog. Select a printer and proceed.
                Verify that the output is a series of a horizontal lines in a
                rectangular box in the center of the page.

                If output is as mentioned above, press Pass else Fail.""";

        PassFailJFrame.builder()
                .title("ScaledImagePrintingTest Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(40)
                .testTimeOut(5)
                .testUI(ScaledImagePrintingTest::createUI)
                .logArea(8)
                .build()
                .awaitAndCheck();
    }

    private static Frame createUI() {
        Frame frame = new Frame("ResolutionTest");
        Button b = new Button("Print");
        b.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                PrintJob pj = frame.getToolkit().getPrintJob(frame, "ResolutionTest", null);
                PassFailJFrame.log("Printing code started.");
                if (pj != null) {
                    Graphics g = pj.getGraphics();
                    g.setColor(Color.black);
                    int w = 200;
                    int h = 200;
                    Image image = frame.createImage(w, h);
                    Graphics imageGraphics = image.getGraphics();
                    Dimension d = pj.getPageDimension();
                    imageGraphics.setColor(Color.black);
                    for (int i = 0; i < h; i += 20) {
                        imageGraphics.drawLine(0, i, w, i);
                    }
                    g.translate(d.width / 2, d.height / 2);
                    g.drawImage(image, -w / 8, -h / 8, w / 4, h / 4, frame);
                    g.setColor(Color.black);
                    g.drawRect(-w / 4, -h / 4, w / 2, h / 2);
                    imageGraphics.dispose();
                    g.dispose();
                    pj.end();
                }
                PassFailJFrame.log("Printing code finished.");
            }
        });
        frame.add(b);
        frame.setSize(50, 50);
        return frame;
    }
}
