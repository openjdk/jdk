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
import java.lang.reflect.InvocationTargetException;

import javax.print.PrintService;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/*
 * @test
 * @bug 4937672 5100706 6252456
 * @key printer
 * @summary Verifies "Print to file" option is disable if reading/writing files
 *          is not allowed by Security Manager.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual/othervm -Djava.security.manager=allow SecurityDialogTest
 */
public class SecurityDialogTest {
    private static final String INSTRUCTIONS =
            "This test brings up a native and cross-platform page and print dialogs.\n" +
            "\n" +
            "If the dialog has an option to save to file, the option ought " +
            "to be disabled.\n" +
            "\n" +
            "Press the Pass button if the \"Print to file\" option was disabled in\n" +
            "all the dialogs where it was present.\n" +
            "Otherwise, press the Fail button.\n" +
            "\n" +
            "The dialogs should be displayed even when " +
            "there is no queuePrintJob permission.";

    private static JLabel dialogType;

    public static void main(String[] args) throws Exception {
        if (PrinterJob.lookupPrintServices().length == 0) {
            throw new RuntimeException("Printer not configured or available.");
        }

        PassFailJFrame passFailJFrame = PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .splitUIBottom(SecurityDialogTest::createTestUI)
                .rows((int) INSTRUCTIONS.lines().count() + 1)
                .columns(45)
                .build();

        displayDialogs();

        passFailJFrame.awaitAndCheck();
    }

    private static JComponent createTestUI() {
        dialogType = new JLabel(" ");

        Box main = Box.createVerticalBox();
        main.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        main.add(new JLabel("Current Dialog:"));
        main.add(Box.createVerticalStrut(4));
        main.add(dialogType);
        return main;
    }

    private static void displayDialogs()
            throws InterruptedException, InvocationTargetException {
        final PrinterJob pj = PrinterJob.getPrinterJob();

        // Install a security manager which does not allow reading and
        // writing of files.
        SecurityManager ptsm = new SecurityManager();
        System.setSecurityManager(ptsm);

        PrintService[] services = PrinterJob.lookupPrintServices();
        for (int i = 0; i < services.length; i++) {
            System.out.println("SecurityDialogTest service " + i + " : " + services[i]);
        }

        System.out.println("SecurityDialogTest default service : " + pj.getPrintService());

        setDialogType("Native Page Dialog");
        pj.pageDialog(new PageFormat());

        setDialogType("Swing Page Dialog");
        PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
        pj.pageDialog(attributes);

        // With the security manager installed, save to file should now
        // be denied.
        setDialogType("Native Print Dialog");
        pj.printDialog();

        setDialogType("Swing Print Dialog");
        pj.printDialog(attributes);

        setDialogType("Test completed");
    }

    private static void setDialogType(String type)
            throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(() -> dialogType.setText(type));
    }
}
