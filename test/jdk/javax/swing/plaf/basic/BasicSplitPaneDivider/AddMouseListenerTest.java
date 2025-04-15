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
 * @bug 4165874
 * @summary Adds a MouseListener to the splitpane divider.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual AddMouseListenerTest
 */

import java.awt.Component;
import java.awt.event.MouseAdapter;

import javax.swing.JFrame;
import javax.swing.JSplitPane;

public class AddMouseListenerTest {
    static final String INSTRUCTIONS = """
        Try dragging the split pane divider, if you can, click PASS,
        else click FAIL.
    """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("AddMouseListenerTest Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(AddMouseListenerTest::createUI)
                .build()
                .awaitAndCheck();
    }

    static JFrame createUI() {
        JFrame f = new JFrame("JSplitPane With ActionListener Test");
        JSplitPane sp = new JSplitPane();

        sp.setContinuousLayout(true);
        Component[] children = sp.getComponents();
        for (int counter = children.length - 1; counter >= 0; counter--) {
            children[counter].addMouseListener(new MouseAdapter() {});
        }
        f.getContentPane().add(sp);
        f.setSize(400, 400);
        return f;
    }
}
