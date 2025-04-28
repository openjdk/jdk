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
                .position(PassFailJFrame.Position.TOP_LEFT_CORNER)
                .build()
                .awaitAndCheck();
    }

    private static List<JFrame> createTestUI() {
        JFrame frame1 = MenuItemTest.doMenuItemTest(true,
                            "com.sun.java.swing.plaf.motif.MotifLookAndFeel",
                             20);
        frame1.setLocation(300, 300);
        JFrame frame2 = MenuItemTest.doMenuItemTest(false,
                            "com.sun.java.swing.plaf.motif.MotifLookAndFeel",
                             20);
        frame2.setLocation((int)(frame1.getLocation().getX() + frame1.getWidth()
                            + 100), 300);
        JFrame frame3 = MenuItemTest.doMenuItemTest(true,
                             "com.sun.java.swing.plaf.gtk.GTKLookAndFeel", 420);
        frame3.setLocation(300, (int)(frame1.getLocation().getY()
                                 + frame1.getHeight() + 100));
        JFrame frame4 = MenuItemTest.doMenuItemTest(false,
                             "com.sun.java.swing.plaf.gtk.GTKLookAndFeel", 420);
        frame4.setLocation((int)(frame3.getLocation().getX() + frame3.getWidth()
                            + 100),
                           (int)frame3.getLocation().getY());
        return List.of(frame1, frame2, frame3, frame4);
    }
}
