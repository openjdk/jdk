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

import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Label;
import java.awt.MenuItem;
import java.awt.PopupMenu;

import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/*
 * @test
 * @bug 4281273
 * @summary PopupMenu crashed in Java. Simplified testcase.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @requires (os.family == "windows")
 * @run main/manual PopupMenuCrash
 */

public class PopupMenuCrash {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                This tests a windows specific problem.
                When you see a frame titled "PopupMenuCrash Test", right-click on it
                several times for a few seconds. Then wait about 10 seconds before the
                PopupMenus start to appear. Then dispose them one by one by clicking on them.
                When PopupMenus do not appear anymore, press Pass.
                In case of a failure, you'll see a crash.
                """;

        PassFailJFrame.builder()
                .title("PopupMenuCrash Instruction")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(PopupMenuCrash::createUI)
                .build()
                .awaitAndCheck();
    }

    private static Frame createUI() {
        final Frame f = new Frame("PopupMenuCrash Test");
        f.setLayout(new FlowLayout());
        f.add(new Label("Press right mouse button inside this frame."));
        f.add(new Label("A pop-up menu should appear."));
        f.addMouseListener(new MouseAdapter() {
            PopupMenu popup;
            boolean firstPress = true;

            @Override
            public void mousePressed(MouseEvent evt) {
                if (firstPress) {
                    firstPress = false;
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException ignored) {
                    }
                }

                if ((evt.getModifiers() & InputEvent.BUTTON3_MASK) != 0) {
                    popup = new PopupMenu("Popup Menu Title");
                    MenuItem mi = new MenuItem("MenuItem");
                    popup.add(mi);
                    f.add(popup);
                    popup.show(evt.getComponent(), evt.getX(), evt.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent evt) {
                if ((evt.getModifiers() & InputEvent.BUTTON3_MASK) != 0) {
                    if (popup != null) {
                        f.remove(popup);
                        popup = null;
                    }
                }
            }
        });

        f.setSize(400, 350);
        return f;
    }
}
