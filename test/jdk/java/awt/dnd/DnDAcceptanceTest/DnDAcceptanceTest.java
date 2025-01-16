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
import java.awt.Color;
import java.awt.Component;
import java.awt.Frame;
import java.awt.Panel;

/*
 * @test
 * @bug 4166541 4225247 4297663
 * @summary Tests Basic DnD functionality
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual DnDAcceptanceTest
 */

public class DnDAcceptanceTest {
     private static final String INSTRUCTIONS = """
            When test runs a Frame which contains a yellow button labeled
            "Drag ME!" and a RED Panel will appear.

            Click on the button and drag to the red panel.
            When the mouse enters the red panel
            during the drag the panel should turn yellow.

            Release the mouse button, panel should turn red again and
            a yellow button labeled Drag ME! should appear inside the panel.
            You should be able to repeat this operation multiple times.

            If above is true press PASS, else press FAIL.
            """;

     public static void main(String[] args) throws Exception {
         PassFailJFrame.builder()
                       .title("Test Instructions")
                       .instructions(INSTRUCTIONS)
                       .columns(38)
                       .testUI(DnDAcceptanceTest::createUI)
                       .build()
                       .awaitAndCheck();
     }

     private static Frame createUI() {
         Frame frame = new Frame("DnDAcceptanceTest");
         Panel mainPanel;
         Component dragSource, dropTarget;

         frame.setSize(400, 400);
         frame.setLayout(new BorderLayout());

         mainPanel = new Panel();
         mainPanel.setLayout(new BorderLayout());

         mainPanel.setBackground(Color.BLACK);

         dropTarget = new DnDTarget(Color.RED, Color.YELLOW);
         dragSource = new DnDSource("Drag ME!");

         mainPanel.add(dragSource, "North");
         mainPanel.add(dropTarget, "Center");
         frame.add(mainPanel, BorderLayout.CENTER);
         frame.setAlwaysOnTop(true);
         return frame;
     }
}
