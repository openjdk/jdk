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

import java.awt.Button;
import java.awt.Component;
import java.awt.Frame;
import java.awt.MenuItem;
import java.awt.PopupMenu;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/*
 * @test
 * @bug 4181790
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Tests a popup menu on a button.
 * @run main/manual PopupOnButton
 */

public class PopupOnButton {
   public static void main(String[] args) throws Exception {
       PopupOnButton obj = new PopupOnButton();
       String INSTRUCTIONS = """
                Right-click on the button.
                Popup Menu should appear and behave fine.
                If a PopupMenu appears, press Pass, else press Fail.
                """;

       PassFailJFrame.builder()
               .title("PopupOnButton Instruction")
               .instructions(INSTRUCTIONS)
               .columns(40)
               .testUI(obj::createUI)
               .build()
               .awaitAndCheck();
   }

    private Frame createUI() {
        Frame f = new Frame("PopupOnButton Test");
        Button b = new Button("button with popup menu");
        PopupMenu m = new PopupMenu("popup");
        m.add(new MenuItem("item1"));
        m.add(new MenuItem("item2"));
        m.add(new MenuItem("item3"));
        b.add(m);
        b.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    m.show((Component) e.getSource(), e.getX(), e.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    m.show((Component) e.getSource(), e.getX(), e.getY());
                }
            }
        });

        f.add(b);
        f.setSize(200, 150);
        return f;
    }
 }
