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

import javax.swing.JButton;
import javax.swing.JFrame;

/*
 * @test
 * @bug 4644444 8076246
 * @summary JToolTip is shown improperly when placed very close to screen boundaries
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4644444
 */

public class bug4644444 {

    private static final String INSTRUCTIONS = """
        1. Move the mouse on the button, so that the tooltip is visible.
        2. Tooltip should get adjusted itself to show its full length of text.
        3. Similarly, move the frame to different locations of the screen
            & see if tooltip works properly everywhere.
        4. Press 'Pass' if tooltip text is fully visible else press 'Fail'. """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("JToolTip Instructions")
                .instructions(INSTRUCTIONS)
                .testUI(bug4644444::createUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createUI() {
        JFrame frame = new JFrame("bug4644444");
        JButton button = new JButton("Button");
        button.setToolTipText("Something really long 1234567890 1234567890 " +
                "1234567890 1234567890 1234567890 1234567890 1234567890 1234567890");
        frame.getContentPane().add(button);
        frame.setSize(200, 80);
        return frame;
    }
}
