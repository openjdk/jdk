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
 * @bug 4294808
 * @summary Tooltip blinking.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4294808
 */

import java.awt.Dimension;
import javax.swing.JButton;
import javax.swing.JComponent;

public class bug4294808 {
    private static final String INSTRUCTIONS = """
        Move mouse cursor to the button named "Tooltip Button"
        at the bottom of the instruction window and wait until
        tooltip has appeared.

        If tooltip appears and eventually disappears without
        rapid blinking then press PASS else FAIL.
        """;


    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("bug4294808 Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .splitUIBottom(bug4294808::createAndShowUI)
                .build()
                .awaitAndCheck();
    }

    private static JComponent createAndShowUI() {
        JButton bt = new JButton("Tooltip Button");
        bt.setToolTipText("Long tooltip text here");
        bt.setPreferredSize(new Dimension(200, 60));
        return bt;
    }
}
