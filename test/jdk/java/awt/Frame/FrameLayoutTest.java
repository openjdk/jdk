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

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Frame;

/*
 * @test
 * @bug 4173503
 * @library /java/awt/regtesthelpers
 * @requires (os.family == "windows")
 * @build PassFailJFrame
 * @summary Tests that frame layout is performed when frame is maximized from taskbar
 * @run main/manual FrameLayoutTest
 */

public class FrameLayoutTest {

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                Right-click on the taskbar button for this test. In the menu appeared,
                choose Maximize. The frame will be maximized. Check if buttons inside
                the frame are laid out properly, i.e. they occupy the frame entirely.

                If so, test passes. If buttons occupy small rectangle in the top left
                corner, test fails.""";

        PassFailJFrame.builder()
                .title("Frame's Layout Test Instruction")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(40)
                .testUI(FrameLayoutTest::createUI)
                .build()
                .awaitAndCheck();
    }

    private static Frame createUI() {
        Frame f = new Frame("Maximize Test");
        f.add(new Button("North"), BorderLayout.NORTH);
        f.add(new Button("South"), BorderLayout.SOUTH);
        f.add(new Button("East"), BorderLayout.EAST);
        f.add(new Button("West"), BorderLayout.WEST);
        f.add(new Button("Cent"), BorderLayout.CENTER);
        f.pack();
        f.setState(Frame.ICONIFIED);
        return f;
    }
}
