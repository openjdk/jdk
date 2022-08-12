/*
 * Copyright (c) 2004, 2022, Oracle and/or its affiliates. All rights reserved.
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
  @bug 5045936 5055171
  @key printer
  @library /test/lib
  @library /javax/accessibility/manual
  @build lib.ManualTestFrame
  @build lib.TestResult
  @build jtreg.SkippedException
  @summary Tests that there is no ClassCastException thrown in printing
   checkbox and scrollbar with XAWT
  @requires (os.family == "linux" | os.family == "solaris")
  @run main/manual PrintCheckboxManualTest
*/

import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.PrintJob;
import java.awt.Scrollbar;
import java.awt.Toolkit;
import java.awt.print.PrinterJob;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JEditorPane;
import jtreg.SkippedException;
import lib.ManualTestFrame;
import lib.TestResult;

public class PrintCheckboxManualTest
{

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, IOException {
        if (PrinterJob.lookupPrintServices().length == 0) {
            throw new SkippedException("Printer not configured or available."
                    + " Test cannot continue.");
        }

        String instruction = """
               Linux or Solaris with XToolkit ONLY!
               1. Click the 'Print' button on the frame,
               2. Select a printer in the print dialog and proceed,
               3. If the frame with checkbox and button on it is printed
               successfully test PASSED else FAILED.
               """;

        Consumer<JEditorPane> testInstProvider = e -> {
            e.setContentType("text/plain");
            e.setText(instruction);
        };

        Supplier<TestResult> resultSupplier = ManualTestFrame.showUI(
                "Tests PrintCheckboxManualTest",
                "Wait until the Test UI is seen", testInstProvider);
        createTestUI();

        //this will block until user decision to pass or fail the test
        TestResult  testResult = resultSupplier.get();
        ManualTestFrame.handleResult(testResult,"PrintCheckboxManualTest");
    }

    public static void createTestUI() {

        Frame f = new Frame("Print checkbox");
        f.setLayout(new GridLayout(2, 2));
        f.setSize(200, 100);

        Checkbox ch = new Checkbox("123");
        ch.setState(true);
        f.add(ch);

        Scrollbar sb = new Scrollbar(Scrollbar.HORIZONTAL);
        f.add(sb);

        Button b = new Button("Print");
        b.addActionListener(ev -> {
            PrintJob pj = Toolkit.getDefaultToolkit().getPrintJob(f, "PrintCheckboxManualTest", null);
            if (pj != null)
            {
                try
                {
                    Graphics g = pj.getGraphics();
                    f.printAll(g);
                    g.dispose();
                    pj.end();
                    System.out.println("Test PASSED");
                }
                catch (ClassCastException cce)
                {
                    System.out.println("Test FAILED: ClassCastException");
                    throw new RuntimeException("Test FAILED: ClassCastException", cce);
                }
                catch (Exception e)
                {
                    System.out.println("Test FAILED: unknown Exception");
                    throw new Error("Test FAILED: unknown exception", e);
                }
            }
        });
        f.add(b);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }
}

