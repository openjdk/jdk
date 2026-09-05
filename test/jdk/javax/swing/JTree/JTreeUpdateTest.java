/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8042054
 * @summary JTree.updateUI should use updated item size information
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual JTreeUpdateTest
 */

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.UIManager;

public class JTreeUpdateTest {

    static final String INSTRUCTIONS = """
        A frame with two identical JTrees is shown.
        If the left JTree's text is abbreviated and JTree items
        are cramped with little space between rows then press Fail.
        If the left JTree is identical with right JTree, press Pass.
    """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("JTreeUpdateTest Test instructions")
                .instructions(INSTRUCTIONS)
                .columns(30)
                .testUI(JTreeUpdateTest::createUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createUI() {
        setLaf("javax.swing.plaf.metal.MetalLookAndFeel");

        final JFrame frame = new JFrame("JTreeUpdateTest");

        final JTree tree = new JTree();
        tree.setPreferredSize(new Dimension(200, 200));
        tree.setCellRenderer(new DefaultTreeCellRenderer());
        tree.setBorder(BorderFactory.createTitledBorder("updateUI() called once"));
        frame.add(tree);

        final JTree tree2 = new JTree();
        tree2.setPreferredSize(new Dimension(200, 200));
        tree2.setCellRenderer(new DefaultTreeCellRenderer());
        tree2.setBorder(BorderFactory.createTitledBorder("updateUI() called twice"));
        frame.add(tree2, BorderLayout.EAST);

        frame.pack();
        frame.setLocationRelativeTo(null);

        SwingUtilities.invokeLater(() -> {
            setLaf("javax.swing.plaf.nimbus.NimbusLookAndFeel");
            SwingUtilities.updateComponentTreeUI(frame);
            SwingUtilities.updateComponentTreeUI(tree2);
        });
        return frame;
    }

    private static void setLaf(String className) {
        try {
            UIManager.setLookAndFeel(className);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
