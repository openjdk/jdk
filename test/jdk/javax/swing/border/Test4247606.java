/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

/*
 * @test
 * @bug 4247606
 * @summary BorderedPane appears wrong with title position below bottom
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual Test4247606
 */

public class Test4247606 {
    public static void main(String[] args) throws Exception {
        String testInstructions = """
                If the button does not fit into the titled border bounds
                and cover the bottom border's line then test fails.
                Otherwise test passes
                """;

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(testInstructions)
                .rows(4)
                .columns(35)
                .splitUI(Test4247606::initializeTest)
                .build()
                .awaitAndCheck();
    }

    public static JComponent initializeTest() {
        JButton button = new JButton("Button");
        button.setBorder(BorderFactory.createLineBorder(Color.red, 1));

        TitledBorder border = new TitledBorder("Bordered Pane");
        border.setTitlePosition(TitledBorder.BELOW_BOTTOM);

        JPanel panel = create(button, border);
        panel.setBackground(Color.green);
        panel.setPreferredSize(new Dimension(200, 150));

        return create(panel, BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    private static JPanel create(JComponent component, Border border) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(border);
        panel.add(component);
        return panel;
    }
}
