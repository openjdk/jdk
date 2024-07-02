/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Color;
import java.awt.Font;
import javax.swing.JColorChooser;
import javax.swing.UIManager;

/*
 * @test
 * @bug 4887836
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Checks for white area under the JColorChooser Swatch tab
 * @run main/manual Test4887836
 */

public class Test4887836 {

    public static void main(String[] args) throws Exception {
        String instructions = """
                                If you do not see white area under the \"Swatches\" tab,
                                then test passed, otherwise it failed.""";

        PassFailJFrame.builder()
                .title("Test4759306")
                .instructions(instructions)
                .rows(5)
                .columns(40)
                .testTimeOut(10)
                .testUI(Test4887836::createColorChooser)
                .build()
                .awaitAndCheck();
    }

    private static JColorChooser createColorChooser() {
        JColorChooser chooser = new JColorChooser(Color.LIGHT_GRAY);

        UIManager.put("Label.font", new Font("Font.DIALOG", 0, 36));
        return chooser;
    }
}
