/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Event;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.TextArea;

/*
 * @test
 * @bug 4092370
 * @summary Test to verify double click
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual DoubleClickTest
 */

public class DoubleClickTest {
    static TextArea ta = new TextArea("", 10, 40);

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. Double click on the red area.
                2. Verify that the event reports click_count > 1 on
                   Double-Click. If click_count shows only 1 for every
                   Double-Clicks then test FAILS, else test PASS.
                """;
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(initialize())
                .build()
                .awaitAndCheck();
    }

    public static Frame initialize() {
        Frame frame = new Frame("Double-click Test");
        frame.setLayout(new BorderLayout());
        frame.add("East", new MyPanel(ta));
        frame.add("West", ta);
        frame.setSize(200, 200);
        return frame;
    }
}

class MyPanel extends Panel {
    TextArea ta;

    MyPanel(TextArea ta) {
        this.ta = ta;
        setBackground(Color.red);
    }

    public Dimension getPreferredSize() {
        return new Dimension(50, 50);
    }


    public boolean mouseDown(Event event, int x, int y) {
        ta.append("event click count= " + event.clickCount + "\n");
        return false;
    }
}
