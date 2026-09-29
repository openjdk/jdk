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
 * @bug 4472032
 * @summary Switching between lightweight menus by horizontal arrow key works incorrect
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual LightweightPopupTest
*/

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

public class LightweightPopupTest {

    private static final String INSTRUCTIONS = """
            When the test starts, you will see a frame titled
            'Lightweight Popup Test', which contains a button
            (titled 'JButton') and two menus ('Menu 1' and 'Menu 2').
            Make sure that both menus, when expanded, fit entirely
            into the frame. Now take the following steps:
                1. Click on 'JButton' to focus it.
                2. Click 'Menu 1' to expand it.
                3. Press right arrow to select 'Menu 2'.
            Now check where the focus is. If it is on 'JButton'
            (you can press space bar to see if it is there), then
            the test failed. If 'JButton' is not focused, then
            the test passed.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("LightweightPopupTest Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int)INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(LightweightPopupTest::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {

        JFrame frame = new JFrame("Lightweight Popup Test");
        JButton button = new JButton("JButton");
        JMenuBar menuBar = new JMenuBar();
        JMenu menu1 = new JMenu("Menu 1");
        menu1.add(new JMenuItem("Menu Item 1"));
        menu1.add(new JMenuItem("Menu Item 2"));
        menuBar.add(menu1);
        JMenu menu2 = new JMenu("Menu 2");
        menu2.add(new JMenuItem("Menu Item 3"));
        menu2.add(new JMenuItem("Menu Item 4"));
        menuBar.add(menu2);

        frame.add(button);
        frame.setJMenuBar(menuBar);
        frame.setSize(300, 200);
        return frame;
    }

}

