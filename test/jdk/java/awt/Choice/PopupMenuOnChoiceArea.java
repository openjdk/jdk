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

/*
 * @test
 * @bug 6240046
 * @summary REG:Choice's Drop-down does not disappear when clicking somewhere, after popup menu is disposed-XTkt
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual PopupMenuOnChoiceArea
 */


import java.awt.CheckboxMenuItem;
import java.awt.Choice;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Menu;
import java.awt.PopupMenu;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;

public class PopupMenuOnChoiceArea extends Frame {
    static final String INSTRUCTIONS = """
            You would see a window named 'Popup menu on choice area'
            with Choice in it. Move the mouse pointer to the choice.
            Click right mouse button on it.
            You should see a popup menu with 'File' in it.
            Close this popup menu by pressing Esc.
            Click the left mouse button on the Choice.
            You should see a Choice drop-down menu.
            Move mouse pointer into drop-down menu.
            Click right mouse button on any item in it.
            If you see a 'File' popup menu press Fail.
            If Choice drop-down closes instead press Pass.
            """;

    public PopupMenuOnChoiceArea() {
        super("Popup menu on choice area");
        this.setLayout(new FlowLayout());
        Choice choice = new Choice();
        choice.add("item-1");
        choice.add("item-2");
        choice.add("item-3");
        choice.add("item-4");
        add("Center", choice);
        Menu fileMenu = new Menu("File");
        Menu open = new Menu("Open");
        Menu save = new Menu("save");
        CheckboxMenuItem exit = new CheckboxMenuItem("Exit");
        fileMenu.add(open);
        fileMenu.add(save);
        fileMenu.add(exit);
        final PopupMenu pop = new PopupMenu();
        pop.setLabel("This is a popup menu");
        pop.setName("a menu");
        pop.add(fileMenu);
        choice.add(pop);
        choice.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent me) {
                if (me.isPopupTrigger()) {
                    pop.show(me.getComponent(), me.getX(), me.getY());
                }
            }

            public void mouseReleased(MouseEvent me) {
                if (me.isPopupTrigger()) {
                    pop.show(me.getComponent(), me.getX(), me.getY());
                }
            }
        });
        setSize(200, 200);
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        PassFailJFrame.builder()
                .title("Test Instructions")
                .testUI(PopupMenuOnChoiceArea::new)
                .instructions(INSTRUCTIONS)
                .columns(40)
                .build()
                .awaitAndCheck();
    }
}
