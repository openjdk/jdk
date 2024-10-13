/*
 * Copyright (c) 2007, 2021, Oracle and/or its affiliates. All rights reserved.
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
   @bug 6302514
   @key printer
   @run main/manual PageDialogTest
   @summary A toolkit modal dialog should not be blocked by Page/Print dialog.
*/

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.TextArea;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.print.PageFormat;
import java.awt.print.PrinterJob;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class PageDialogTest {

    public static Frame frame;
    public static Dialog dialog;
    public static volatile boolean testResult;
    public static final CountDownLatch countDownLatch = new CountDownLatch(1);

    public static void createUI() {
        frame = new Frame("Test 6302514");
        String instructions =
                "1. Click on the 'Show Dialog' button to show a 'Toolkit Modal Dialog' \n" +
                "2. Click on the 'Open PageDialog' button to show 'Page Dialog'.\n" +
                "3. The test fails if the page dialog blocks the toolkit\n"+
                " else test pass.\n" +
                "4. Close Page dialog and 'Toolkit modal dialog'\n" +
                "5. Click appropriate button to mark the test case pass or fail.\n" ;

        TextArea instructionsTextArea = new TextArea( instructions, 8,
                50, TextArea.SCROLLBARS_NONE );
        instructionsTextArea.setEditable(false);
        frame.add(BorderLayout.NORTH, instructionsTextArea);

        Panel buttonPanel = new Panel(new FlowLayout());
        Button passButton = new Button("pass");
        passButton.setActionCommand("pass");
        passButton.addActionListener(e -> {
            testResult = true;
            countDownLatch.countDown();
            dialog.dispose();
            frame.dispose();
        });

        Button failButton = new Button("fail");
        failButton.addActionListener(e->{
            testResult = false;
            countDownLatch.countDown();
            dialog.dispose();
            frame.dispose();
        });

        Button showDialog = new Button("Show Dialog");
        showDialog.addActionListener(e->{
            createToolkitModalDialog();
        });

        buttonPanel.add(showDialog);
        buttonPanel.add(passButton);
        buttonPanel.add(failButton);
        frame.add(BorderLayout.SOUTH, buttonPanel);
        frame.pack();
        frame.setVisible(true);
    }

    public static void createToolkitModalDialog() {
        dialog = new Dialog((Frame) null, "Toolkit modal dialog",
                Dialog.ModalityType.TOOLKIT_MODAL);
        dialog.setLayout(new FlowLayout());
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                super.windowClosing(e);
                dialog.dispose();
            }
        });

        Button openPageDialogButton = new Button("Open PageDialog");
        openPageDialogButton.addActionListener(e->{
            PrinterJob.getPrinterJob().pageDialog(new PageFormat());
        });
        dialog.add(openPageDialogButton);
        dialog.setSize(250, 150);
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }

    public static void main(String []args) throws InterruptedException {
        createUI();
        if ( !countDownLatch.await(5, TimeUnit.MINUTES)) {
            throw new RuntimeException("Timeout : user did not perform any " +
                    "action on the UI.");
        }
        if ( !testResult) {
            throw new RuntimeException("Test failed");
        }
    }
}

