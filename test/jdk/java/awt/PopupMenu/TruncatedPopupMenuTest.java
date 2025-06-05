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

import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/*
 * @test
 * @bug 5090643
 * @key headful
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Menus added to the popup menu are truncated on XToolkit
 * @run main/manual TruncatedPopupMenuTest
 */

public class TruncatedPopupMenuTest {
    private static final String INSTRUCTIONS =
            "1. Right-click on the Test Window.\n\n" +
            "2. Look at the appeared popup menu.\n\n" +
            "3. It should consist of one menu item (\"First simple menu item\")\n" +
            "and one submenu (\"Just simple menu for the test\").\n\n" +
            "4. The submenu should not be truncated. The submenu title text should\n" +
            "be followed by a triangle. On the whole, menu should be good-looking.\n\n" +
            "5. Left-click on the submenu (\"Just simple menu for the test\").\n" +
            "After this operation, a submenu should popup. It should consist of\n"+
            "one menu item (\"Second simple menu item \") and one submenu (\"Other Menu\").\n\n" +
            "6. The submenu should not be truncated. The \"Other Menu\" text should be followed by\n" +
            "a triangle.\n\n" +
            "On the whole, menu should be good-looking.\n";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                      .instructions(INSTRUCTIONS)
                      .rows(20)
                      .columns(55)
                      .testUI(TruncatedPopupMenuTest::createTestUI)
                      .build()
                      .awaitAndCheck();
    }

    private static Frame createTestUI() {
        Menu subMenu = new Menu("Just simple menu for the test");
        subMenu.add(new MenuItem("Second simple menu item"));
        subMenu.add(new Menu("Other Menu"));

        PopupMenu popup = new PopupMenu();
        popup.add(new MenuItem("First simple menu item"));
        popup.add(subMenu);

        Frame testUI = new Frame("TruncatedPopupMenuTest");
        testUI.add(popup);
        testUI.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent me) {
                if (me.isPopupTrigger()) {
                    popup.show(me.getComponent(), me.getX(), me.getY());
                }
            }
            public void mouseReleased(MouseEvent me) {
                if (me.isPopupTrigger()) {
                    popup.show(me.getComponent(), me.getX(), me.getY());
                }
            }
        });

        testUI.setSize(400, 400);
        return testUI;
   }
}
