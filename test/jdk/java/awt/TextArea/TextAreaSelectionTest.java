/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.TextArea;
import java.awt.TextField;

/*
 * @test
 * @bug 4095946
 * @summary 592677:TEXTFIELD TAB SELECTION CONFUSING; REMOVE ES_NOHIDESEL STYLE IN
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TextAreaSelectionTest
 */

public class TextAreaSelectionTest {
    private static final String INSTRUCTIONS = """
                Please look at the 'TextAreaSelectionTest' frame.

                If you see that the all TextFields and TextAreas have
                the highlighted selections, the test FAILED. Else, if
                you see that the text of the focused component is
                highlighted, it is ok.

                Try to traverse the focus through all components by
                pressing CTRL+TAB. If the focused component highlights
                its selection, the test is passed for a while.

                Please select the entire/part of the text of some component
                by mouse and choose some menu item. If the highlighted
                selection is hidden, the test FAILED.

                Please select the entire/part of the text of some component
                by mouse and click right mouse button. A context menu
                should appear. Please check its items.
                Press ESC to hide the context menu. If the selection
                of the text component is not visible, the test FAILED.

                Please double click on the word 'DoubleClickMe' in the
                first text area. If there are several words selected, the
                test FAILED, if the word 'DoubleClickMe' is selected only,
                the test PASSED!
                """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("TextAreaSelectionTest")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(40)
                .testUI(TextAreaSelectionTest::createGUI)
                .build()
                .awaitAndCheck();
    }

    public static Frame createGUI() {
        Frame f = new Frame("TextAreaSelectionTest");
        f.setLayout(new FlowLayout());

        MenuBar mb = new MenuBar();
        String name = "Submenu";
        Menu m = new Menu(name, false);
        m.add(new MenuItem(name + " item 1"));
        m.add(new MenuItem(name + " item 2"));
        m.add(new MenuItem(name + " item 3"));
        mb.add(m);

        TextField tf1, tf2;
        TextArea ta1, ta2;
        f.setMenuBar(mb);
        f.add(tf1 = new TextField("some text"));
        f.add(tf2 = new TextField("more text"));
        String eoln = System.getProperty("line.separator", "\n");
        f.add(ta1 = new TextArea("some text" + eoln + eoln + "DoubleClickMe"));
        f.add(ta2 = new TextArea("more text"));

        tf1.selectAll();
        tf2.selectAll();
        ta1.selectAll();
        ta2.selectAll();

        f.pack();
        return f;
    }

}
