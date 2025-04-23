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
 * @bug 4196987
 * @summary Test Metal L&F JTree expander icons transparency.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4196987
 */

import java.awt.Color;
import java.awt.GridLayout;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.UIManager;

public class bug4196987 {
    static final String INSTRUCTIONS = """
        If the background of tree icons are red, the test PASSES.
        Otherwise the test FAILS.
    """;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        PassFailJFrame.builder()
                .title("bug4196987 Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(bug4196987::createUI)
                .build()
                .awaitAndCheck();
    }

    static JFrame createUI() {
        JFrame f = new JFrame("JTree Icon Transparency Test");
        JPanel p = new JPanel();
        p.setBackground(Color.red);
        p.setLayout(new GridLayout(1, 1));
        JTree t = new JTree();
        t.setOpaque(false);
        p.add(t);
        f.add(p);
        f.setSize(200, 200);
        return f;
    }
}
