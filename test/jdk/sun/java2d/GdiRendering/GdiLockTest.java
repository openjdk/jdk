/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4693644
 * @summary verifies that there are no artifacts due to copying with GDI
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual GdiLockTest
*/

import java.awt.Color;
import java.awt.Component;
import java.awt.Frame;
import java.awt.Graphics;

public class GdiLockTest {

        static final String INSTRUCTIONS = """
            A window will open up next to these instructions.
            The text you see in that window should blink on and off.
            If it never disappears, then the test has failed.
        """;

    public static void main(String[] argv) throws Exception {
        PassFailJFrame.builder()
                .title("GdiLockTest")
                .instructions(INSTRUCTIONS)
                .testTimeOut(5)
                .rows(5)
                .columns(45)
                .testUI(GdiLockTest::createUI)
                .build()
                .awaitAndCheck();
    }

    private static Frame createUI() {
        Frame f = new Frame("GdiLockTest");
        f.setSize(300, 300);
        GdiLockTestComponent test = new GdiLockTestComponent();
        Thread t = new Thread(test);
        f.add(test);
        t.start();
        return f;
    }
}

class GdiLockTestComponent extends Component implements Runnable {

    boolean textVisible = true;

    public void paint(Graphics g) {
        g.setColor(Color.white);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(Color.black);
        if (!textVisible) {
            g.setClip(200, 200, 300, 300);
        }
        g.drawString("This text should be blinking", 10, 30);
        if (!textVisible) {
            g.setClip(0, 0, getWidth(), getHeight());
        }
    }

    public void run() {
        while (true) {
            repaint();
            textVisible = !textVisible;
            try {
                Thread.sleep(500);
            } catch (Exception e) {}
        }
    }
}
