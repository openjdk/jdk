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

/*
 * @test
 * @bug 4476083
 * @summary Disabled components do not receive MouseEvent in Popups
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual PopupTest
 */

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Frame;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

public class PopupTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                PopupMenus should disappear when a disabled component is
                clicked.

                Step 1. Pop down the popup menu by clicking on it.
                Step 2. Click on the disabled component to make the menu
                disappear.

                If the menu disappears when the disabled component is clicked,
                the test passes, otherwise, the test fails.
                """;

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(PopupTest::createUI)
                .build()
                .awaitAndCheck();
    }

    private static Frame createUI() {
        Frame f = new Frame("Disabled Component in Popup Test");
        f.setLayout(new BorderLayout());

        JButton b = new JButton("step 1: press me to display menu");
        b.addActionListener(e -> {
            JPopupMenu m = new JPopupMenu();
            m.add(new JMenuItem("item 1"));
            m.add(new JMenuItem("item 2"));
            m.add(new JMenuItem("item 3"));
            m.add(new JMenuItem("item 4"));
            m.add(new JMenuItem("item 5"));
            m.add(new JMenuItem("item 6"));
            m.show((Component) e.getSource(), 0, 10);
        });

        JLabel disabled = new JLabel("step 2: click me. the menu should be " +
                "dismissed");
        disabled.setEnabled(false);

        JLabel enabled = new JLabel("step 3: there is no step 3");

        f.add(BorderLayout.NORTH, b);
        f.add(BorderLayout.CENTER, disabled);
        f.add(BorderLayout.SOUTH, enabled);
        f.setSize(300, 200);
        return f;
    }
}
