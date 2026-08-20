/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6328248
 * @summary JProgessBar should be printed on paper with PrintJob
 * @key printer
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual PrintProgressBarTest
 */

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import java.awt.Graphics;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.PrintJob;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Properties;

public class PrintProgressBarTest {

    private static final String INSTRUCTIONS = """
        This test shows a frame with a progressbar and a "Print" button.
            1. Click the 'Print' button on the frame
            2. Select a printer/pdf-printer in the print dialog and proceed
               If the progressbar is printed along with progress string
               test PASSED else FAILED.
        """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(PrintProgressBarTest::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {
        JPanel panel = new JPanel(new FlowLayout());
        JProgressBar bar = new JProgressBar();
        bar.setOpaque(true);
        bar.setStringPainted(true);
        bar.setValue(50);
        panel.add("Center", bar);

        JFrame frame = new JFrame("Print Progressbar");
        frame.setContentPane(panel);
        JButton b = new JButton("Print");
        b.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
                PrintJob pj = Toolkit.getDefaultToolkit().getPrintJob(frame,
                                              "PrintProgressBarTest", null);
                if (pj != null) {
                    try {
                        Graphics g = pj.getGraphics();
                        panel.printAll(g);
                        g.dispose();
                        pj.end();
                    } catch (ClassCastException cce) {
                        throw new RuntimeException("Test FAILED: ClassCastException", cce);
                    } catch (Exception e) {
                        throw new Error("Test FAILED: unknown exception", e);
                    }
                }
            }
        });

        frame.setSize(200, 150);
        frame.add(b);
        return frame;
    }

}
