/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @bug 4257944
  @summary PopupMenu.setEnabled fails on Win32
  @key headful
  @run main EnableTest
*/

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.MenuItem;
import java.awt.PopupMenu;

public class EnableTest {
    PopupMenu popup = null;
    Frame frame;

    public static void main(String[] args) throws Exception {
        EnableTest test = new EnableTest();
        test.start();
    }

    public void start() throws Exception {
        try {
            EventQueue.invokeAndWait(() -> {
                frame = new Frame("EnableTest");
                popup = new PopupMenu("Popup Menu Title");
                MenuItem mi1 = new MenuItem("Menu Item");
                MenuItem mi2 = new MenuItem("Menu Item");
                popup.add(mi1);
                popup.addSeparator();
                popup.add(mi2);
                popup.setEnabled(false);
                popup.setLabel("New Label");
                mi2.setEnabled(false);
                frame.add(popup);
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
