/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6197830
 * @requires (os.family == "linux")
 * @summary Fix for 4729669 does not work on Motif and GTK look and feels
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug6197830
 */

import java.util.List;
import javax.swing.JFrame;

public class bug6197830 {

    private static final String INSTRUCTIONS = """
            Four windows should appear: Left-to-right and Right-to-left for
            the two different Look and Feels (Motif and GTK).
            Check that text on all the menu items of all menus is properly
            vertically aligned.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("bug6197830 Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(bug6197830::createTestUI)
                .positionTestUIBottomRowCentered()
                .build()
                .awaitAndCheck();
    }

    private static List<JFrame> createTestUI() {
        JFrame frame1 = MenuItemTestHelper.getMenuItemTestFrame(true,
                "com.sun.java.swing.plaf.motif.MotifLookAndFeel");
        JFrame frame2 = MenuItemTestHelper.getMenuItemTestFrame(false,
                "com.sun.java.swing.plaf.motif.MotifLookAndFeel");
        JFrame frame3 = MenuItemTestHelper.getMenuItemTestFrame(true,
                "com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
        JFrame frame4 = MenuItemTestHelper.getMenuItemTestFrame(false,
                "com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
        return List.of(frame1, frame2, frame3, frame4);
    }
}
