/*
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Frame;
import java.awt.print.PageFormat;
import java.awt.print.PrinterJob;

import javax.print.PrintService;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextArea;

/*
 * @test
 * @bug 4937672 5100706 6252456
 * @key printer
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/othervm/manual -Djava.security.manager=allow SecurityDialogTest
 */
public class SecurityDialogTest extends Frame {
    private static final String INSTRUCTIONS =
            "This test brings up a native and cross-platform page and print dialogs.\n" +
                    "\n" +
                    "The dialogs should be displayed even when " +
                    "there is no queuePrintJob permission.\n" +
                    "If the dialog has an option to save to file, the option ought " +
                    "to be disabled if there is no read/write file permission.\n" +
                    "You should test this by trying different policy files.";
    private static final JTextArea msg = new JTextArea("");

    public static void main(String[] args) throws Exception {
        if (PrinterJob.lookupPrintServices().length == 0) {
            throw new RuntimeException("Printer not configured or available.");
        }

        PassFailJFrame passFailJFrame = new PassFailJFrame.Builder()
                .instructions(INSTRUCTIONS)
                .splitUIBottom(SecurityDialogTest::createTestUI)
                .rows((int) INSTRUCTIONS.lines().count() + 1)
                .columns(45)
                .build();

        new SecurityDialogTest();
        passFailJFrame.awaitAndCheck();
    }

    private static JComponent createTestUI()
    {
        Box main = Box.createVerticalBox();
        JLabel title = new JLabel("Current Dialog:");
        msg.setEditable(false);
        main.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        main.add(Box.createVerticalGlue());
        main.add(title);
        main.add(Box.createVerticalStrut(4));
        main.add(msg);
        main.add(Box.createVerticalGlue());
        return main;
    }

    public SecurityDialogTest() {
        PrinterJob pj = PrinterJob.getPrinterJob();

        // Install a security manager which does not allow reading and
        // writing of files.
        //PrintTestSecurityManager ptsm = new PrintTestSecurityManager();
        SecurityManager ptsm = new SecurityManager();
        System.setSecurityManager(ptsm);

        PrintService[] services = PrinterJob.lookupPrintServices();
        for (int i = 0; i < services.length; i++) {
            System.out.println("SecurityDialogTest service " + i + " : " + services[i]);
        }

        PrintService defservice = pj.getPrintService();
        System.out.println("SecurityDialogTest default service : " + defservice);

        msg.setText("Native Page Dialog");
        pj.pageDialog(new PageFormat());

        msg.setText("Swing Page Dialog ");
        PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
        pj.pageDialog(attributes);

        // With the security manager installed, save to file should now
        // be denied.
        msg.setText("Native Print Dialog ");
        pj.printDialog();

        msg.setText("Swing Print Dialog ");
        pj.printDialog(attributes);

        msg.setText("Test completed");
    }
}
