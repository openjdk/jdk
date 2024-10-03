/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4059430
 * @summary Test for component z-ordering consistency
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ZOrderTest
 */

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Panel;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

public class ZOrderTest {
    public final static String INSTRUCTIONS = """
            The ZOrderTest creates two frames.
            - Frame 1 has components added to an intermediate panel
            - Frame 2 has components added directly to the frame itself
            Verify that the components are in the correct z-order. Lower numbered
            components should overlap higher numbered ones (e.g. component zero should
            appear on top of component one).
            Both frames should have the same component ordering, and this ordering should
            be the same on all supported operating systems.
            """;

    public static void main(String [] args) throws InterruptedException,
            InvocationTargetException {
        PassFailJFrame.builder()
                .title("Component ZOrder Test")
                .testUI(ZOrderTest::makeFrames)
                .instructions(INSTRUCTIONS)
                .columns(40)
                .logArea()
                .build()
                .awaitAndCheck();
    }

    private static List<Frame> makeFrames() {
        Frame frame, frame2;

        // test adding components to panel on a frame
        frame = new Frame("ZOrderTest(1) for 4059430");
        frame.pack();
        frame.show();
        Panel panel = new ZOrderPanel();
        frame.setBounds(0, 0, 500, 350);
        frame.setLayout(new GridLayout());
        frame.add(panel);
        doTest(panel);

        // test adding components directly to frame
        frame2 = new ZOrderTestFrame("ZOrderTest(2) for 4059430");
        frame2.pack();
        frame2.show();
        frame2.setBounds(80, 80, 500, 350);
        doTest(frame2);

        return List.of(frame, frame2);
    }

    /*
     * This tests various boundary conditions with z-ordering
     *  - inserting at the top of the z-order
     *  - inserting at the bottom of the z-order
     *  - inserting in the middle of the z-order
     */
    private static void doTest(Container cont) {
        Component compZero, compOne, compTwo, compThree, compFour;

        compZero = makeBox(cont, "Comp One", Color.blue, -1);
        // insert on top
        compOne = makeBox(cont, "Comp Zero", Color.yellow, 0);
        // put at the back
        compThree = makeBox(cont, "Comp Three", Color.red, 2);
        // insert in last position
        compTwo = makeBox(cont, "Comp Two", Color.green, 3);
        // swap compTwo and compThree to correct positions
        cont.remove(compTwo);
        cont.add(compTwo, 2);
        // one more test of adding to the end
        compFour = makeBox(cont, "Comp Four", Color.magenta, -1);
        // re-validate so components cascade into proper place
        cont.validate();
    }

    private static Component makeBox(Container cont, String s, Color c, int index) {
        Label l = new Label(s);
        l.setBackground(c);
        l.setAlignment(Label.RIGHT);
        if (index == -1) {
            cont.add(l); // semantically equivalent to -1, but why not test this too
        } else {
            cont.add(l, index);
        }
        cont.validate();
        return l;
    }

    /**
     * Cascades components across the container so
     * that they overlap, demonstrating their z-ordering
     */
    static void doCascadeLayout(Container cont) {
        int i, n;
        Insets ins = cont.insets();
        n = cont.countComponents();
        for (i = n - 1; i >= 0; i--) {
            Component comp = cont.getComponent(i);
            comp.reshape(ins.left + 75 * i, ins.top + 30 * i, 100, 100);
        }
    }
}

class ZOrderPanel extends Panel {
    public void layout() {
        ZOrderTest.doCascadeLayout(this);
    }
}

class ZOrderTestFrame extends Frame
{
    public ZOrderTestFrame(String title) {
        super(title);
    }

    public void layout() {
        ZOrderTest.doCascadeLayout(this);
    }
}
