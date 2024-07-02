/*
 * Copyright (c) 2010, 2024, Oracle and/or its affiliates. All rights reserved.
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
import javax.swing.JColorChooser;
import javax.swing.JLabel;

/*
 * @test
 * @bug 6977726
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Checks if JColorChooser.setPreviewPanel(JLabel) doesn't remove the preview panel but
 *          removes the content of the default preview panel
 * @run main/manual Test6977726
 */

public class Test6977726 {

    public static void main(String[] args) throws Exception {
        String instructions = """
                Check that there is a panel with "Text Preview Panel" text
                and with title "Preview" in the JColorChooser.
                Test passes if the panel is as described, test fails otherwise.""";

        PassFailJFrame.builder()
                .title("Test6977726")
                .instructions(instructions)
                .rows(5)
                .columns(40)
                .testTimeOut(2)
                .testUI(Test6977726::createColorChooser)
                .build()
                .awaitAndCheck();
    }

    private static JColorChooser createColorChooser() {
        JColorChooser chooser = new JColorChooser(Color.BLUE);
        chooser.setPreviewPanel(new JLabel("Text Preview Panel"));
        return chooser;
    }
}
