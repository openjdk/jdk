/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6399659
 * @summary   When minimizing non-focusable window focus shouldn't jump out of the focused window.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MinimizeNonfocusableWindowTest
*/

import java.awt.Frame;
import java.awt.Window;
import java.util.List;

public class MinimizeNonfocusableWindowTest {

    private static final String INSTRUCTIONS = """

             You should see three frames: Frame-1, Frame-2 and Unfocusable.

             1. Click Frame-1 to make it focused window, then click Frame-2.
                Minimize Unfocusable frame with the mouse. If Frame-2 is still
                the focused window continue testing, otherwise press FAIL.

             2. Restore Unfocusable frame to normal state. Try to resize by dragging
                its edge with left mouse button. It should be resizable. If not press
                FAIL. Try the same with right mouse button. It shouldn't resize.
                If it does, press FAIL, otherwise press PASS.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("MinimizeNonfocusableWindowTest Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 1)
                .columns(40)
                .testUI(MinimizeNonfocusableWindowTest::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static List<Window> createTestUI() {
        Frame frame1 = new Frame("Frame-1");
        Frame frame2 = new Frame("Frame-2");
        Frame frame3 = new Frame("Unfocusable");
        frame1.setBounds(100, 0, 200, 100);
        frame2.setBounds(100, 150, 200, 100);
        frame3.setBounds(100, 300, 200, 100);

        frame3.setFocusableWindowState(false);

        return List.of(frame1, frame2, frame3);
    }
}

