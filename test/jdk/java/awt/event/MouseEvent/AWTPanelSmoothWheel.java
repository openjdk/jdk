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
import javax.swing.JOptionPane;

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
    private static final String WARNING_MSG = "WARNING !!!"
            + " You might NOT be using a hi-res mouse.";
    private static final String INSTRUCTIONS = """
            <html>
            <body>
            This test is relevant on platforms with high-resolution mouse wheel
            or a trackpad can be used too.
            Please press PASS if this is not the case.<br> <br>

            Place the mouse cursor above the green panel and rotate the mouse wheel,
            the test will print mouse wheel event messages in the format
            <b> [Event#, WheelRotation, PreciseWheelRotation]</b> into the logging
            panel below the instruction window.<br> <br>

            A hi-res mouse/trackpad is one which produces MouseWheelEvents having:
            <pre><b> Math.abs(preciseWheelRotation) &lt; 1. </b></pre><br>

            Check if the test works OK when the mouse-wheel/trackpad is scrolled
            very slowly.<br> <br>
            This is a semi-automated test, if you are using a hi-res mouse/trackpad
            and it satisfies the hi-res MouseWheelEvents as described below,
            the test should automatically pass.<br> <br>

            When preciseWheelRotation adds up, wheelRotation becomes non-zero
            (can be negative when mouse wheel is scrolled down). <br>
            You should see many events where the absolute value of
            preciseWheelRotation &lt; 1 &amp; wheelRotation = 0 followed by
            an event where wheelRotation != 0 in the logs.<br> <br>

            <hr>
            <b> NOTE: </b>
            <ul>
                <li> If you don't see events with preciseWheelRotation &lt; 1,
                then the mouse doesn't support high-resolution scrolling. </li>
                <li> A warning is shown if you are not using a hi-res mouse. </li>
                <li> MouseWheelEvent logs are displayed in the log area
                for user reference. </li>
                <li> When mouse is scrolled up, preciseWheelRotation & wheelRotation
                 are positive and they are negative when scrolled down. </li>
            </ul>
            </body>
            </html>
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows(30)
                .columns(54)
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
                PassFailJFrame.log("WheelEvent#" + (++wheelEventCount)
                        + " --- Wheel Rotation: " + e.getWheelRotation()
                        + " --- Precise Wheel Rotation: "
                        + String.format("%.2f", e.getPreciseWheelRotation()));
                if (Math.abs(e.getPreciseWheelRotation()) < 1) {
                    hiResWheelCount++;
                }
                if (wheelEventCount >= 5 && hiResWheelCount == 0) {
                    PassFailJFrame.log(WARNING_MSG);
                    JOptionPane.showMessageDialog(frame, WARNING_MSG,
                            "Warning", JOptionPane.WARNING_MESSAGE);
                }
                if (e.getWheelRotation() != 0 && hiResWheelCount > 0) {
                    PassFailJFrame.log("The test passes: hiResWheelCount = "
                            + hiResWheelCount);
                    PassFailJFrame.forcePass();
                }
            }
        });
        frame.setSize(400, 200);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.CENTER);
        return frame;
    }
}
