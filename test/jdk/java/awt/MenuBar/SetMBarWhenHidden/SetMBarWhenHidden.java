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
 * @bug 4105881
 * @summary Sets the menu bar while frame window is hidden, then shows
    frame again
 * @key headful
 * @run main SetMBarWhenHidden
 */

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.Rectangle;

// test case for 4105881: FRAME.SETSIZE() DOESN'T WORK FOR SOME SOLARIS WITH
// JDK115+CASES ON
public class SetMBarWhenHidden {
    private static Frame f;
    private static Rectangle startBounds;
    private static Rectangle endBounds;

    public static void main(String[] args) throws Exception {
        try {
            EventQueue.invokeAndWait(() -> {
                f = new Frame("Set MenuBar When Hidden Test");
                Menu file;
                Menu edit;
                MenuBar menubar = new MenuBar();
                file = new Menu("File");
                menubar.add(file);
                edit = new Menu("Edit");
                menubar.add(edit);
                edit.setEnabled(false);
                f.setMenuBar(menubar);
                f.setSize(200, 200);
                startBounds = f.getBounds();
                System.out.println("About to call setVisible(false)");
                f.setVisible(false);
                System.out.println("About to call setSize(500, 500)");
                f.setSize(500, 500);
                // create a new menubar and add
                MenuBar menubar1 = new MenuBar();
                menubar1.add(file);
                menubar1.add(edit);
                System.out.println("About to call setMenuBar");
                f.setMenuBar(menubar1);
                System.out.println("About to call setVisible(true)");
                f.setVisible(true);
                endBounds = f.getBounds();
            });
            if (startBounds.getHeight() > endBounds.getHeight() &&
                startBounds.getWidth() > endBounds.getWidth()) {
                throw new RuntimeException("Test failed. Frame size didn't " +
                        "change.\nStart: " + startBounds + "\n" +
                        "End: " + endBounds);
            } else {
                System.out.println("Test passed.\nStart: " + startBounds +
                        "\nEnd: " + endBounds);
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (f != null) {
                    f.dispose();
                }
            });
        }
    }
}
