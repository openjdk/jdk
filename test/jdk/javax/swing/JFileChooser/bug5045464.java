/*
 * Copyright (c) 2004, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5045464
 * @requires (os.family == "linux")
 * @summary Regression: GTK L&F, JFileChooser shows "null/" in folder list
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug5045464
 */

import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class bug5045464 {
    private static final String INSTRUCTIONS = """
            When the filechooser appears check the directory list (the left list).
            If it starts with two items: "./" (current directory)
            and "../" (parent directory) press PASS.
            If something else is here (e.g. "null/" instead of "./")
            press FAIL.
            """;

    public static void main(String[] argv) throws Exception {
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(bug5045464::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JComponent createTestUI() {
        JFileChooser fc = new JFileChooser();
        fc.setControlButtonsAreShown(false);
        try {
         UIManager.setLookAndFeel("com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
        } catch (Exception ex) {
        throw new RuntimeException("Test Failed!", ex);
        }
        SwingUtilities.updateComponentTreeUI(fc);
        return fc;
    }
}
