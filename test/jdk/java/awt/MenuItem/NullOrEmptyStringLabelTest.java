/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Button;
import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/*
 * @test
 * @bug 4251036
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary MenuItem setLabel(null/"") behaves differently under Win32 and Solaris
 * @run main/manual NullOrEmptyStringLabelTest
 */

public class NullOrEmptyStringLabelTest {

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                The bug is reproducible under Win32 and Solaris.
                Setting 'null' and "" as a label of menu item
                should set blank label on all platforms according to the specification.
                But under Solaris setting "" as a label of menu item used to
                cause some garbage to be set as label.
                Under Win32 setting 'null' as a label used to result in
                throwing NullPointerException.

                If you see any of these things happen test fails otherwise
                it passes.""";

        PassFailJFrame.builder()
                .title("NullOrEmptyStringLabelTest")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(40)
                .testUI(NullOrEmptyStringLabelTest::createAndShowUI)
                .build()
                .awaitAndCheck();
    }

    private static Frame createAndShowUI() {
        Frame frame = new Frame("Null Or Empty String Label Test");
        Menu menu = new Menu("Menu");
        MenuItem mi = new MenuItem("Item");
        MenuBar mb = new MenuBar();
        Button button1 = new Button("Set MenuItem label to 'null'");
        Button button2 = new Button("Set MenuItem label to \"\"");
        Button button3 = new Button("Set MenuItem label to 'text'");
        button1.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                System.out.println("Setting MenuItem label to null");
                mi.setLabel(null);
            }
        });
        button2.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                System.out.println("Setting MenuItem label to \"\"");
                mi.setLabel("");
            }
        });
        button3.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                System.out.println("Setting MenuItem label to 'text'");
                mi.setLabel("text");
            }
        });
        menu.add(mi);
        mb.add(menu);
        frame.add(button1, BorderLayout.NORTH);
        frame.add(button2, BorderLayout.CENTER);
        frame.add(button3, BorderLayout.SOUTH);
        frame.setMenuBar(mb);
        frame.setSize(200, 135);
        return frame;
    }
}
