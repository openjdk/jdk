/*
 * Copyright (c) 2009, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Color;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.event.MouseWheelEvent;

/*
 * @test
 * @bug 6730447
 * @summary To verify the support for high resolution mouse wheel.
 *          AWT panel needs to support high-res mouse wheel rotation.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual AWTPanelSmoothWheel
 */

public class AWTPanelSmoothWheel {
    private static int wheelEventCount = 0;
    private static final String INSTRUCTIONS = """
            <html>
            <body>
            This test is relevant on platforms with high-resolution mouse wheel,
            please press PASS if this is not the case.<br> <br>

            Place the mouse cursor above the green panel and rotate the mouse wheel,
            the test will print all mouse wheel event messages into the logging panel
            below the instruction window.<br> <br>

            Check if the test works OK when the mouse wheel is rotated very slow.<br> <br>

            This is a semi-automated test, when 5 or more MouseWheelEvents with
            <br><b> scrollType=WHEEL_UNIT_SCROLL and wheelRotation != 0 </b> <br>
            are recorded, the test automatically passes.<br>
            The events are also logged in the logging panel
            for user reference.<br> <br>

            <hr>
            PS: If you don't see events with scrollType=WHEEL_UNIT_SCROLL,
            then the mouse doesn't support high-resolution scrolling.<br> <br>
            </body>
            </html>
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows(18)
                .columns(50)
                .logArea(10)
                .testUI(AWTPanelSmoothWheel::createUI)
                .build()
                .awaitAndCheck();
    }

    private static Frame createUI() {
        Frame frame = new Frame("Test Wheel Rotation");
        Panel panel = new Panel();
        panel.setBackground(Color.GREEN);
        panel.addMouseWheelListener(e -> {
            PassFailJFrame.log(e.toString());
            if (e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL
                    && e.getWheelRotation() != 0) {
                wheelEventCount++;
            }
            if (wheelEventCount > 5) {
                PassFailJFrame.forcePass();
            }
        });
        frame.setSize (400, 200);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.CENTER);
        return frame;
    }
}
