/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Frame;
import java.awt.EventQueue;
import java.awt.Robot;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/*
 * @test
 * @bug 4103095
 * @summary Test for getBounds() after a Frame resize.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual GetBoundsResizeTest
*/

public class GetBoundsResizeTest {
    private static final String INSTRUCTIONS = """
            0. There is a test window with a "Press" button,
                Its original bounds should be printed in the text area below.
            1. Resize the test window using the upper left corner.
            2. Press the button to print the result of getBounds() to the text area.
            3. Previously, a window could report an incorrect position on the
                screen after resizing the window in this way.
                If getBounds() prints the appropriate values for the window,
                click Pass, otherwise click Fail.
            """;

    private static JTextArea textArea;
    private static Frame frame;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();

        PassFailJFrame passFailJFrame = PassFailJFrame
                .builder()
                .title("GetBoundsResizeTest Instructions")
                .instructions(INSTRUCTIONS)
                .splitUIBottom(() -> {
                    textArea = new JTextArea("", 8, 55);
                    textArea.setEditable(false);
                    return new JScrollPane(textArea);
                })
                .testUI(GetBoundsResizeTest::getFrame)
                .rows((int) (INSTRUCTIONS.lines().count() + 2))
                .columns(40)
                .build();

        robot.waitForIdle();
        robot.delay(500);

        EventQueue.invokeAndWait(() ->
                logFrameBounds("Original Frame.getBounds() = %s\n"));

        passFailJFrame.awaitAndCheck();
    }

    private static Frame getFrame() {
        frame = new Frame("GetBoundsResizeTest");

        Button button = new Button("Press");
        button.addActionListener((e) ->
                logFrameBounds("Current Frame.getBounds() = %s\n"));

        frame.add(button);
        frame.setSize(200, 100);

        return frame;
    }

    private static void logFrameBounds(String format) {
        textArea.append(format.formatted(frame.getBounds()));
    }
}
