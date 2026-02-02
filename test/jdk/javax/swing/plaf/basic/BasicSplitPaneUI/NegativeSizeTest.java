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
 * @bug 4199666
 * @summary Makes sure initial negative size of a component does not confuse
 *          JSplitPane.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual NegativeSizeTest
 */

import java.awt.BorderLayout;
import java.awt.CardLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

public class NegativeSizeTest {
    static final String INSTRUCTIONS = """
        Click on the 'Show JSplitPane' button. If two buttons appear,
        click PASS, otherwise click FAIL.
    """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("NegativeSizeTest Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(NegativeSizeTest::createUI)
                .build()
                .awaitAndCheck();
    }

    static JFrame createUI() {
        JFrame f = new JFrame("Negative Size Test");
        CardLayout cardLayout = new CardLayout();
        JPanel mainPanel = new JPanel(cardLayout);

        JSplitPane splitPane = new JSplitPane();
        splitPane.setContinuousLayout(true);
        JPanel splitContainer = new JPanel(new BorderLayout());
        splitContainer.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        splitContainer.add(splitPane, BorderLayout.CENTER);

        if (false) {
            mainPanel.add(splitContainer, "split");
            mainPanel.add(new JPanel(), "blank");
        }
        else {
            mainPanel.add(new JPanel(), "blank");
            mainPanel.add(splitContainer, "split");
        }

        f.add(mainPanel, BorderLayout.CENTER);

        JButton button = new JButton("Show JSplitPane");
        button.addActionListener(e -> cardLayout.show(mainPanel, "split"));
        f.add(button, BorderLayout.SOUTH);
        f.setSize(400, 300);
        return f;
    }
}
