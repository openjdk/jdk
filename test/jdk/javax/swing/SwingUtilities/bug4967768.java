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
 * @bug 4967768
 * @requires (os.family != "mac")
 * @summary Tests that underline is painted correctly in mnemonics
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4967768
 */

import java.awt.Font;
import javax.swing.JButton;
import javax.swing.JPanel;

public class bug4967768 {
    private static final String INSTRUCTIONS = """
            When the test starts you'll see a button "Oops"
            with the "p" letter underlined at the bottom
            of the instruction frame.

            Ensure the underline cuts through the descender
            of letter "p", i.e. the underline is painted
            not below the letter but below the baseline.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(35)
                .splitUIBottom(bug4967768::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JPanel createTestUI() {
        JPanel panel = new JPanel();
        JButton but = new JButton("Oops");
        but.setFont(new Font("Dialog", Font.BOLD, 24));
        but.setMnemonic('p');
        panel.add(but);
        return panel;
    }
}
