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
 * @bug 4207474 4218495 4375928
 * @summary Tests various tooltip issues: HTML tooltips, long tooltip text
 *          and mnemonic keys displayed in tooltips
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TooltipTest
 */

import java.awt.FlowLayout;
import java.awt.event.KeyEvent;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.UIManager;

public class TooltipTest {
    private static final String INSTRUCTIONS = """
        1.  Move the mouse over the button labeled "Red tip" and let it stay
            still in order to test HTML in JToolTip. If the tooltip has some
            text which is red then test passes, otherwise it fails (bug 4207474).

        2.  Move the mouse over the button labeled "Long tip".
            If the last letter of the tooltip appears clipped,
            then the test fails. If you can see the entire last character,
            then the test passes (bug 4218495).

        3.  Verify that "M" is underlined on the button labeled "Mnemonic"
            Move the mouse pointer over the button labeled "Mnemonic" and look
            at tooltip when it appears. It should read "hint".
            If the above is true test passes else test fails (bug 4375928).
        """;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");

        PassFailJFrame.builder()
                .title("TooltipTest Instructions")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(TooltipTest::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JComponent createTestUI() {
        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout());

        JButton b = new JButton("Red tip");
        b.setToolTipText("<html><center>Here is some <font color=red>" +
                "red</font> text.</center></html>");
        panel.add(b);

        b = new JButton("Long tip");
        b.setToolTipText("Is the last letter clipped?");
        panel.add(b);

        b = new JButton("Mnemonic");
        b.setMnemonic(KeyEvent.VK_M);
        b.setToolTipText("hint");
        panel.add(b);

        return panel;
    }
}
