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
    private static int hiResWheelCount = 0;
    private static final String INSTRUCTIONS = """
            <html>
            <body>
            This test is relevant on platforms with high-resolution mouse wheel,
            please press PASS if this is not the case.<br> <br>

            Place the mouse cursor above the green panel and rotate the mouse wheel,
            the test will print mouse wheel event messages in the format
            <b> [Event#, WheelRotation, PreciseWheelRotation]</b> into the logging
            panel below the instruction window.<br> <br>

            A hi-res mouse is one which produces MouseWheelEvents having
            <b>preciseWheelRotation &lt 1.</b> <br>
            When preciseWheelRotation adds up to 1,wheelRotation becomes 1. <br>
            You should see a few events where preciseWheelRotation < 1 & wheelRotation = 0
            followed by a event where preciseWheelRotation = 1 & wheelRotation = 1.<br> <br>

            Check if the test works OK when the mouse wheel is rotated very slow.<br> <br>
            Please press PASS if above is true, else FAIL. <br> <br>

            <hr>
            PS: If you don't see events with preciseWheelRotation < 1,
            then the mouse doesn't support high-resolution scrolling.
            A warning is shown in the logging area if you are not using a hi-res mouse.
            <br> <br>
            </body>
            </html>
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows(20)
                .columns(50)
                .testTimeOut(10)
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
            if (e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL) {
                PassFailJFrame.log("WheelEvent#"+ ++wheelEventCount
                        + " --- Wheel Rotation: " + e.getWheelRotation()
                        + " --- Precise Wheel Rotation: "
                        + String.format("%.2f", e.getPreciseWheelRotation()));
                if (e.getPreciseWheelRotation() < 1) {
                    hiResWheelCount++;
                }
                if (wheelEventCount >= 5 && hiResWheelCount == 0) {
                    PassFailJFrame.log("WARNING !!! You might not be using a high-res mouse.");
                }
            }
        });
        frame.setSize (400, 200);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.CENTER);
        return frame;
    }
}
