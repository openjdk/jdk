/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Frame;

/*
 * @test
 * @bug 4041442
 * @key headful
 * @summary Test resizing a frame containing a canvas
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FrameResizeTest_1
 */

public class FrameResizeTest_1 {

    private static final String INSTRUCTIONS = """
        To the right of this frame is an all-white 200x200 frame.

        This is actually a white canvas component in the frame.
        The frame itself is red.
        The red should never show.
        In particular, after you resize the frame, you should see all white and no red.
        (During very fast window resizing, red color may appear briefly,
        which is not a failure.)

        Upon test completion, click Pass or Fail appropriately.
        """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("FrameResizeTest_1 Instructions")
                .instructions(INSTRUCTIONS)
                .testTimeOut(5)
                .rows(12)
                .columns(45)
                .testUI(FrameResize_1::new)
                .build()
                .awaitAndCheck();
    }
}

class FrameResize_1 extends Frame {

    FrameResize_1() {
        super("FrameResize_1");
        // Create a white canvas
        Canvas canvas = new Canvas();
        canvas.setBackground(Color.white);

        setLayout(new BorderLayout());
        add("Center", canvas);

        setBackground(Color.red);
        setSize(200,200);
    }
}
