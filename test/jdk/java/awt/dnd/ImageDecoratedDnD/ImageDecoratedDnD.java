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
import java.awt.Component;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.dnd.DragSource;

/*
 * @test
 * @bug 4874070
 * @summary Tests basic DnD functionality
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ImageDecoratedDnD
 */

public class ImageDecoratedDnD {
    private static final String INSTRUCTIONS = """
            A Frame, which contains a yellow button labeled "Drag ME!"
            and a RED Panel will appear.

            1. Click on the button and drag to the red panel by pressing
               the "CTRL" key on the keyboard.

            2. When the mouse enters the red panel during the drag, the panel
               should turn yellow.
                On the systems that supports pictured drag, the image under the drag-cursor
                should appear (anchor is shifted, from top-left corner of the picture to
                inside the picture to 10pt in both dimensions).

                In WIN32 systems the image under cursor would be visible ONLY over
                the drop targets with activated extended OLE D'n'D support (that are,
                the desktop and IE).

            3. Release the mouse button.
                The panel should turn red again and a yellow button labeled ,
                "Drag ME!" should appear inside the panel. You should be able,
                to repeat this operation multiple times.

            If above is true press PASS, else press FAIL.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(40)
                .testUI(ImageDecoratedDnD::createUI)
                .build()
                .awaitAndCheck();
    }

    public static Frame createUI() {
        Frame frame = new Frame("DnD Test using Keyboard");
        Panel mainPanel;
        Component dragSource, dropTarget;

        frame.setBounds(0, 400, 400, 400);
        frame.setLayout(new BorderLayout());

        mainPanel = new Panel();
        mainPanel.setLayout(new BorderLayout());

        mainPanel.setBackground(Color.BLUE);

        dropTarget = new DnDTarget(Color.RED, Color.YELLOW);
        dragSource = new DnDSource("Drag ME! ("
                + (DragSource.isDragImageSupported() ? "with " : "without") + " image)" );

        mainPanel.add(dragSource, "North");
        mainPanel.add(dropTarget, "Center");
        frame.add(mainPanel, BorderLayout.CENTER);
        return frame;
    }
}