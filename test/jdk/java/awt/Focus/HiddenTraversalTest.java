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
/*
 * @test
 * @bug 4157017
 * @summary Checks whether focus can be traversed when component not visible
           within parent container.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual HiddenTraversalTest
*/

import java.awt.Button;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Panel;

public class HiddenTraversalTest {

    private static final String INSTRUCTIONS = """
         Examine the Frame. If six buttons are visible, resize the frame
         so that only four are visible. If fewer than six buttons are
         visible, do nothing.\n
         Now, repeatedly press the tab key. Focus should cycle through the
         visible and invisible buttons. If after six presses of the tab
         button 'Button 0' has focus, the test passes. If focus is instead
         stuck at 'Button 3', the test fails.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("HiddenTraversalTest Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(HiddenTraversalTest::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static Frame createTestUI() {
        Frame f = new Frame("Focus test");
        Panel p = new Panel(new FlowLayout());
        for (int i = 0; i < 6; i++) {
            p.add(new Button("Button " + i));
        }
        f.add(p);
        f.setSize(200, 100);
        return f;
    }

}

