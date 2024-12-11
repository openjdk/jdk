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
 * @bug 5005194
 * @summary Frame.remove(getMenuBar()) throws NPE if the frame doesn't
 *       have a menu bar
 * @key headful
 * @run main MenuNPE
 */

import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;

public class MenuNPE {
    private static Frame frame;
    public static void main(String[] args) throws Exception {
        try {
            frame = new Frame("Menu NPE");
            MenuBar menuBar = new MenuBar();
            Menu menu1 = new Menu("Menu 01");
            MenuItem menuLabel = new MenuItem("Item 01");
            menu1.add(menuLabel);
            menuBar.add(menu1);
            frame.setMenuBar(menuBar);
            frame.setSize(200, 200);
            frame.setVisible(true);
            frame.validate();
            frame.remove(frame.getMenuBar());
            frame.remove(frame.getMenuBar());
            System.out.println("Test passed.");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (frame != null) {
                frame.dispose();
            }
        }
    }
}
