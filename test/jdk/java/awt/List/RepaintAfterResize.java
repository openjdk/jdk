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
 * @bug 6308295
 * @summary XAWTduplicate list item is displayed
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual RepaintAfterResize
*/

import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.List;

public class RepaintAfterResize {

    private static final String INSTRUCTIONS = """
            1) A Frame appears with a list
            2) Resize somehow the frame using mouse
            3) Move down the vertical scrollbar of the list
            4) If you see that two selected items are displayed then the test failed.
               Otherwise, the test passed.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("RepaintAfterResize Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(RepaintAfterResize::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static Frame createTestUI() {
        Frame frame = new Frame("RepaintAfterResize Frame");
        List list = new List(4, false);

        frame.setLayout (new FlowLayout ());
        list.setBounds(100, 100, 100, 100);
        for (int i = 0 ; i < 7 ; i++) {
            list.add(" " + i);
        }
        frame.add(list);
        list.select(3);

        frame.setSize(100, 100);
        return frame;

    }
}
