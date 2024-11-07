/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5079694
 * @summary Test if JDialog respects setCursor
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual HiddenDialogParentTest
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.border.LineBorder;

public class HiddenDialogParentTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                 You can see a label area in the center of JDialog.
                 Verify that the cursor is a hand cursor in this area.
                 If so, press pass, else press fail.
                """;

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(HiddenDialogParentTest::createUI)
                .build()
                .awaitAndCheck();
    }

    public static JDialog createUI() {
        JDialog dialog = new JDialog();
        dialog.setTitle("JDialog Cursor Test");
        dialog.setLayout(new BorderLayout());
        JLabel centerLabel = new JLabel("Cursor should be a hand in this " +
                "label area");
        centerLabel.setBorder(new LineBorder(Color.BLACK));
        centerLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        dialog.add(centerLabel, BorderLayout.CENTER);
        dialog.setSize(300, 200);

        return dialog;
    }
}
