/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4201967
 * @summary tests that a multiselection list doesn't causes crash when FileDialog is invoked
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MultiSelectionListCrashTest
 */

import java.awt.Button;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.List;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class MultiSelectionListCrashTest {

    private static final String INSTRUCTIONS = """
         Press "Invoke dialog" button to invoke a FileDialog.
         When it appears close it by pressing cancel button.
         If all remaining frames are enabled and
         page fault didn't occur the test passed. Otherwise the test failed.

         Try to invoke a FileDialog several times to verify that the bug doesn't exist.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("MultiSelectionListCrashTest Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(MultiSelectionListCrashTest::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static Frame createTestUI() {

        Frame frame = new Frame("MultiSelectionListCrashTest frame");
        Button button = new Button("Invoke dialog");
        button.addActionListener(new FileDialogInvoker(frame));
        List list = new List(4, true);
        list.add("Item1");
        list.add("Item2");
        frame.setLayout(new FlowLayout());
        frame.add(button);
        frame.add(list);
        frame.setSize(200, 200);
        return frame;
    }
}

class FileDialogInvoker implements ActionListener {
     FileDialog fileDialog;

     public FileDialogInvoker(Frame frame) {
         fileDialog = new FileDialog(frame);
     }

     public void actionPerformed(ActionEvent e) {
         fileDialog.setVisible(true);
     }

}
