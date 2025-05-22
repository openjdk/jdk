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

import java.awt.Frame;
import java.awt.TextArea;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/*
 * @test
 * @bug 4199397
 * @summary Test to mouse click count
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MouseClickCount
 */

public class MouseClickCount {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. Clicking on Frame panel quickly will produce clickCount larger than 1
                   in the TextArea the count is printed for each mouse click
                2. Verify that a left-button click followed by a right button click quickly
                   will not generate 1, 2, i.e. it's not considered a double clicking.
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

    private static Frame initialize() {
        Frame f = new Frame("Mouse Click Count Test");
        final TextArea ta = new TextArea();
        f.add("South", ta);
        f.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (e.getClickCount() == 1) ta.append("\n1");
                else ta.append(", " + e.getClickCount());
            }
        });
        f.setSize(300, 500);
        return f;
    }
}
