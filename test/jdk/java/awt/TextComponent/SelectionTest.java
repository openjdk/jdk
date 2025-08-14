/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4056231
 * @summary Checks that TextComponents don't grab the global CDE selection
 *  upon construction if their own selection is null.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual SelectionTest
 */

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Frame;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class SelectionTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. "Select some text in another window, then click the button.",
                2. "If the text in the other window is de-selected, the test FAILS.",
                   "If the text remains selected, the test PASSES."
                    """;
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(initialize())
                .build()
                .awaitAndCheck();
    }

    public static Frame initialize() {
        Frame frame = new Frame("Selection Test");
        frame.setLayout(new BorderLayout());
        Button b = new Button("Select some text in another window, then" +
                " click me");
        b.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                frame.add(new TextField("text field test"));
                frame.add(new TextArea("text area test"));
            }
        });
        frame.add(b);
        frame.setSize(400, 400);
        return frame;
    }
}
