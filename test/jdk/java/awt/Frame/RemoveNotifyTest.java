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
  @bug 4154099
  @summary Tests that calling removeNotify() on a Frame and then reshowing
            the Frame does not crash or lockup
  @key headful
  @run main RemoveNotifyTest
*/

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;

public class RemoveNotifyTest {
    static Frame f;

    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(() -> {
            for (int i = 0; i < 100; i++) {
                try {
                    f = new Frame();
                    f.setBounds(10, 10, 100, 100);
                    MenuBar bar = new MenuBar();
                    Menu menu = new Menu();
                    menu.add(new MenuItem("foo"));
                    bar.add(menu);
                    f.setMenuBar(bar);

                    for (int j = 0; j < 5; j++) {
                        f.setVisible(true);
                        f.removeNotify();
                    }
                } finally {
                    if (f != null) {
                        f.dispose();
                    }
                }
            }
        });

      System.out.println("done");

    }

 }// class RemoveNotifyTest
