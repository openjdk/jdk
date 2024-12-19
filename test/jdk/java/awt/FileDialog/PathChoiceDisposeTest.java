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

import java.awt.Button;
import java.awt.FileDialog;
import java.awt.Frame;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/*
 * @test
 * @bug 6240084
 * @summary Test that disposing unfurled list by the pressing ESC
 *          in FileDialog is working properly on XToolkit
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual PathChoiceDisposeTest
 */

public class PathChoiceDisposeTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("sun.awt.disableGtkFileDialogs", "true");
        String INSTRUCTIONS = """
                1) Click on 'Show File Dialog' button to bring up the FileDialog window.
                2) Open the directory selection choice by clicking button next to
                   'Enter Path or Folder Name'. A drop-down will appear.
                3) Press 'ESC'.
                4) If you see that the dialog gets disposed and the popup
                   still remains on the screen, the test failed, otherwise passed.
                """;

        PassFailJFrame.builder()
                .title("PathChoiceDisposeTest Instruction")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(PathChoiceDisposeTest::createUI)
                .build()
                .awaitAndCheck();
    }

    public static Frame createUI() {
        Frame f = new Frame("PathChoiceDisposeTest Test");
        Button b = new Button("Show File Dialog");
        FileDialog fd = new FileDialog(f);
        b.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                fd.setVisible(true);
            }
        });
        f.add(b);
        f.setSize(300, 200);
        return f;
    }
}
