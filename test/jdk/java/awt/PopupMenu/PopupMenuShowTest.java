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

import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Label;
import java.awt.MenuItem;
import java.awt.PopupMenu;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/*
 * @test
 * @bug 4168006 4196790
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Popup menu test fails on x86/Solaris 2.6 combination.
 * @run main/manual PopupMenuShowTest
 */

public class PopupMenuShowTest {
    public static void main(String[] args) throws Exception {
        PopupMenuShowTest obj = new PopupMenuShowTest();
        String INSTRUCTIONS = """
                Press the right mouse button in the PopupTest window.
                If a PopupMenu appears, press Pass, else press Fail.
                """;

        PassFailJFrame.builder()
                .title("PopupMenuShowTest Instruction")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(obj::createUI)
                .build()
                .awaitAndCheck();
    }

    private Frame createUI() {
        Frame f = new Frame("PopupMenuShowTest Test");
        f.setLayout(new FlowLayout());
        f.add(new Label("Press right mouse button inside this frame."));
        f.add(new Label("A pop-up menu should appear."));
        PopupMenu popupMenu = new PopupMenu("Popup Menu Title");
        MenuItem mi = new MenuItem("Menu Item");
        popupMenu.add(mi);
        f.add(popupMenu);
        f.setSize(400, 350);
        f.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                popupMenu.show(e.getComponent(), e.getX(), e.getY());
            }
        });
        return f;
    }
}
