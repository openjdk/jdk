/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Color;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Point;

/*
 * @test
 * @bug 4340727
 * @summary Tests that undecorated property is set correctly
 *          when Frames and Dialogs are mixed.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FrameDialogMixedTest
 */

public class FrameDialogMixedTest {
    private static final int SIZE = 100;

    private static final String INSTRUCTIONS = """
            When the test starts, a RED UNDECORATED Frame is seen.
            Click on "Create Dialog" button, you should see a GREEN UNDECORATED Dialog.
            If both the frame and the dialog are undecorated press PASS otherwise FAIL.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                      .title("Undecorated Frame & Dialog Test Instructions")
                      .instructions(INSTRUCTIONS)
                      .rows((int) INSTRUCTIONS.lines().count() + 2)
                      .columns(40)
                      .testUI(FrameDialogMixedTest::createUI)
                      .build()
                      .awaitAndCheck();
    }

    private static Frame createUI() {
        Frame frame = new Frame("Undecorated Frame");
        frame.setSize(SIZE, SIZE);
        frame.setBackground(Color.RED);
        frame.setUndecorated(true);
        frame.setLayout(new FlowLayout(FlowLayout.CENTER));

        Button button = new Button("Create Dialog");
        button.addActionListener(e -> {
            Dialog dialog = new Dialog(frame);
            Point frameLoc = frame.getLocationOnScreen();
            dialog.setBounds(frameLoc.x + frame.getSize().width + 5,
                             frameLoc.y,
                             SIZE, SIZE);
            dialog.setBackground(Color.GREEN);
            dialog.setUndecorated(true);
            dialog.setVisible(true);
        });

        frame.add(button);
        return frame;
    }
}
