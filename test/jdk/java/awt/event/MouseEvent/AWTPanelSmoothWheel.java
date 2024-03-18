/*
 * Copyright (c) 2009, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Color;
import java.awt.Frame;
import java.awt.Panel;

/*
 * @test
 * @bug 6730447
 * @summary [Win] To verify the support for high resolution mouse wheel on Windows.
 *          AWT panel needs to support high-res mouse wheel rotation.
 * @requires (os.family == "windows")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual AWTPanelSmoothWheel
 */

public class AWTPanelSmoothWheel {
    public static final String INSTRUCTIONS = """
            This test is relevant for windows platforms and mouses with high-resolution wheel,
            please just press pass if this is not the case.

            Place the mouse cursor above the green panel and rotate the mouse wheel,
            the test will print all mouse wheel event messages into the logging panel
            below the instruction window.
            Please make sure that some of the messages have non-zero 'wheelRotation' value,
            and also check if the test works OK if the mouse wheel is rotated very slow.

            If the above is true press PASS, else FAIL.
            """;

    public static void main (String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(45)
                .logArea(8)
                .testUI(AWTPanelSmoothWheel::createUI)
                .build()
                .awaitAndCheck();
    }

    private static Frame createUI () {
        Frame frame = new Frame("Test Wheel Rotation");
        Panel panel = new Panel();
        panel.setBackground(Color.GREEN);
        panel.addMouseWheelListener(e -> PassFailJFrame.log(e.toString()));
        frame.setSize (200, 200);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.CENTER);
        return frame;
    }
}
