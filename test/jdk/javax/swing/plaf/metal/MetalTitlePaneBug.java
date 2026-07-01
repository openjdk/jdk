/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8078744
 * @summary Verifies right half of system menu icon on title bar
 *          activates when clicked
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MetalTitlePaneBug
 */

import javax.swing.JFrame;
import javax.swing.UIManager;

public class MetalTitlePaneBug {

    static final String INSTRUCTIONS = """
        A Frame is shown with a titlepane.
        Click on the left edge of the system menu icon on the title pane.
        It should show "Restore", "Minimize", "Maximize", "Close" menu.
        Click on the right edge of the system menu icon.
        It should also show "Restore", "Minimize", "Maximize", "Close" menu.
        If it shows, press Pass else press Fail.
    """;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(MetalTitlePaneBug::createUI)
                .build()
                .awaitAndCheck();
    }

    static JFrame createUI() {
        JFrame.setDefaultLookAndFeelDecorated(true);
        JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(200, 100);
        return frame;
    }
}
