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

/*
 * @test
 * @bug 4419914
 * @summary Tests that tab movement is correct in RTL component orientation.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4419914
*/

import java.awt.BorderLayout;
import java.awt.ComponentOrientation;
import javax.swing.JButton;
import javax.swing.JFrame;
import java.util.Locale;

public class bug4419914 {
    private static final String INSTRUCTIONS = """
        1. You will see a frame with five buttons.
        2. Confirm that each button is placed as follows:
             NORTH
        END  CENTER  START
             SOUTH
        3. Press the "NORTH" button and confirm the button is focused.
        4. Press TAB repeatedly and confirm that the TAB focus moves from right to left.
             (NORTH - START - CENTER - END - SOUTH - NORTH - START - CENTER - ...)

            If there's anything different from the above items, click Fail else click Pass.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("Tab movement Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(48)
                .testUI(bug4419914::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {
        JFrame frame = new JFrame("bug4419914");
        frame.setFocusCycleRoot(true);
        frame.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        frame.setLocale(Locale.ENGLISH);
        frame.enableInputMethods(false);

        frame.getContentPane().setComponentOrientation(
                               ComponentOrientation.RIGHT_TO_LEFT);
        frame.getContentPane().setLocale(Locale.ENGLISH);
        frame.getContentPane().setLayout(new BorderLayout());
        frame.add(new JButton("SOUTH"), BorderLayout.SOUTH);
        frame.add(new JButton("CENTER"), BorderLayout.CENTER);
        frame.add(new JButton("END"), BorderLayout.LINE_END);
        frame.add(new JButton("START"), BorderLayout.LINE_START);
        frame.add(new JButton("NORTH"), BorderLayout.NORTH);
        frame.setSize(300, 150);
        return frame;
    }
}
