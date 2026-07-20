/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4141400
 * @summary Tests that the divider of JSplitPane can be moved only by
 * dragging its thumb under Motif LAF
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4141400
 */

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JSplitPane;
import javax.swing.UIManager;

public class bug4141400 {
    public static void main(String[] args) throws Exception {
        try {
            UIManager.setLookAndFeel(
                "com.sun.java.swing.plaf.motif.MotifLookAndFeel");
        } catch (Exception e) {
            throw new RuntimeException("Failed to set Motif LAF");
        }

        String INSTRUCTIONS = """
            Place mouse cursor somewhere on the split pane divider, but outside
            its thumb. Then try to move the divider. It should not move. If it
            does not move, the test passes, otherwise it fails.
            """;
        PassFailJFrame.builder()
            .instructions(INSTRUCTIONS)
            .columns(70)
            .testUI(bug4141400::initialize)
            .build()
            .awaitAndCheck();
    }

    private static JFrame initialize() {
        JFrame fr = new JFrame("bug4141400");
        JSplitPane pane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            true,
            new JButton("Button 1"),
            new JButton("Button 2"));
        fr.add(pane);
        fr.setSize(250, 300);
        return fr;
    }
}
