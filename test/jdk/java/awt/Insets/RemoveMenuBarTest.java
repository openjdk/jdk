/*
 * Copyright (c) 2006, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 6353381
  @summary REG: Container.getInsets() returns an incorrect value after removal of menubar, Win32
  @key headful
  @run main RemoveMenuBarTest
*/
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.Menu;
import java.awt.MenuBar;

public class RemoveMenuBarTest {
    static Frame frame;

    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(() -> {
            try {
                // old insets: top>0 | left>0
                // new insets: top=0 & left=0
                // the bug is that updating doesn't happen
                frame = new Frame();
                MenuBar menubar = new MenuBar();
                frame.setBounds(100,100,100,100);
                frame.setUndecorated(true);
                frame.pack();
                menubar.add(new Menu());
                frame.setMenuBar(menubar);
                System.out.println(frame.getInsets());

                frame.setMenuBar(null);
                Insets insets = frame.getInsets();
                System.out.println(insets);
                if (insets.top != 0 || insets.left != 0 ||
                    insets.bottom !=0 || insets.right != 0) {
                    throw new RuntimeException("Test failed: the incorrect insets");
                }
            } finally {
                if (frame != null) {
                    frame.dispose();
                }
            }
        });
    }
}
