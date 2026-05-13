/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.FlowLayout;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;

/*
 * @test
 * @key headful
 * @bug 8377936
 * @summary manual test for VoiceOver reading button names and states
 * @requires os.family == "mac"
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual VoiceOverNamesAndStates
 */

public class VoiceOverNamesAndStates {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                INSTRUCTIONS:

                Part A:
                1. Open VoiceOver
                2. Move the VoiceOver cursor to the leftmost button.
                3. Press CTRL + ALT + RIGHT to move to the rightmost button.

                Expected behavior: VoiceOver should announce rightmost button.
                (It should NOT announce the leftmost button.)

                Part B (Regression for 8061359, 8348936, 8309733):
                1. Open VoiceOver
                2. Move the VoiceOver cursor to the leftmost button.
                3. Press SPACE to trigger Swing's default KeyListeners.
                4. Repeat step 3 to untoggle button.
                5. Press CTRL + ALT + SPACE to trigger VO's listeners.
                6. Repeat step 5 to untoggle button

                Expected behavior: VoiceOver should announce the change in the
                button state in steps 3-6. (When you use VoiceOver to toggle
                the button you should hear a small chirp as the button
                toggles.)

                Part C (Regression for 8345728, 8283400):
                1. Turn on Screen Magnifier
                (See System Settings -> Accessibility -> Hover Text)
                2. Press CMD key and hover mouse over leftmost button
                3. Click the button
                4. Release the CMD key

                Expected behavior: both the button and the "hover text" window
                repaint to show a selected button.

                5. Repeat steps 2-4 to untoggle the button

                Expected behavior: both the button and the "hover text" window
                repaint to show a deselected button.

                """;

        PassFailJFrame.builder()
                .title("VoiceOverNamesAndStates Instruction")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(VoiceOverNamesAndStates::createUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createUI() {
        JFrame f = new JFrame();

        f.getContentPane().setLayout(new FlowLayout());

        AbstractButton button1 = new JCheckBox("chess");
        AbstractButton button2 = new JButton("backgammon");

        f.getContentPane().add(button1);
        f.getContentPane().add(button2);
        f.pack();
        f.setVisible(true);
        return f;
    }
}
