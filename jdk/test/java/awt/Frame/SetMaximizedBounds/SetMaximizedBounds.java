/*
 * Copyright (c) 2007, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.*;

/*
 * @test
 * @summary When Frame.setExtendedState(Frame.MAXIMIZED_BOTH)
 *          is called for a Frame after been called setMaximizedBounds() with
 *          certain value, Frame bounds must equal to this value.
 *
 * @library ../../../../lib/testlibrary
 * @build ExtendedRobot
 * @run main SetMaximizedBounds
 */

public class SetMaximizedBounds {

    Frame frame;
    Rectangle bound;
    boolean supported;
    ExtendedRobot robot;
    static Rectangle max = new Rectangle(100,100,400,400);

    public void doTest() throws Exception {
        robot = new ExtendedRobot();

        EventQueue.invokeAndWait( () -> {
            frame = new Frame( "TestFrame ");
            frame.setLayout(new FlowLayout());

            if (Toolkit.getDefaultToolkit().isFrameStateSupported(Frame.MAXIMIZED_BOTH)) {
                supported = true;
                frame.setMaximizedBounds(max);
            } else {
                supported = false;
            }

            frame.setSize(200, 200);
            frame.setVisible(true);
        });

        robot.waitForIdle(2000);
        if (supported) {
            EventQueue.invokeAndWait( () -> {
                frame.setExtendedState(Frame.MAXIMIZED_BOTH);
            });
            robot.waitForIdle(2000);
            bound = frame.getBounds();
            if(!bound.equals(max))
                throw new RuntimeException("The bounds of the Frame do not equal to what"
                    + " is specified when the frame is in Frame.MAXIMIZED_BOTH state");
        } else {
            System.out.println("Frame.MAXIMIZED_BOTH not supported");
        }

        frame.dispose();
    }

    public static void main(String[] args) throws Exception {
        String os = System.getProperty("os.name").toLowerCase();
        System.out.println(os);
        if (os.contains("windows") || os.contains("os x"))
            new SetMaximizedBounds().doTest();
        else
            System.out.println("Platform "+os+" is not supported. Supported platforms are Windows and OS X.");
    }
}
