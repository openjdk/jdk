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
 * @bug 4225314
 * @summary Tests that tooltip is painted properly when it has thick border
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4225314
 */

import java.awt.Color;
import java.awt.FlowLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JToolTip;
import javax.swing.border.LineBorder;

public class bug4225314 {
    private static final String INSTRUCTIONS = """
            The word "Tooltip" in both tooltips should not be clipped by the
            black border and be fully visible for this test to pass.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("bug4225314 Instructions")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(bug4225314::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JComponent createTestUI() {
        JToolTip tt1 = new JToolTip();
        tt1.setTipText("Tooltip");
        tt1.setBorder(new LineBorder(Color.BLACK, 10));

        JToolTip tt2 = new JToolTip();
        tt2.setTipText("<html><b><i>Tooltip</i></b></html>");
        tt2.setBorder(new LineBorder(Color.BLACK, 10));

        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout());
        panel.add(tt1);
        panel.add(tt2);

        return panel;
    }
}
