/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Color;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Panel;
import java.awt.ScrollPane;
import java.awt.TextArea;
import java.awt.TextField;
import java.lang.reflect.InvocationTargetException;

/*
 * @test
 * @bug 6198290 6277332
 * @summary TextField painting is broken when placed on a Scrollpane, XToolkit
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual SetBoundsTest
 */

public class SetBoundsTest extends Frame {

    private static final String INSTRUCTIONS = """
            1) Make active a frame with a scrollpane and a few components.
            2) Please, focus attention on the text components
                 placed on the upper half of the frame
            3) Make sure, that the scrollbar of the frame
                 have the same position during the test.
            4) Bring focus to TextField, try deleting the text.
                 If the text never gets erased, the test failed
            5) Bring focus to TextArea, try deleting the text.
                 If the text never gets erased, the test failed
            6) Please, focus attention on the text components
                 placed on the lower half of the frame
            7) Try input something into TextField.
                 If you can not input anything into TextField, the test failed
            8) Try input something into TextArea.
                 If you can not input anything into TextArea, the test failed
            9) The test passed
            """;

    public SetBoundsTest() {
        setTitle("SetBoundsTest Test Frame");
        setLayout(new GridLayout(2, 1));
        Panel hw = new Panel();
        ScrollPane sp = new ScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
        Container lw = new Container();
        fill(hw);
        fill(lw);

        sp.add(hw);
        add(sp);
        add(lw);

        setSize(600, 600);
        sp.setScrollPosition(20, 0);

    }

    private void fill(Container c) {
        Button button = new Button("button");
        c.add(button);
        button.setBackground(new Color(0xd3ceac));
        button.setForeground(new Color(0x000000));
        button.move(60, 80);
        button.resize(400, 60);
        button.show(true);

        TextField textfield = new TextField("textfield");
        c.add(textfield);
        textfield.setBackground(new Color(0xd3ceac));
        textfield.setForeground(new Color(0x000000));
        textfield.move(60, 20);
        textfield.resize(400, 40);
        textfield.show(true);

        TextArea textarea = new TextArea("textarea");
        c.add(textarea);
        textarea.setBackground(new Color(0xd3ceac));
        textarea.setForeground(new Color(0x000000));
        textarea.move(60, 80);
        textarea.resize(400, 60);
        textarea.show(true);

        c.setLayout (new FlowLayout());
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        PassFailJFrame.builder()
                .title("Set Bounds Test Instructions")
                .instructions(INSTRUCTIONS)
                .testUI(SetBoundsTest::new)
                .rows((int) INSTRUCTIONS.lines().count() + 1)
                .columns(40)
                .build()
                .awaitAndCheck();
    }
}
