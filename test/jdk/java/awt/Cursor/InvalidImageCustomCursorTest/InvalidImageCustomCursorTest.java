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
 * @bug 4212593
 * @summary The Toolkit.createCustomCursor does not check absence of the
 *          image of cursor
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual InvalidImageCustomCursorTest
 */

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Frame;
import java.awt.Image;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Toolkit;

public class InvalidImageCustomCursorTest {
    static Cursor cursor;

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                Press 'Hide' button to hide (set transparent) cursor for the
                green panel. Move the pointer over the green panel - pointer
                should disappear. Press 'Default' button to set default cursor
                for the green panel.

                If you see any exceptions or cursor is not transparent,
                test failed, otherwise it passed.
                """;

        Toolkit tk = Toolkit.getDefaultToolkit();
        Image image = tk.getImage("NON_EXISTING_FILE.gif");
        Point p = new Point(0, 0);

        cursor = tk.createCustomCursor(image, p, "Test");

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(InvalidImageCustomCursorTest::createUI)
                .logArea(5)
                .build()
                .awaitAndCheck();
    }

    public static Frame createUI() {
        Frame f = new Frame("Invalid Cursor Image Test");
        f.setLayout(new BorderLayout());
        f.setSize(200, 200);

        Button def = new Button("Default");
        Button hide = new Button("Hide");
        Panel panel = new Panel();

        def.addActionListener(e -> panel.setCursor(Cursor.getDefaultCursor()));
        hide.addActionListener(e -> panel.setCursor(cursor));

        panel.setBackground(Color.green);
        panel.setSize(100, 100);
        f.add("Center", panel);
        f.add("North", hide);
        f.add("South", def);

        return f;
    }
}
