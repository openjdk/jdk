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
 * @bug 4188825
 * @summary Tests if toolbars return to original location when closed
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4188825
 */

import java.awt.BorderLayout;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JToolBar;

public class bug4188825 {
    static final String INSTRUCTIONS = """
        Drag the toolbar out of frame and close it. If it returns to
        the original location, then the test succeeded, otherwise it failed.
    """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("bug4188825 Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(bug4188825::createUI)
                .build()
                .awaitAndCheck();
    }

    static JFrame createUI() {
        JFrame frame = new JFrame("Toolbar Drag Test");
        frame.setLayout(new BorderLayout());
        JToolBar tb = new JToolBar();
        tb.setOrientation(JToolBar.VERTICAL);
        tb.add(new JButton("a"));
        tb.add(new JButton("b"));
        tb.add(new JButton("c"));
        frame.add(tb, BorderLayout.WEST);
        JButton l = new JButton("Get me!!!");
        l.setSize(200, 200);
        frame.add(l);
        frame.setSize(200, 200);
        return frame;
    }
}
