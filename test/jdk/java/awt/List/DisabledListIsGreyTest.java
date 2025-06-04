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
 * @bug 6354810
 * @summary Items in the list are not grayed out when disabled, XToolkit
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual DisabledListIsGreyTest
*/

import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.List;

public class DisabledListIsGreyTest {

    private static final String INSTRUCTIONS = """
            1) After the test started you will see two lists.
            2) One of them is enabled, and the second is disabled.
            3) Check that the items of the disabled list are grayed.
            4) If so, the test passed. Otherwise, failed.""";


    public static void main(String[] args) throws Exception {
         PassFailJFrame.builder()
                .title("DisabledListIsGreyTest Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(DisabledListIsGreyTest::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static Frame createTestUI() {
        Frame frame = new Frame("DisabledListIsGreyTest Frame");
        frame.setLayout(new FlowLayout());

        List list1 = new List(3);
        List list2 = new List(3);
        for (int i = 0; i < 5; i++) {
            list1.addItem("Item " + i);
            list2.addItem("Item " + i);
        }
        frame.add(list1);

        list2.setEnabled(false);
        frame.add(list2);
        frame.pack();
        return frame;
    }

}
