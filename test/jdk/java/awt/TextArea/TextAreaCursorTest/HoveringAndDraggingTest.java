/*
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Button;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Panel;
import java.awt.TextArea;
import java.util.concurrent.CountDownLatch;

/*
 * @test
 * @bug 6497109
 * @summary Mouse cursor icons for TextArea should be correct in case of
 *  hovering or dragging mouse over different subcomponents.
 * @run main/manual HoveringAndDraggingTest
 */

public class HoveringAndDraggingTest {
    static Frame frame;
    static Frame instructionsFrame;
    static CountDownLatch countDownLatch;
    public static CountDownLatch createCountDownLatch() {
        return new CountDownLatch(1);
    }

    public static void main(String[] args) throws Exception {
        countDownLatch = createCountDownLatch();
        EventQueue.invokeAndWait(() -> {
            initialize();
            showInstructionFrame();
        });
        countDownLatch.await();
        System.out.println("Test Pass");
    }

    public static void initialize() {
        Panel panel = new Panel();
        panel.setLayout(new GridLayout(3, 3));

        for (int y = 0; y < 3; ++y) {
            for (int x = 0; x < 3; ++x) {
                if (x == 1 && y == 1) {
                    panel.add(new TextArea(bigString()));
                } else {
                    panel.add(new Panel());
                }
            }
        }

        frame = new Frame("TextArea cursor icon test");
        frame.setSize(300, 300);
        frame.setLocation(450, 400);
        frame.add(panel);
        frame.setVisible(true);
    }

    static void showInstructionFrame() {
        String INSTRUCTIONS = """
                1. Notice components in test window: main-panel,box-for-text,
                   2 scroll-sliders, and 4 scroll-buttons.
                2. Hover mouse over box-for-text.
                   Make sure, that mouse cursor is TextCursor(a.k.a. \"beam\").
                3. Hover mouse over each of components (see item 1),
                   except for box-for-text.
                   Make sure, that cursor is DefaultCursor (arrow).
                4. Drag mouse (using any mouse button) from box-for-text to every"
                   component in item 1, and also outside application window."
                   Make sure, that cursor remains TextCursor
                   while mouse button is pressed.
                5. Repeat item 4 for each other component in item 1,
                   except for box-for-text
                   _but_ now make sure that cursor is DefaultCursor.
                6. If cursor behaves as described in items 2-3-4-5,
                   then test is PASS otherwise it FAILED.
                 """;
        TextArea textArea = new TextArea(INSTRUCTIONS, 16, 65, TextArea.SCROLLBARS_NONE);
        Button passBtn = new Button("PASS");
        Button failBtn = new Button("FAIL");
        Panel btnPanel = new Panel(new GridBagLayout());
        Panel panel = new Panel(new GridBagLayout());
        instructionsFrame = new Frame("Test Instructions");
        passBtn.setMaximumSize(new Dimension(100, 30));
        failBtn.setMaximumSize(new Dimension(100, 30));
        btnPanel.add(passBtn);
        btnPanel.add(failBtn);
        passBtn.addActionListener(e -> disposeFrames());
        failBtn.addActionListener(e -> {
            disposeFrames();
            throw new RuntimeException("Test Failed");
        });
        panel.add(textArea);
        panel.add(btnPanel);
        instructionsFrame.add(panel);
        instructionsFrame.pack();
        instructionsFrame.setLocation(300, 100);
        instructionsFrame.setVisible(true);
    }

    static void disposeFrames() {
        countDownLatch.countDown();
        if (frame != null) {
            frame.dispose();
        }
        if (instructionsFrame != null) {
            instructionsFrame.dispose();
        }
    }

    static String bigString() {
        String s = "";
        for (int lines = 0; ; ++lines) {
            for (int symbols = 0; symbols < 100; ++symbols) {
                s += "0";
            }
            if (lines < 50) {
                s += "\n";
            } else {
                break;
            }
        }
        return s;
    }
}
