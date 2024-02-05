/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Button;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import javax.swing.SwingUtilities;

/*
 * @test
 * @bug 4071086
 * @key headful
 * @summary Test dynamically adding and removing a menu bar
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual AddRemoveMenuBarTest_4
 */

public class AddRemoveMenuBarTest_4 {

    private static final String INSTRUCTIONS = """
            There is a frame with a menubar and a single button.

            The button is labelled 'Add new MenuBar'.

            If you click the button, the menubar is replaced with another menubar.
            This can be done repeatedly.

            The <n>-th menubar contains one menu, 'TestMenu<n>',
            with two items, 'one <n>' and 'two <n>'.

            Click again to replace the menu bar with another menu bar.

            After a menubar has been replaced with another menubar,
            the frame should not be resized nor repositioned on the screen.

            Upon test completion, click Pass or Fail appropriately.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame passFailJFrame = new PassFailJFrame.Builder()
                .title("AddRemoveMenuBarTest_4 Instructions")
                .instructions(INSTRUCTIONS)
                .testTimeOut(5)
                .rows(18)
                .columns(45)
                .build();

        SwingUtilities.invokeAndWait(() -> {
            AddRemoveMenuBar_4 frame = new AddRemoveMenuBar_4();

            PassFailJFrame.addTestWindow(frame);
            PassFailJFrame.positionTestWindow(frame,
                    PassFailJFrame.Position.HORIZONTAL);

            frame.setVisible(true);
        });

        passFailJFrame.awaitAndCheck();
    }
}

class AddRemoveMenuBar_4 extends Frame {
    int count = 1;
    MenuBar mb = null;

    AddRemoveMenuBar_4() {
        super("AddRemoveMenuBar_4");
        setLayout(new FlowLayout());

        Button b = new Button("Add new MenuBar");
        b.addActionListener((e) -> createMenuBar());
        add(b);

        createMenuBar();

        setSize(300, 300);
    }

    void createMenuBar() {
        if (mb != null) {
            remove(mb);
        }

        mb = new MenuBar();
        Menu m = new Menu("TestMenu" + count);
        m.add(new MenuItem("one " + count));
        m.add(new MenuItem("two " + count));
        count++;
        mb.add(m);
        setMenuBar(mb);
    }
}
