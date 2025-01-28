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
 * @bug 4167811
 * @summary tests that shortcuts work for Checkbox menu items
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual CheckMenuShortcut
*/

import java.awt.CheckboxMenuItem;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.MenuShortcut;
import java.awt.Panel;
import java.awt.Rectangle;
import java.awt.TextArea;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;

public class CheckMenuShortcut implements ActionListener, ItemListener {

     static final String INSTRUCTIONS = """
         A window that contains a text area will be displayed.
         The window will have a menu labeled 'Window Menu'.  Click on the menu to see its items.

         The two menu items should have shortcuts which in order are : Ctrl-A, Ctrl-I.
         On macOS these will be Command-A, Command-I.

         If the second item only has the label 'checkbox item' and no shortcut
         ie none of Ctrl-I or Ctrl-i, or Command-I or Command-i on macOS painted on it, the test FAILS.

         The same second item - labeled 'checkbox item' is in fact a Checkbox menu item.
         The menu item should NOT be checked (eg no tick mark).

         Dismiss the menu by clicking inside the window, do not select any of menu items.
         After that press Ctrl-i, (Command-i on macOS).

         After that click on the menu again. If the second menu item 'checkbox item' is now
         checked, the test PASSES, if it is not checked, the test FAILS.
       """;

    public static void main(String[] args) throws Exception {
       PassFailJFrame.builder()
                .title("CheckboxMenuItem Shortcut Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(60)
                .logArea()
                .testUI(CheckMenuShortcut::createUI)
                .build()
                .awaitAndCheck();
    }


    static Frame createUI() {

        MenuBar mainMenu;
        Menu menu;
        MenuItem action;
        CheckboxMenuItem item;
        TextArea pane;

        boolean isMac = System.getProperty("os.name").startsWith("Mac");
        String ctrlA = (isMac) ? "Command-A" : "Ctrl-A";
        String ctrlI = (isMac) ? "Command-I" : "Ctrl-I";

        CheckMenuShortcut cms = new CheckMenuShortcut();
        Frame frame = new Frame("CheckMenuShortcut");

        mainMenu = new MenuBar();
        menu = new Menu("Window Menu");

        action = new MenuItem("action");
        action.setShortcut(new MenuShortcut(KeyEvent.VK_A, false));
        action.addActionListener(cms);
        action.setActionCommand("action");
        menu.add(action);

        item = new CheckboxMenuItem("checkbox item", false);
        item.setShortcut(new MenuShortcut(KeyEvent.VK_I,false));
        item.addItemListener(cms);
        item.addActionListener(cms);
        menu.add(item);

        mainMenu.add(menu);

        frame.setMenuBar(mainMenu);

        pane = new TextArea(ctrlA + " -- action menu test\n", 10, 40, TextArea.SCROLLBARS_VERTICAL_ONLY);
        Dimension mySize = frame.getSize();
        Insets myIns = frame.getInsets();
        pane.setBounds(new Rectangle(mySize.width - myIns.left - myIns.right,
                                     mySize.height - myIns.top - myIns.bottom));
        pane.setLocation(myIns.left,myIns.top);
        frame.add(pane);

        pane.append(ctrlI + " -- item menu test\n");

        frame.pack();
        return frame;
    }

    public void itemStateChanged(ItemEvent evt) {
        PassFailJFrame.log("Got item: " + evt.getItem() + "\n");
    }

    public void actionPerformed(ActionEvent evt) {
        PassFailJFrame.log("Got action: " + evt.getActionCommand() + "\n");
    }
}
