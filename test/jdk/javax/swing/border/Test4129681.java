/*
 * Copyright (c) 2010, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.event.ItemEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;

/*
 * @test
 * @bug 4129681
 * @summary Tests disabling of titled border's caption
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual Test4129681
 */

public class Test4129681 {
    public static void main(String[] args) throws Exception {
        String testInstructions = """
                Click the checkbox to disable the label.
                The test passes if the title of the border
                is disabled as well as the label.
                """;

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(testInstructions)
                .rows(4)
                .columns(25)
                .splitUI(Test4129681::init)
                .build()
                .awaitAndCheck();
    }

    public static JComponent init() {
        JLabel label = new JLabel("message");
        JCheckBox check = new JCheckBox("Enable/Disable");
        check.addItemListener(event ->
                label.setEnabled(ItemEvent.DESELECTED == event.getStateChange()));
        label.setBorder(BorderFactory.createTitledBorder("label"));
        label.setEnabled(!check.isSelected());

        Box main = Box.createVerticalBox();
        main.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        main.add(check);
        main.add(label);
        return main;
    }
}
