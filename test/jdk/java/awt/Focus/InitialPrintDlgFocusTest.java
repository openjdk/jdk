/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4688591
 * @summary Tab key hangs in Native Print Dialog on win32
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual InitialPrintDlgFocusTest
 */

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.JobAttributes;
import java.awt.PageAttributes;
import java.awt.PrintJob;
import java.awt.Toolkit;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JFrame;

public class InitialPrintDlgFocusTest {

    private static final String INSTRUCTIONS = """
            After the tests starts you will see a frame titled "PrintTest".
            Press the "Print" button and the print dialog should appear.
            If you are able to transfer focus between components of the Print dialog
            using the TAB key, then the test passes else the test fails.

            Note: close the Print dialog before clicking on "Pass" or "Fail" buttons.""";


    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("InitialPrintDlgFocusTest Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(InitialPrintDlgFocusTest::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {
        return new PrintTest();

    }
}

class PrintTest extends JFrame implements ActionListener {

    JButton b;
    JobAttributes jbattrib;
    Toolkit tk ;
    PageAttributes pgattrib;

    public PrintTest() {
        setTitle("PrintTest");
        setSize(500, 400);

        b = new JButton("Print");
        jbattrib = new JobAttributes();
        tk = Toolkit.getDefaultToolkit();
        pgattrib = new PageAttributes();
        getContentPane().setLayout(new FlowLayout());
        getContentPane().add(b);

        b.addActionListener(this);

    }

    public void actionPerformed(ActionEvent ae) {
        if(ae.getSource()==b)
            jbattrib.setDialog(JobAttributes.DialogType.NATIVE);

        PrintJob pjob = tk.getPrintJob(this, "Printing Test",
                                       jbattrib, pgattrib);

    }
}

