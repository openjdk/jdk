/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;

/*
 * @test
 * @bug 4135833
 * @summary Tests that JComboBox draws correctly if the first item in list is an empty string
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4135833
 */

public class bug4135833 {
    private static final String INSTRUCTIONS = """
            Press the combo box. If the popup is readable and appears to be sized properly,
            then it passes. The First item is blank intentionally.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("Instructions")
                .instructions(INSTRUCTIONS)
                .columns(50)
                .testUI(bug4135833::createTestUI)
                .build()
                .awaitAndCheck();
    }

    public static JFrame createTestUI() {
        JFrame frame = new JFrame("bug4135833");
        JPanel panel = new JPanel();
        JComboBox comboBox = new JComboBox(new Object[]{"", "Bob", "Hank", "Joe", "Fred"});
        panel.add(comboBox);
        frame.add(panel);
        frame.pack();
        return frame;
    }
}
