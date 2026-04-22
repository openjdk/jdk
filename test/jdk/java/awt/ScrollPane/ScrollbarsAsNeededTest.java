/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4094248
 * @summary Test initial appearance of SCROLLBARS_AS_NEEDED policy
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ScrollbarsAsNeededTest
 */

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.ScrollPane;

public class ScrollbarsAsNeededTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. A Frame window with a ScrollPane that is
                   initially created with the SCROLLBARS_AS_NEEDED policy.
                2. If there are no scrollbars around the ScrollPane then
                    the test PASS. Otherwise the test FAILS.
                """;
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(ScrollbarsAsNeededTest::initialize)
                .build()
                .awaitAndCheck();
    }

    static Frame initialize() {
        Frame frame = new Frame("Scrollbar as needed test");
        ScrollPane scrollPane = new ScrollPane() {
            @Override
            public void paint(Graphics g) {
                super.paint(g);
                g.drawString("ScrollPane", 10, 50);
            }
        };
        scrollPane.setBackground(Color.WHITE);
        frame.setBackground(Color.GRAY);
        frame.setSize(200, 200);
        frame.setLayout(new FlowLayout());
        frame.add(scrollPane);
        return frame;
    }
}
