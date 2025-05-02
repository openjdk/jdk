/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4530087
 * @summary Test if double-clicking causes ActionEvent on underlying button
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual BadActionEventTest
 */

import java.awt.Button;
import java.awt.Color;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class BadActionEventTest implements ActionListener {
    private static Button showBtn;
    private static Button listBtn;

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
            1) Click on 'Show File Dialog' to bring up the FileDialog window.
            (If necessary, change to a directory with files (not just directories) in it.)
            2) Move the FileDialog so that one of the file names (again, a file, NOT a directory) in the list is
            directly over the 'ActionListener' button.
            3) Double-click on the file name over the button. The FileDialog will disappear.
            4) If the 'ActionListener' button receives an ActionEvent, the test fails and a
            message to that effect will be printed.
            Otherwise, the test passes.
            """;

        PassFailJFrame.builder()
            .title("Test Instructions")
            .instructions(INSTRUCTIONS)
            .columns(45)
            .testUI(BadActionEventTest::createUI)
            .logArea(2)
            .build()
            .awaitAndCheck();
    }

    private static Frame createUI() {
        Frame frame = new Frame("BadActionEventTest");
        frame.setLayout(new GridLayout(1, 2));
        frame.setSize(400, 200);
        showBtn = new Button("Show File Dialog");
        listBtn = new Button("ActionListener");
        showBtn.setSize(200, 200);
        listBtn.setSize(200, 200);
        showBtn.addActionListener(new BadActionEventTest());
        listBtn.addActionListener(new BadActionEventTest());
        frame.add(showBtn);
        frame.add(listBtn);
        return frame;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == showBtn) {
            FileDialog fd = new FileDialog(new Frame());
            fd.setVisible(true);
        } else if (e.getSource() == listBtn) {
            listBtn.setBackground(Color.red);
            listBtn.setLabel("TEST FAILS!");
            PassFailJFrame.log("*TEST FAILS!* ActionListener got ActionEvent! *TEST FAILS!*");
        }
    }
}
