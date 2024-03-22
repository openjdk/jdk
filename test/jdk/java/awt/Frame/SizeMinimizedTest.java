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

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Frame;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/*
 * @test
 * @bug 4065534
 * @key headful
 * @summary Frame.setSize() doesn't change size if window is in an iconified state
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual SizeMinimizedTest
 */

public class SizeMinimizedTest {
    private static Frame frame2;

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                        (While this test runs, frame2 size and position will
                        continuously be logged, so you can verify its changes)
                        1. When the test starts, two frame windows will appear.
                        Note the size of frame2.
                        2. Minimize frame2 then click the resize button
                        3. Restore frame2, it should be square and 200x200 pixels.
                        4. Note the position of frame2.
                        5. Minimize frame2 and click the move button several times.
                        6. Restore frame2, it should have shifted to the right by
                        about 10 pixels for every time move was clicked.
                """;

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .rows(11)
                .columns(50)
                .testUI(SizeMinimizedTest::initialize)
                .logArea()
                .build()
                .awaitAndCheck();
    }

    public static List<Frame> initialize() {
        Frame frame1 = new Frame("frame1");
        Button resize = new Button("resize");
        frame1.add(resize, BorderLayout.NORTH);
        resize.addActionListener(actionEvent -> {
            PassFailJFrame.log("** Setting size to 200, 200 **");
            frame2.setSize(200, 200);
        });
        Button move = new Button("move");
        frame1.add(move, BorderLayout.CENTER);
        move.addActionListener(actionEvent -> {
            PassFailJFrame.log("** Moving right 10 pixels **");
            Point pt = frame2.getLocation();
            frame2.setLocation(pt.x + 10, pt.y);
        });
        Button quit = new Button("quit");
        frame1.add(quit, BorderLayout.SOUTH);
        quit.addActionListener(actionEvent -> System.exit(0));

        frame1.setSize(100, 100);
        frame1.setLocation(10, 10);

        frame2 = new Frame("frame2");
        frame2.setSize(100, 100);
        frame2.setLocation(50, 50);
        PassFailJFrame.addTestWindow(frame1);
        PassFailJFrame.addTestWindow(frame2);
        PassFailJFrame.positionTestWindow(frame1,
                PassFailJFrame.Position.HORIZONTAL);
        PassFailJFrame.positionTestWindow(frame2,
                PassFailJFrame.Position.VERTICAL);
        frame1.setVisible(true);
        frame2.setVisible(true);
        List<Frame> frameList = new ArrayList<>();
        frameList.add(frame1);
        frameList.add(frame2);
        return frameList;
    }
}
