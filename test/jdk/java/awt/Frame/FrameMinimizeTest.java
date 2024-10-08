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

import java.awt.Frame;

/*
 * @test
 * @bug 4172782
 * @summary Test if non-resizable frame is minimizable
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FrameMinimizeTest
 */

public class FrameMinimizeTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                When the blank FrameMinimizeTest frame is shown, verify that
                  1. It is not resizable;
                  2. It is minimizable.
                                """;

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows(4)
                .columns(35)
                .testUI(FrameMinimizeTest::initialize)
                .build()
                .awaitAndCheck();
    }

    public static Frame initialize() {
        Frame f = new Frame("FrameMinimizeTest");
        f.setSize(200, 200);
        f.setResizable(false);
        return f;
    }
}
