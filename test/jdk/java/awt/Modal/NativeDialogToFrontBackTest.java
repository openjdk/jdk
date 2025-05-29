/*
 * Copyright (c) 2006, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Dialog;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.util.List;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.print.PageFormat;
import java.awt.print.PrinterJob;

/*
 * @test
 * @bug 6393608
 * @summary Tests that toBack/toFront methods works correctly for native dialogs
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual NativeDialogToFrontBackTest
 */

public class NativeDialogToFrontBackTest {
    public static void main(String[] args) throws Exception {
        final String INSTRUCTIONS = """
                When the test starts two frames appear: 'Control' and 'Blocked'
                    1. Click on the 'Show file dialog' button
                    2. Drag the file dialog so it partially overlaps the 'Blocked' frame
                    3. 'Blocked' frame must be below the file dialog, if not - press Fail
                    3. Click on the 'Blocked to front' button
                    4. 'Blocked' frame must still be below the file dialog, if not - press Fail
                    5. Close the file dialog
                    6. Repeat steps 2 to 4 with print and page dialogs using the corresponding button
                    7. If 'Blocked' frame is always below File/Print/Page dialog, press Pass""";

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(45)
                .testUI(NativeDialogToFrontBackTest::init)
                .positionTestUI(WindowLayouts::rightOneColumn)
                .build()
                .awaitAndCheck();
    }

    public static List<Frame> init() {
        Frame blocked = new Frame("Blocked");
        blocked.setSize(200, 200);

        Frame control = new Frame("Control");
        control.setModalExclusionType(Dialog.ModalExclusionType.APPLICATION_EXCLUDE);
        control.setLayout(new FlowLayout());

        Button showFileDialog = new Button("Show file dialog");
        showFileDialog.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                new FileDialog(control, "File dialog").setVisible(true);
            }
        });
        control.add(showFileDialog);

        Button showPrintDialog = new Button("Show print dialog");
        showPrintDialog.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                PrinterJob.getPrinterJob().printDialog();
            }
        });
        control.add(showPrintDialog);

        Button showPageDialog = new Button("Show page dialog");
        showPageDialog.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                PrinterJob.getPrinterJob().pageDialog(new PageFormat());
            }
        });
        control.add(showPageDialog);

        Button blockedToFront = new Button("Blocked to front");
        blockedToFront.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                blocked.toFront();
            }
        });
        control.add(blockedToFront);

        control.setSize(200, 200);
        return List.of(control, blocked);
    }
}
