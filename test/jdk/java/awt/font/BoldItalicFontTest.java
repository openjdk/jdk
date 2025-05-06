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

import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Label;

/*
 * @test
 * @bug 4935871
 * @summary Check that correct type faces are used regardless of bold/italic styles
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual/othervm -Duser.language=ja -Duser.country=JP BoldItalicFontTest
 */

public class BoldItalicFontTest {

    public static void main(String[] args) throws Exception {
        final String INSTRUCTIONS = """
                This test is reproduced with a non-English user locale only.
                All the letters "X" in the first line should be in serif font.
                All the letters "X" in the second line should be in sans-serif font.

                If so, press Pass, else press Fail.""";

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(BoldItalicFontTest::createUI)
                .build()
                .awaitAndCheck();
    }

    private static Frame createUI() {
        String[] faces = { Font.SERIF, Font.SANS_SERIF };
        int[] styles = { 0, Font.BOLD, Font.ITALIC, Font.BOLD | Font.ITALIC };

        Frame f = new Frame("BoldItalicFontTest Test UI");
        f.setLayout(new GridLayout(faces.length, styles.length));
        for (int fn = 0; fn < faces.length; fn++) {
            for (int sn = 0; sn < styles.length; sn++) {
                Label l = new Label("X");
                Font f1 = new Font(faces[fn], styles[sn], 36);
                l.setFont(f1);
                f.add(l);
            }
        }
        f.setSize(300, 300);
        return f;
    }
}
