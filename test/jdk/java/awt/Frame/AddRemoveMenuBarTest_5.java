/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Button;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.Robot;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/*
 * @test
 * @key headful
 * @bug 4159883
 * @summary Adding/Removing a menu causes frame to unexpected small size
 * @requires (os.family == "linux" | os.family == "windows")
 */

public class AddRemoveMenuBarTest_5 {

    static Frame frame;
    static MenuBar menu;
    static Button btnAdd, btnRemove;
    static Dimension oldSize;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        try {
            EventQueue.invokeAndWait(AddRemoveMenuBarTest_5::initAndShowGui);
            robot.waitForIdle();
            robot.delay(500);

            EventQueue.invokeAndWait(() -> {
                oldSize = frame.getSize();
                changeMenubar(true);
            });
            robot.waitForIdle();
            robot.delay(500);

            EventQueue.invokeAndWait(() -> {
                checkSize();
                changeMenubar(false);
            });
            robot.waitForIdle();
            robot.delay(500);

            EventQueue.invokeAndWait(AddRemoveMenuBarTest_5::checkSize);
        } finally {
            EventQueue.invokeAndWait(frame::dispose);
        }
    }

    public static void initAndShowGui() {
        frame = new Frame();
        frame.setLocationRelativeTo(null);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                System.out.println("Frame size:" + frame.getSize().toString());
                System.out.println("Button size:" + btnAdd.getSize().toString());
            }
        });
        frame.add("West", btnAdd = new Button("TRY:ADD"));
        frame.add("East", btnRemove = new Button("TRY:REMOVE"));


        btnAdd.addActionListener((e) -> changeMenubar(true));
        btnRemove.addActionListener((e) -> changeMenubar(false));
        frame.setSize(500, 100);
        frame.setVisible(true);
    }

    private static void changeMenubar(boolean enable) {
        if (enable) {
            menu = new MenuBar();
            menu.add(new Menu("BAAAAAAAAAAAAAAA"));
            menu.add(new Menu("BZZZZZZZZZZZZZZZ"));
            menu.add(new Menu("BXXXXXXXXXXXXXXX"));
        } else {
            menu = null;
        }
        frame.setMenuBar(menu);
        frame.invalidate();
        frame.validate();

        System.out.println("Frame size:" + frame.getSize().toString());
        System.out.println("Button size:" + btnAdd.getSize().toString());
    }

    private static void checkSize() {
        Dimension newSize = frame.getSize();
        if (!oldSize.equals(newSize)) {
            throw new RuntimeException("Frame size changed: old %s new %s"
                    .formatted(oldSize, newSize));
        }
    }
}
