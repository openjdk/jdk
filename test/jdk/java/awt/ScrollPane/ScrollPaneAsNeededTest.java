/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4152524
 * @summary ScrollPane AS_NEEDED always places scrollbars first time component
 *          is laid out
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ScrollPaneAsNeededTest
 */

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Frame;
import java.awt.ScrollPane;

public class ScrollPaneAsNeededTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. You will see a frame titled 'ScrollPane as needed'
                   of minimum possible size in the middle of the screen.
                2. If for the first resize of frame(using mouse) to
                   a very big size(may be, to half the area of the screen)
                   the scrollbars(any - horizontal, vertical or both)
                   appear, click FAIL else, click PASS.
                                           """;
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(ScrollPaneAsNeededTest::initialize)
                .build()
                .awaitAndCheck();
    }

    static Frame initialize() {
        Frame f = new Frame("ScrollPane as needed");
        f.setLayout(new BorderLayout());
        ScrollPane sp = new ScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
        sp.add(new Button("TEST"));
        f.add("Center", sp);
        f.setSize(200, 200);
        return f;
    }
}
