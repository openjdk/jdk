/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4492456
 * @summary MouseWheelEvent coordinates are wrong
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual WheelEventCoord
 */

import java.awt.Button;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridLayout;
import java.lang.reflect.InvocationTargetException;

public class WheelEventCoord extends Frame {
    static String INSTRUCTIONS = """
            This test requires mouse with scrolling wheel or device,
            that has capability to simulate scrolling wheel with gestures
            such as Apple mouse or a trackpad with gesture control.
            If you do not have such device press "Pass".
            Move mouse to the top of the button named "Button 1".
            While constantly turning mouse wheel up and down slowly move
            mouse cursor until it reaches bottom of the button named "Button 3".
            While doing so look at the log area.
            If despite the wheel direction y coordinate is steadily increases
            as you move the mouse down press "Pass".
            If y coordinate decreases when cursor is moving down or suddenly jumps
            by more than 50 points when crossing to another button press "Fail".
            """;

    public WheelEventCoord() {
        super("Wheel Event Coordinates");
        setLayout(new GridLayout(3, 1));

        add(new BigButton("Button 1"));
        add(new BigButton("Button 2"));
        add(new BigButton("Button 3"));

        addMouseWheelListener(e -> PassFailJFrame.log("Mouse y coordinate = " + e.getY()));
        pack();
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        PassFailJFrame.builder()
                .title("Wheel Event Coordinates Instructions")
                .instructions(INSTRUCTIONS)
                .logArea(10)
                .testUI(WheelEventCoord::new)
                .build()
                .awaitAndCheck();
    }
}

class BigButton extends Button {
    public BigButton(String label) {
        super(label);
    }

    public Dimension getPreferredSize() {
        return new Dimension(300, 100);
    }

    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    public Dimension getMaximumSize() {
        return getPreferredSize();
    }
}
