/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Frame;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/*
 * @test
 * @bug 4074498
 * @summary Test: MOUSE_PRESSED events in the title bar of a frame
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TitleBarGetsMousePressed
 */
public class TitleBarGetsMousePressed {
    private static final String INSTRUCTIONS = """
    1. You will see a Frame window next to this window with instructions
    2. Clicking in the title bar of the Frame and even moving around the Frame
       should not generate MOUSE_PRESSED / MOUSE_RELEASED / MOUSE_CLICKED events.
       (printed below in the log area).
    """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("TitleBarGetsMousePressed Instructions")
                .instructions(INSTRUCTIONS)
                .columns(45)
                .testUI(TitleBarGetsMousePressed::createTestUI)
                .logArea(5)
                .build()
                .awaitAndCheck();
    }

    private static Window createTestUI() {
        Frame frame = new Frame("TitleBarGetsMousePressed");
        frame.setSize(300, 200);
        frame.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent ev) {
                PassFailJFrame.log("mouseClicked at x:" + ev.getX() +
                        " y:" + ev.getY());
            }

            public void mousePressed(MouseEvent ev) {
                PassFailJFrame.log("mousePressed at x:" + ev.getX() +
                        " y:" + ev.getY());
            }

            public void mouseReleased(MouseEvent ev) {
                PassFailJFrame.log("mouseReleased at x:" + ev.getX() +
                        " y:" + ev.getY());
            }
        });

        return frame;
    }
}
