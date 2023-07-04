/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4426750
  @requires (os.family == "linux")
  @key headful
  @summary tests that middle mouse button click pastes primary selection
*/

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.TextArea;
import java.awt.TextComponent;
import java.awt.TextField;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.Toolkit;

public class MiddleMouseClickPasteTest {
    static final int FRAME_ACTIVATION_TIMEOUT  = 1000;
    static final int SELECTION_PASTE_TIMEOUT   = 1000;
    static final int CLICK_INTERVAL            = 50;
    static final String TEST_TEXT              = "TEST TEXT";
    static Frame frame;
    static TextField tf;
    static TextArea ta;
    static final Clipboard systemSelection = Toolkit.getDefaultToolkit().getSystemSelection();


    public static void main(String[] args) throws Exception {
        if (systemSelection != null) {
            try {
                EventQueue.invokeAndWait(MiddleMouseClickPasteTest::createAndShowGui);
                Thread.sleep(FRAME_ACTIVATION_TIMEOUT);

                checkPaste(tf);
                checkPaste(ta);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                EventQueue.invokeAndWait(()-> {
                    if (frame != null) {
                        frame.dispose();
                    }
                });
            }
        }
    }

    public static void createAndShowGui() {
        frame = new Frame();
        tf = new TextField();
        ta = new TextArea();

        frame.setLayout(new BorderLayout());
        frame.add(tf, BorderLayout.NORTH);
        frame.add(ta, BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void checkPaste(TextComponent textComponent) throws Exception {

        final Point sourcePoint = textComponent.getLocationOnScreen();
        final Dimension d = textComponent.getSize();
        sourcePoint.translate(d.width / 2, d.height / 2);
        final Robot robot = new Robot();
        robot.setAutoWaitForIdle(true);
        robot.setAutoDelay(CLICK_INTERVAL);

        textComponent.setText("");
        systemSelection.setContents(new StringSelection(TEST_TEXT), null);

        robot.mouseMove(sourcePoint.x, sourcePoint.y);
        robot.mousePress(InputEvent.BUTTON2_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON2_DOWN_MASK);
        robot.delay(SELECTION_PASTE_TIMEOUT);

        if (!TEST_TEXT.equals(textComponent.getText())) {
            throw new RuntimeException("Primary selection not pasted" +
                    " into: " + textComponent);
        }
    }
}
