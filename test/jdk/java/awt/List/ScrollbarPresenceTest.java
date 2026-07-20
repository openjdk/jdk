/*
 * Copyright (c) 2006, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6336384
 * @summary ScrollBar does not show up correctly
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ScrollbarPresenceTest
*/

import java.awt.Font;
import java.awt.Frame;
import java.awt.List;

public class ScrollbarPresenceTest {

    private static final String INSTRUCTIONS = """
        You will see a list,
        If a vertical scrollbar appears on the list and the list is big enough
        to show all items then the test failed else the test passed.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("ScrollbarPresenceTest Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(ScrollbarPresenceTest::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static Frame createTestUI() {
        Frame frame = new Frame("ScrollbarPresenceTest Frame");
        List list = new List();

        for (int i = 0; i < 6; i++) {
            list.addItem("Row " + i);
        }

        list.setFont(new Font("MonoSpaced", Font.PLAIN, 12));
        list.setBounds(30, 30, 128, 104);
        frame.add(list);

        frame.pack();
        return frame;
    }

}
