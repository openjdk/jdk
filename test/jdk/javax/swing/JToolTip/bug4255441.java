/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4255441
 * @summary Tests that tooltip appears inside AWT Frame
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4255441
 */

import java.awt.FlowLayout;
import java.awt.Frame;
import javax.swing.JButton;

public class bug4255441 {
    private static final String INSTRUCTIONS = """
            Move mouse pointer inside the button.
            If a tooltip with "Tooltip text" appears, the test passes.
            """;

    private static Frame createTestUI() {
        Frame fr = new Frame("bug4255441");
        fr.setLayout(new FlowLayout());

        JButton bt = new JButton("Button");
        bt.setToolTipText("Tooltip text");
        fr.add(bt);

        fr.setSize(200, 200);
        return fr;
    }

    public static void main(String[] argv) throws Exception {
        PassFailJFrame.builder()
                .title("bug4255441 Instructions")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(bug4255441::createTestUI)
                .build()
                .awaitAndCheck();
    }
}
