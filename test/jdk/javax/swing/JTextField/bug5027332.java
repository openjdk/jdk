/*
 * Copyright (c) 2004, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5027332
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Tests that textfield caret is placed slightly off textfield borders
 * @run main/manual bug5027332
 */

import java.awt.BorderLayout;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.UIManager;

public class bug5027332 {

    private static final String INSTRUCTIONS = """
            Click into the text field so that caret appears inside.
            The caret should be placed slightly off text field borders,
            so that it can be easily distinguished from the border.
            Test fails if the caret touches the border.""";

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        PassFailJFrame.builder()
                .title("bug5027332 Instructions")
                .instructions(INSTRUCTIONS)
                .rows(6)
                .columns(35)
                .testUI(bug5027332::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {
        JFrame frame = new JFrame("bug5027332");
        JTextField t = new JTextField(10);
        JPanel p = new JPanel();
        p.add(t, BorderLayout.CENTER);
        frame.setContentPane(p);
        frame.pack();
        return frame;
    }
}
