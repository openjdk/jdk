/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @summary Test that medium weight submenus are not hidden by a heavyweight canvas.
 * @bug 4188832
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4188832
 */

import java.awt.Color;
import java.awt.Panel;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

public class bug4188832 {

    static final String INSTRUCTIONS = """
        Select the File menu, then select the "Save As..." submenu.
        If you can see the submenu items displayed, press PASS, else press FAIL
    """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
            .instructions(INSTRUCTIONS)
            .columns(40)
            .testUI(bug4188832::createUI)
            .build()
            .awaitAndCheck();
    }

    static JFrame createUI() {
        // for Medium Weight menus
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);
        JFrame frame = new JFrame("bug4188832");

        // Create the menus
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        menuBar.add(fileMenu);
        fileMenu.add(new JMenuItem("New"));
        fileMenu.add(new JMenuItem("Open"));
        fileMenu.add(new JMenuItem("Save"));
        JMenu sm = new JMenu("Save As...");
        // these guys don't show up
        sm.add(new JMenuItem("This"));
        sm.add(new JMenuItem("That"));
        fileMenu.add(sm);
        fileMenu.add(new JMenuItem("Exit"));
        frame.setJMenuBar(menuBar);

        Panel field = new Panel(); // a heavyweight container
        field.setBackground(Color.blue);
        frame.add(field);
        frame.setSize(400, 400);
        return frame;
    }
}
