/*
 * Copyright (c) 2004, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5097531
 * @summary Make sure the cursor updates correctly under certain
 *          circumstances even when the EDT is busy
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual CursorUpdateTest
 */

import java.awt.Button;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;

public class CursorUpdateTest {
    final static String progress = "|/-\\";

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                Check the following:
                1. Cursor must be crosshair when hovering the mouse over the
                    blue square.
                2. Crosshair cursor must not flicker.
                3. The cursor must be "I-beam" when hovering the mouse over the
                    button.
                4. Click the button - it will display "Busy" message and a
                    rotating bar for 5 seconds. The cursor must change to
                    hourglass.
                5. (Windows only) While the cursor is on the button, press Alt.
                    The cursor must change to normal shape. Pressing Alt again
                    must revert it back to I-beam.
                6. Move the mouse out of the button and back onto it. The cursor
                    must update correctly (hourglass over the button, normal
                    over the frame) even when the button displays "busy".
                    Do not try to check (1) or (5) when the button displays
                    "Busy" - this is not required.
                Pass if all the steps are as behave as described. Otherwise,
                fail this test.
                """;

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(CursorUpdateTest::createUI)
                .build()
                .awaitAndCheck();
    }

    public static Frame createUI() {
        Frame f = new Frame();
        f.setLayout(new FlowLayout());
        Button b = new Button("Button");
        f.add(b);
        b.setCursor(new Cursor(Cursor.TEXT_CURSOR));
        Component c = new MyComponent();
        f.add(c);
        c.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
        b.addActionListener(e -> {
            String oldLabel = b.getLabel();
            Cursor oldCursor = b.getCursor();
            b.setCursor(new Cursor(Cursor.WAIT_CURSOR));
            try {
                for (int i = 0; i < 50; i++) {
                    b.setLabel("Busy " + progress.charAt(i % 4));
                    Thread.sleep(100);
                }
            } catch (InterruptedException ex) {
            }
            b.setCursor(oldCursor);
            b.setLabel(oldLabel);
        });
        return f;
    }
}

class MyComponent extends Component {
    public void paint(Graphics g) {
        g.setColor(getBackground());
        g.fillRect(0, 0, getSize().width, getSize().height);
    }

    public MyComponent() {
        setBackground(Color.blue);
    }

    public Dimension getPreferredSize() {
        return new Dimension(100, 100);
    }
}
