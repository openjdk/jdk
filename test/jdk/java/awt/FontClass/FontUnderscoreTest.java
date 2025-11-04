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

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;

import javax.swing.JPanel;

/*
 * @test
 * @bug 4248579
 * @summary Make sure the underscore glyph appears in the different strings
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FontUnderscoreTest
 */

public class FontUnderscoreTest extends JPanel {
    public static void main(String[] args) throws Exception {
        final String INSTRUCTIONS = """
                Make sure all 8 underscore characters appear in each
                of the 3 strings.

                Press PASS if all 8 are there, else FAIL.""";

        PassFailJFrame.builder()
                .title("FontUnderscoreTest Instruction")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .splitUI(FontUnderscoreTest::new)
                .build()
                .awaitAndCheck();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(550, 230);
    }

    @Override
    public void paint(Graphics g) {
        Font f = new Font(Font.SANS_SERIF, Font.PLAIN, 24);
        g.setFont(f);
        g.drawString ("8 underscore characters appear in each string", 5, 200);

        g.drawString("J_A_V_A_2_j_a_v_a", 25, 50);

        f = new Font(Font.SERIF, Font.PLAIN, 24);
        g.setFont(f);
        g.drawString("J_A_V_A_2_j_a_v_a", 25, 100);

        f = new Font(Font.MONOSPACED, Font.PLAIN, 24);
        g.setFont(f);
        g.drawString("J_A_V_A_2_j_a_v_a", 25, 150);
    }
}
