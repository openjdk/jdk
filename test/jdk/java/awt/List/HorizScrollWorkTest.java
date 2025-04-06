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
 * @bug 6355467
 * @summary Horizontal scroll bar thumb of a List does not stay at the end
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @requires (os.family == "linux")
 * @run main/manual HorizScrollWorkTest
*/

import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.List;

public class HorizScrollWorkTest {

    private static final String INSTRUCTIONS = """
            This is a linux only test.
            Drag and drop the horizontal scroll bar thumb at the right end.
            If the thumb does not stay at the right end, then the test failed. Otherwise passed.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("HorizScrollWorkTest Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int)INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(HorizScrollWorkTest::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static Frame createTestUI() {
        Frame frame = new Frame("HorizScrollWorkTest Frame");
        List list = new List(4);

        frame.setLayout (new FlowLayout());

        list.add("veryyyyyyyyyyyyyyyyyyyyyyyyyy longgggggggggggggggggggggg stringggggggggggggggggggggg");

        frame.add(list);
        frame.pack();

        return frame;
    }
}
