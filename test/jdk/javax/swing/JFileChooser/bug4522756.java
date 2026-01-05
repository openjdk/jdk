/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4522756
 * @requires (os.family == "windows")
 * @summary Verifies that the Desktop icon is not missing when
            JFileChooser is opened for the first time.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4522756
 */

import javax.swing.JFileChooser;
import javax.swing.UIManager;

public class bug4522756 {
    private static final String INSTRUCTIONS = """
            Verify the following:

            1. If Desktop icon image is present on the Desktop button
               on the left panel of JFileChooser.
            2. Press Desktop button. Check that you actually
               go up to the desktop.

            If the above is true, press PASS else FAIL.
            """;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(50)
                .rows(12)
                .testUI(() -> {
                    JFileChooser jfc = new JFileChooser();
                    jfc.setControlButtonsAreShown(false);
                    return jfc;
                })
                .build()
                .awaitAndCheck();
    }
}
