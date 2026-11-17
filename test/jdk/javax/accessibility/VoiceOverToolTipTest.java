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

import javax.swing.JButton;
import javax.swing.JFrame;

/*
 * @test
 * @key headful
 * @bug 8378404
 * @summary manual test for VoiceOver regarding tooltips
 * @requires os.family == "mac"
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual VoiceOverToolTipTest
 */

public class VoiceOverToolTipTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                INSTRUCTIONS:
                1. Open VoiceOver
                2. Move the mouse over the "Intro" button
                3. Leave the mouse over the button until the tooltip appears

                Expected behavior: VoiceOver should announce the button name,
                and it should announce the AX description ("This is just a
                tribute").

                This test fails if VoiceOver announces anything about a
                new window/dialog, or if it otherwise describes the tooltip
                toggling between visible/hidden, or if it announces
                "This is not the greatest tooltip in the world".
                """;

        PassFailJFrame.builder()
                .title("VoiceOverToolTipTest Instruction")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(VoiceOverToolTipTest::createUI)
                .build()
                .awaitAndCheck();
    }

    public static JFrame createUI() {
        JFrame f = new JFrame();
        JButton button = new JButton("Intro");
        button.setToolTipText("This is not the greatest tooltip in the world");
        button.getAccessibleContext().setAccessibleDescription(
                "This is just a tribute.");
        f.getContentPane().add(button);
        f.pack();
        return f;
    }
}
