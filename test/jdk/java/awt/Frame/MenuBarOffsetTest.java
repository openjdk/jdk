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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Menu;
import java.awt.MenuBar;

/*
 * @test
 * @bug 4180577
 * @summary offset problems with menus in frames: (2 * 1)  should be (2 * menuBarBorderSize)
 * @requires (os.family == "linux" | os.family == "windows")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MenuBarOffsetTest
*/

public class MenuBarOffsetTest {
    private static final String INSTRUCTIONS = """
            If a menubar containing a menubar item labeled Test appears.
            in a frame, and fits within the frame, press Pass, else press Fail.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("MenuBarOffsetTest Instructions")
                .instructions(INSTRUCTIONS)
                .columns(45)
                .testUI(FrameTest::new)
                .build()
                .awaitAndCheck();
    }

    private static class FrameTest extends Frame {
        public FrameTest() {
            super("MenuBarOffsetTest FrameTest");
            MenuBar m = new MenuBar();
            setMenuBar(m);
            Menu test = m.add(new Menu("Test"));
            test.add("1");
            test.add("2");
            setSize(100, 100);
        }

        public void paint(Graphics g) {
            setForeground(Color.red);
            Insets i = getInsets();
            Dimension d = getSize();
            System.err.println(getBounds());
            System.err.println("" + i);

            g.drawRect(i.left, i.top,
                    d.width - i.left - i.right - 1,
                    d.height - i.top - i.bottom - 1);
        }
    }
}
