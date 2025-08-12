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

import java.awt.Button;
import java.awt.Frame;
import java.awt.GridLayout;

/*
 * @test
 * @summary Test to make sure non-resizable Frames can be resized with the
 *          setSize() method.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FrameResizeTest_5
*/

public class FrameResizeTest_5  {
    private static final String INSTRUCTIONS = """
            This tests the programmatic resizability of non-resizable Frames.
            Even when a Frame is set to be non-resizable, it should still be
            programmatically resizable using the setSize() method.

            Initially the Frame will be resizable.  Try using the "Smaller"
            and "Larger" buttons to verify that the Frame resizes correctly.
            Then, click the "Toggle" button to make the Frame non-resizable.
            Again, verify that clicking the "Larger" and "Smaller" buttons
            causes the Frame to get larger and smaller.  If the Frame does
            not change size, or does not re-layout correctly, the test fails.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("FrameResizeTest_5 Instructions")
                .instructions(INSTRUCTIONS)
                .columns(45)
                .logArea(6)
                .testUI(TestFrame::new)
                .build()
                .awaitAndCheck();
    }

    private static class TestFrame extends Frame {
        Button bLarger, bSmaller, bCheck, bToggle;

        public TestFrame() {
            super("Frame Resize Test");
            setSize(200, 200);
            bLarger = new Button("Larger");
            bLarger.addActionListener(e -> {
                setSize(400, 400);
                validate();
            });
            bSmaller = new Button("Smaller");
            bSmaller.addActionListener(e -> {
                setSize(200, 100);
                validate();
            });
            bCheck = new Button("Resizable?");
            bCheck.addActionListener(e -> {
                if (isResizable()) {
                    PassFailJFrame.log("Frame is resizable");
                    setResizable(true);
                } else {
                    PassFailJFrame.log("Frame is not resizable");
                    setResizable(false);
                }
            });
            bToggle = new Button("Toggle");
            bToggle.addActionListener(e -> {
                if (isResizable()) {
                    PassFailJFrame.log("Frame is now not resizable");
                    setResizable(false);
                } else {
                    PassFailJFrame.log("Frame is now resizable");
                    setResizable(true);
                }
            });
            setLayout(new GridLayout(4, 1));
            add(bSmaller);
            add(bLarger);
            add(bCheck);
            add(bToggle);
        }
    }
}
