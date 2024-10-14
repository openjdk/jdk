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

/*
 * @test
 * @bug 6246467
 * @summary Tests that list works correctly if user specified foreground colors on XToolkit/Motif
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual SetForegroundTest
 */

import java.awt.Button;
import java.awt.Component;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Label;
import java.awt.List;
import java.awt.Panel;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.ScrollPane;

public class SetForegroundTest {

    private static final String INSTRUCTIONS = """
        To make sure, that for each component
        (Button, Checkbox, Label, List, TextArea, TextField, Choice)
        in the frame,
        the title exist and the color of the title is red.
        If not, the test failed.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("SetForegroundTest Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(SetForegroundTest::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static Frame createTestUI() {
        Frame frame = new Frame();
        ScrollPane sp = new ScrollPane() {
            public Dimension getPreferredSize() {
                return new Dimension(180, 180);
            }
        };
        Panel p = new Panel();
        Component childs[] = new Component[] {new Button("button"),
                                              new Checkbox("checkbox"),
                                              new Label("label"),
                                              new List(3, false),
                                              new TextArea("text area"),
                                              new TextField("text field"),
                                              new Choice()};

        p.setLayout (new FlowLayout ());

        sp.add(p);

        sp.validate();

        frame.add(sp);
        for (int i = 0; i < childs.length; i++){
            childs[i].setForeground(Color.red);
        }

        for (int i = 0; i < childs.length; i++) {
            p.add(childs[i]);
            if (childs[i] instanceof List) {
                ((List)childs[i]).add("list1");
                ((List)childs[i]).add("list2");
            } else if (childs[i] instanceof Choice) {
                ((Choice)childs[i]).add("choice1");
                ((Choice)childs[i]).add("choice2");
            }
        }
        frame.pack();
        return frame;
    }
}
