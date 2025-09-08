/*
 * Copyright (c) 2004, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5045936 5055171
 * @summary Tests that there is no ClassCastException thrown in printing
 *          checkbox and scrollbar with XAWT
 * @key printer
 * @requires (os.family == "linux")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual PrintCheckboxManualTest
 */

import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Panel;
import java.awt.PrintJob;
import java.awt.Scrollbar;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class PrintCheckboxManualTest extends Panel {

    private static final String INSTRUCTIONS = """
            This test is for Linux with XToolkit ONLY!,
            1. Click the 'Print' button on the frame
            2. Select a printer in the print dialog and proceed
            3. If the frame with checkbox and button on it
               is printed without any exception test PASSED else FAILED.
        """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("Instructions")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(PrintCheckboxManualTest::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static Frame createTestUI() {

        Frame f = new Frame("Print checkbox");
        f.setLayout(new GridLayout(2, 2));
        f.setSize(200, 100);

        Checkbox ch = new Checkbox("123");
        ch.setState(true);
        f.add(ch);

        Scrollbar sb = new Scrollbar(Scrollbar.HORIZONTAL);
        f.add(sb);

        Button b = new Button("Print");
        b.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
                PrintJob pj = Toolkit.getDefaultToolkit().
                                      getPrintJob(f, "PrintCheckboxManualTest",
                                                  null);
                if (pj != null) {
                    try {
                        Graphics g = pj.getGraphics();
                        f.printAll(g);
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
        f.add(b);
        return f;
    }
}
