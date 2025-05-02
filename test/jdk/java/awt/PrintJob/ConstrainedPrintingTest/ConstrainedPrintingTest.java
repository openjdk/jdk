/*
 * Copyright (c) 1998, 2022, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4116029 4300383
  @key printer
  @summary verify that child components can draw only inside their
           visible bounds
  @library /test/lib
  @library /javax/accessibility/manual
  @build lib.ManualTestFrame
  @build lib.TestResult
  @build jtreg.SkippedException
  @run main/manual ConstrainedPrintingTest
*/

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.JobAttributes;
import java.awt.PageAttributes;
import java.awt.Panel;
import java.awt.PrintJob;
import java.awt.Rectangle;
import java.awt.print.PrinterJob;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JEditorPane;
import jtreg.SkippedException;
import lib.ManualTestFrame;
import lib.TestResult;

public class ConstrainedPrintingTest {

    public static void createTestUI() {
        Frame frame = new Frame("PrintTest");
        Button button = new Button("Print");
        Panel panel = new Panel();
        Component testComponent = new Component() {
            public void paint(Graphics g) {
                ConstrainedPrintingTest.paintOutsideBounds(this, g, Color.green);
            }
            public Dimension getPreferredSize() {
                return new Dimension(100, 100);
            }
        };

        Canvas testCanvas = new Canvas() {
            public void paint(Graphics g) {
                ConstrainedPrintingTest.paintOutsideBounds(this, g, Color.red);
                // The frame is sized so that only the upper part of
                // the canvas is visible. We draw on the lower part,
                // so that we can verify that the output is clipped
                // by the parent container bounds.
                Dimension panelSize = panel.getSize();
                Rectangle b = getBounds();
                g.setColor(Color.red);
                g.setClip(null);
                for (int i = panelSize.height - b.y; i < b.height; i+= 10) {
                    g.drawLine(0, i, b.width, i);
                }
            }
            public Dimension getPreferredSize() {
                return new Dimension(100, 100);
            }
        };

        button.addActionListener((actionEvent) -> {
            PageAttributes pa = new PageAttributes();
            pa.setPrinterResolution(36);
            PrintJob pjob = frame.getToolkit().getPrintJob(frame, "NewTest",
                    new JobAttributes(), pa);
            if (pjob != null) {
                Graphics pg = pjob.getGraphics();
                if (pg != null) {
                    pg.translate(20, 20);
                    frame.printAll(pg);
                    pg.dispose();
                }
                pjob.end();
            }
        });

        panel.setBackground(Color.white);
        panel.setLayout(new FlowLayout(FlowLayout.CENTER, 20, 20));
        panel.add(testComponent);
        panel.add(testCanvas);

        frame.setLayout(new BorderLayout());
        frame.add(button, BorderLayout.NORTH);
        frame.add(panel, BorderLayout.CENTER);
        frame.setSize(200, 250);
        frame.validate();
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void paintOutsideBounds(Component comp,
                                          Graphics g,
                                          Color color) {
        Dimension dim = comp.getSize();
        g.setColor(color);

        g.setClip(0, 0, dim.width * 2, dim.height * 2);
        for (int i = 0; i < dim.height * 2; i += 10) {
            g.drawLine(dim.width, i, dim.width * 2, i);
        }

        g.setClip(null);
        for (int i = 0; i < dim.width * 2; i += 10) {
            g.drawLine(i, dim.height, i, dim.height * 2);
        }

        g.setClip(new Rectangle(0, 0, dim.width * 2, dim.height * 2));
        for (int i = 0; i < dim.width; i += 10) {
            g.drawLine(dim.width * 2 - i, 0, dim.width * 2, i);
        }
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, IOException {

        if (PrinterJob.lookupPrintServices().length == 0) {
            throw new SkippedException("Printer not configured or available."
                    + " Test cannot continue.");
        }

        String instruction = """
                1.Look at the frame titled "PrintTest". If you see green or,
                red lines on the white area below the "Print" button, the,
                test fails. Otherwise go to step 2.,
                2.Press "Print" button. The print dialog will appear.
                Select, a printer and proceed. Look at the output.
                If you see multiple, lines outside of the frame bounds
                or in the white area below, the image of the "Print"
                button, the test fails. Otherwise,the test passes.
                """;
        Consumer<JEditorPane> testInstProvider = e -> {
            e.setContentType("text/plain");
            e.setText(instruction);
        };

        Supplier<TestResult> resultSupplier = ManualTestFrame.showUI(
                "Tests ConstrainedPrintingTest",
                "Wait until the Test UI is seen", testInstProvider);
        EventQueue.invokeAndWait(ConstrainedPrintingTest::createTestUI);

        //this will block until user decision to pass or fail the test
        TestResult  testResult = resultSupplier.get();
        ManualTestFrame.handleResult(testResult,"ConstrainedPrintingTest");
    }
}

