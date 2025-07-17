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

/*
 * @test
 * @bug 4147246
 * @summary Simple check for peer != null in Component.componentMoved
 * @key headful
 * @run main LWParentMovedTest
 */

import java.awt.Button;
import java.awt.Color;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;

public class LWParentMovedTest {
    static CMTFrame f;

    // test will throw an exception and fail if lwc is null
    public static void main(String[] args) throws Exception {
        try {
            EventQueue.invokeAndWait(() -> f = new CMTFrame());
        } finally {
            if (f != null) {
                EventQueue.invokeAndWait(() -> f.dispose());
            }
        }
    }
}

class CMTFrame extends Frame {
    Container lwc;
    Button button;

    public CMTFrame() {
        super("Moving LWC Test");
        setLayout(new FlowLayout());
        lwc = new LWSquare(Color.blue, 100, 100);
        button = new Button();
        lwc.add(button);
        add(lwc);

        setSize(400, 300);
        setVisible(true);

        // queue up a bunch of COMPONENT_MOVED events
        for (int i = 0; i < 1000; i++) {
            lwc.setLocation(i, i);
        }

        // remove heavyweight from lightweight container
        lwc.remove(button);
    }
}

//
// Lightweight container
//
class LWSquare extends Container {
    int width;
    int height;

    public LWSquare(Color color, int w, int h) {
        setBackground(color);
        setLayout(new FlowLayout());
        width = w;
        height = h;
        setName("LWSquare-" + color.toString());
    }

    public void paint(Graphics g) {
        g.setColor(getBackground());
        g.fillRect(0, 0, 1000, 1000);
    }
}
