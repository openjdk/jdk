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
  @bug 4234266
  @summary MenuItem throws NullPointer exception when setting the label with created peer.
  @key headful
  @run main SetLabelWithPeerCreatedTest
*/

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;

public class SetLabelWithPeerCreatedTest {
     Frame frame;
     public static void main(String[] args) throws Exception {
         SetLabelWithPeerCreatedTest test = new SetLabelWithPeerCreatedTest();
         test.start();
     }

    public void start() throws Exception {
        try {
            EventQueue.invokeAndWait(() -> {
                frame = new Frame("SetLabel with Peer Created Test");
                Menu menu = new Menu("Menu");
                MenuItem mi = new MenuItem("Item");
                MenuBar mb = new MenuBar();
                menu.add(mi);
                mb.add(menu);
                frame.setMenuBar(mb);
                frame.setSize(300, 200);
                frame.setLocationRelativeTo(null);
                mi.setLabel("new label");
                frame.setVisible(true);
                System.out.println("Test PASSED!");
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
