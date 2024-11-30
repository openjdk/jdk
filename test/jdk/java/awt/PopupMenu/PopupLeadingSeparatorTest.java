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

import java.awt.Component;
import java.awt.Frame;
import java.awt.Font;
import java.awt.MenuItem;
import java.awt.PopupMenu;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/*
 * @test
 * @bug 4169155
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Popup menus get a leading separator on Motif system
 * @run main/manual PopupLeadingSeparatorTest
 */

public class PopupLeadingSeparatorTest {
    public static void main(String[] args) throws Exception {
        PopupLeadingSeparatorTest obj = new PopupLeadingSeparatorTest();
        String INSTRUCTIONS = """
                Press mouse button on the frame. Popup menu without leading
                separator should appear.
                If a PopupMenu behaves same, press Pass, else press Fail.
                """;

        PassFailJFrame.builder()
                .title("PopupLeadingSeparatorTest Instruction")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(obj::createUI)
                .build()
                .awaitAndCheck();
    }

    private Frame createUI() {
        Frame f = new Frame("PopupLeadingSeparatorTest Test");
        PopupMenu popupMenu = new PopupMenu("Popup Menu Title");
        popupMenu.add(new MenuItem("Item1"));
        PopupMenu cascadeMenu = new PopupMenu("Multifont menu");
        cascadeMenu.add(new MenuItem("Item1"));
        MenuItem item2 = new MenuItem("Item2");
        item2.setFont(new Font("Serif", Font.BOLD, 36));
        cascadeMenu.add(item2);

        popupMenu.add(cascadeMenu);
        f.add(popupMenu);
        f.setSize(300, 150);
        f.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent evt) {
                popupMenu.show((Component) evt.getSource(), evt.getX(), evt.getY());
            }
        });
        return f;
    }
}
