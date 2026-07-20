/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Button;
import java.awt.Frame;
import java.awt.MenuItem;
import java.awt.PopupMenu;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/*
 * @test
 * @bug 6267162
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Popup Menu gets hidden below the screen when opened near the periphery
 *          of the screen, XToolkit Test if popup menu window is adjusted on screen
 *          when trying to show outside
 * @run main/manual PeripheryOfScreen
 */

public class PeripheryOfScreen {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                Click on the button to show popup menu in the center of
                frame. Move frame beyond the edge of screen and click on
                button to show the popup menu and see if popup menu is
                adjusted to the edge.

                Press Pass if popup menu behaves as per instruction, otherwise
                press Fail.
                """;

        PassFailJFrame.builder()
                .title("PeripheryOfScreen Instruction")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(PeripheryOfScreen::createUI)
                .build()
                .awaitAndCheck();
    }

    private static Frame createUI () {
        Frame f = new Frame("PeripheryOfScreen Test frame");
        Button b = new Button("Click to show popup menu");
        PopupMenu pm = new PopupMenu("Test menu");
        MenuItem i = new MenuItem("Click me");
        pm.add(i);
        b.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pm.show(f, 100, 100);
            }
        });
        f.add(b);
        f.add(pm);
        f.setSize(300, 200);
        f.toFront();
        return f;
    }
}
