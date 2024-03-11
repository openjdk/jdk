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

import java.awt.BorderLayout;
import java.awt.event.ItemEvent;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/*
 * @test
 * @bug 4129681
 * @summary Tests enabling/disabling of titled border's caption
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual Test4129681
 */

public class Test4129681 {
    private static JLabel label;
    private static JFrame frame;

    public static void main(String[] args) throws Exception {
        String testInstructions = """
                When frame starts, you'll see a checkbox
                and a label with a titled border.
                Turn on the checkbox to disable the label.
                The test passes if the title of the border
                is disabled as well as the label.
                """;
        PassFailJFrame passFailJFrame = new PassFailJFrame.Builder()
                .title("JInternalFrame Instructions")
                .instructions(testInstructions)
                .testTimeOut(5)
                .rows(6)
                .columns(35)
                .build();

        SwingUtilities.invokeAndWait(() -> {
            init();
        });
        passFailJFrame.awaitAndCheck();
    }

    public static void init() {
        JCheckBox check = new JCheckBox("Enable/Disable");
        frame = new JFrame("Test Border Enable/Disable");
        check.addItemListener(event ->
                label.setEnabled(ItemEvent.DESELECTED == event.getStateChange()));

        label = new JLabel("message");
        label.setBorder(BorderFactory.createTitledBorder("label"));
        label.setEnabled(!check.isSelected());

        frame.add(BorderLayout.NORTH, check);
        frame.add(BorderLayout.CENTER, label);
        frame.setSize(300, 300);
        PassFailJFrame.addTestWindow(frame);
        PassFailJFrame.positionTestWindow(frame, PassFailJFrame.Position.TOP_LEFT_CORNER);
        frame.setVisible(true);
    }
}
