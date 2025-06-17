/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4313052
 * @summary Test cursor changes after mouse dragging ends
 * @run main/manual ListDragCursor
 */

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Cursor;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.List;
import java.awt.Panel;
import java.awt.TextArea;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ListDragCursor {
    static Frame testFrame;
    static Frame instructionsFrame;
    private static final CountDownLatch countDownLatch = new CountDownLatch(1);
    static String INSTRUCTIONS = """
                1. Move mouse to the TextArea.
                2. Press the left mouse button.
                3. Drag mouse to the list.
                4. Release the left mouse button.

                The mouse cursor should appear as an I-beam cursor
                and should stay the same while dragging across the
                components. Once you reach the list, release the
                left mouse button. Immediately after, the cursor
                should change to a Hand cursor. If true, this test
                passes.

                The test fails if the cursor updates while dragging
                over the components before releasing the left
                mouse button.
                """;

    public static void main(String[] args) throws Exception {
        try {
            EventQueue.invokeAndWait(() -> {
                instructionsFrame = createInstructionsFrame();
                testFrame = createTestFrame();
            });
            if (!countDownLatch.await(2, TimeUnit.MINUTES)) {
                throw new RuntimeException("Test timeout : No action was"
                        + " taken on the test.");
            }
            System.out.println("Test passed.");
        } finally {
            EventQueue.invokeAndWait(ListDragCursor::disposeFrames);
        }
    }

    static Frame createTestFrame() {
        Frame frame = new Frame("Cursor change after drag");
        Panel panel = new Panel();

        List list = new List(2);
        list.add("List1");
        list.add("List2");
        list.add("List3");
        list.add("List4");
        list.setCursor(new Cursor(Cursor.HAND_CURSOR));

        TextArea textArea = new TextArea(3, 5);
        textArea.setCursor(new Cursor(Cursor.TEXT_CURSOR));

        panel.add(textArea);
        panel.add(list);

        frame.add(panel);
        frame.setSize(300, 150);
        frame.setLocation(450, 500);
        frame.setVisible(true);
        return frame;
    }

    static Frame createInstructionsFrame() {
        Frame frame = new Frame("Test Instructions");
        Panel mainPanel = new Panel(new BorderLayout());
        TextArea textArea = new TextArea(INSTRUCTIONS,
                15, 60, TextArea.SCROLLBARS_NONE);

        Panel btnPanel = new Panel();
        Button passBtn = new Button("PASS");
        Button failBtn = new Button("FAIL");
        btnPanel.add(passBtn);
        btnPanel.add(failBtn);

        passBtn.addActionListener(e -> {
            countDownLatch.countDown();
        });
        failBtn.addActionListener(e -> {
            throw new RuntimeException("Test Failed");
        });

        mainPanel.add(textArea, BorderLayout.CENTER);
        mainPanel.add(btnPanel, BorderLayout.SOUTH);

        frame.add(mainPanel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        return frame;
    }

    static void disposeFrames() {
        if (testFrame != null) {
            testFrame.dispose();
        }
        if (instructionsFrame != null) {
            instructionsFrame.dispose();
        }
    }
}
