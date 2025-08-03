/*
 * Copyright (c) 2004, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6180413 6184485 6267144
 * @summary test for popup menu visual bugs in XAWT
 * @library /java/awt/regtesthelpers /test/lib
 * @build PassFailJFrame jdk.test.lib.Platform
 * @run main/manual PopupMenuVisuals
*/

import java.awt.Button;
import java.awt.CheckboxMenuItem;
import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.MenuShortcut;
import java.awt.PopupMenu;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

import jdk.test.lib.Platform;

public class PopupMenuVisuals {
    private static final String INSTRUCTIONS = """
         This test should show a button 'Popup'.
         Click on the button. A popup menu should be shown.
         If following conditions are met:
          - Menu is disabled %s%s

         Click Pass else click Fail."""
            .formatted(
                    Platform.isLinux() ? "\n - Menu has caption 'Popup menu'" : "",
                    !Platform.isOSX() ? "\n - Menu items don't show shortcuts" : ""
            );

    static PopupMenu pm;
    static Frame frame;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("PopupMenu Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(PopupMenuVisuals::createTestUI)
                .build()
                .awaitAndCheck();
    }

    static class Listener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            pm.show(frame, 0, 0);
        }
    }

    private static Frame createTestUI() {
        Button b = new Button("Popup");
        pm = new PopupMenu("Popup menu");
        MenuItem mi1 = new MenuItem("Item 1");
        CheckboxMenuItem mi2 = new CheckboxMenuItem("Item 2");
        CheckboxMenuItem mi3 = new CheckboxMenuItem("Item 3");
        Menu sm = new Menu("Submenu");

        // Get things going.  Request focus, set size, et cetera
        frame = new Frame("PopupMenuVisuals");
        frame.setSize(200, 200);
        frame.validate();

        frame.add(b);
        b.addActionListener(new Listener());
        mi1.setShortcut(new MenuShortcut(KeyEvent.VK_A));
        pm.add(mi1);
        pm.add(mi2);
        pm.add(mi3);
        pm.add(sm);
        sm.add(new MenuItem("Item"));
        pm.setEnabled(false);
        mi3.setState(true);
        frame.add(pm);
        return frame;
    }

}
