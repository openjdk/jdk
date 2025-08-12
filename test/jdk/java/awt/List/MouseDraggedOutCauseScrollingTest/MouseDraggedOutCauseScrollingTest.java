/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @bug 6243382 8006070
  @summary Dragging of mouse outside of a List and Choice area don't work properly on XAWT
  @requires (os.family == "linux")
  @library /java/awt/regtesthelpers
  @run main/manual MouseDraggedOutCauseScrollingTest
*/

import java.awt.Choice;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.List;
import java.awt.Toolkit;

public class MouseDraggedOutCauseScrollingTest {

    static Frame createUI() {
        Frame frame = new Frame("MouseDraggedOutCausesScrollingTest");
        frame.setLayout(new GridLayout(1, 3));

        Choice choice = new Choice();
        List singleList = new List(3, false);
        List multipleList = new List(3, true);

        choice.add("Choice");
        for (int i = 1; i < 100; i++){
            choice.add(""+i);
        }

        singleList.add("Single list");
        for (int i = 1; i < 100; i++)
            singleList.add(""+i);

        multipleList.add("Multiple list");
        for (int i = 1; i < 100; i++)
            multipleList.add(""+i);

        frame.add(choice);
        frame.add(singleList);
        frame.add(multipleList);
        frame.setSize(400, 100);
        return frame;
    }

    public static void main(String[] args) throws Exception {
        String toolkitName = Toolkit.getDefaultToolkit().getClass().getName();

        if (!toolkitName.equals("sun.awt.X11.XToolkit")) {
              System.out.println(INAPPLICABLE);
              return;
        }

        PassFailJFrame
            .builder()
            .instructions(INSTRUCTIONS)
            .rows(40)
            .columns(70)
            .testUI(MouseDraggedOutCauseScrollingTest::createUI)
            .build()
            .awaitAndCheck();
    }

    static final String INAPPLICABLE = "The test is not applicable to the current platform. Test PASSES.";
    static final String INSTRUCTIONS = """
            0) Please note, that this is an XAWT/Linux only test. First, make the test window is active.
            -----------------------------------
            1.1) Click on the Choice.
            1.2) Press and hold down the left button of the mouse to select (eg) item 5 in the choice.
            1.3) Drag the mouse vertically out of the area of the open list,
                 keeping the X coordinate of the mouse position about the same.
            1.4) Check that when the Y coordinate of the mouse position is higher than the upper bound of the list
                 then the list continues to scrolls UP and the selected item changes at the top until you reach the topmost item.
                 If not, the test failed. Press FAIL.
            1.5) Check that when the Y coordinate of the mouse position is lower than the lower bound of the list
                 then the list continues to scroll DOWN and the selected item changes at the bottom until you reach the bottommost item.
                 If not, the test failed. Press FAIL.
            -----------------------------------
            2.1) Click on the Single List.
            2.2) Press and hold down the left button of the mouse to select (eg) item 5 in the list.
            2.3) Drag the mouse vertically out of the area of the open list,
                 keeping the X coordinate of the mouse position about the same.
            2.4) Check that when the Y coordinate of the mouse position is higher than the upper bound of the list
                 then the list continues to scrolls UP and the selected item changes at the top until you reach the topmost item.
                 If not, the test failed. Press FAIL.
            2.5) Check that when the Y coordinate of the mouse position is lower than the lower bound of the list
                 then the list continues to scroll DOWN and the selected item changes at the bottom until you reach the bottommost item.
                 If not, the test failed. Press FAIL.
            -----------------------------------
            3.1) Click on the Multiple List.
            3.2) Press and hold down the left button of the mouse to select (eg) item 5 in the list.
            3.3) Drag the mouse vertically out of the area of the open list,
                 keeping the X coordinate of the mouse position about the same.
            3.4) Check that when the Y coordinate of the mouse is higher than the upper bound of the list
                 that scrolling of the list DOES NOT OCCUR and the selected item IS UNCHANGED at the top.
                 If not, the test failed. Press FAIL.
            3.5) Check that when the Y coordinate of the mouse is below the lower bound of the list
                 that scrolling of the list DOES NOT OCCUR and the selected item IS UNCHANGED at the bottom.
                 If not, the test failed. Press FAIL.
            -----------------------------------
            4) The test has now passed. Press PASS.
    """;
}
