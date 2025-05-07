/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4237517
 * @summary Tests that scrolling with blit draws the right thing
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4237517
 */

import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JScrollPane;

public class bug4237517 {
    static final String INSTRUCTIONS = """
        Select the first item in the list and hit PageDown
        key two times. If the list is redrawn correctly,
        i.e. if the digits go in order, then the test PASSES.
        Otherwise, the test FAILS.
    """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("bug4237517 Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(bug4237517::createUI)
                .build()
                .awaitAndCheck();
    }

    static JFrame createUI() {
        JFrame f = new JFrame("Scrolling Window Blit Test");
        String[] data = new String[100];

        for (int counter = 0; counter < data.length; counter++) {
            data[counter] = Integer.toString(counter);
        }
        JList list = new JList(data);
        JScrollPane sp = new JScrollPane(list);
        sp.getViewport().putClientProperty("EnableWindowBlit", Boolean.TRUE);
        f.add(sp);
        f.setSize(200, 200);
        return f;
    }
}
