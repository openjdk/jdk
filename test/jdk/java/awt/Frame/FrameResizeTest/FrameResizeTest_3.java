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
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionListener;

/*
 * @test
 * @bug 4097207
 * @summary setSize() on a Frame does not resize its content
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FrameResizeTest_3
*/

public class FrameResizeTest_3 {

    private static final String INSTRUCTIONS = """
            1. You would see a frame titled 'TestFrame' with 2 buttons
               named 'setSize(500,500)' and 'setSize(400,400)'
            2. Click any button and you would see the frame resized
            3. If the buttons get resized along with the frame
               (ie., to fit the frame), press Pass else press Fail.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("FrameResizeTest_3 Instructions")
                .instructions(INSTRUCTIONS)
                .columns(45)
                .logArea(6)
                .testUI(FrameResizeTest_3::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static Window createTestUI() {
        Frame frame = new Frame("TestFrame");
        frame.setLayout(new GridLayout(2, 1));

        Button butSize500 = new Button("setSize(500,500)");
        Button butSize400 = new Button("setSize(400,400)");

        ActionListener actionListener = e -> {
            if (e.getSource() instanceof Button) {
                if (e.getSource() == butSize500) {
                    frame.setSize(500, 500);
                    PassFailJFrame.log("New bounds: " + frame.getBounds());
                } else if (e.getSource() == butSize400) {
                    frame.setSize(400, 400);
                    PassFailJFrame.log("New bounds: " + frame.getBounds());
                }
            }
        };
        butSize500.addActionListener(actionListener);
        butSize400.addActionListener(actionListener);
        frame.add(butSize500);
        frame.add(butSize400);

        frame.setSize(270, 200);
        return frame;
    }
}
