/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8372103
 * @summary Verifies Metal JButton show focus even if there's no text or icon
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MetalFocusBug
 */

import java.awt.Color;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.plaf.ColorUIResource;
import javax.swing.UIManager;

public class MetalFocusBug {

    static final String INSTRUCTIONS = """
        A JFrame is shown with 2 JButtons.
        A button "Top" at TOP and
        another button with empty text and without icon at the bottom.
        Initially the focus is at the "TOP" button.
        Verify that Red color focus rectangle is around the "TOP" text.
        Press "Tab" key and verify the bottom button gets focus via
        red color focus rectangle.
        If red color focus rectangle is seen at the bottom button,
        press Pass else press Fail.
    """;

    public static void main(String[] args) throws Exception {
        // Set Metal L&F
        UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(MetalFocusBug::createUI)
                .build()
                .awaitAndCheck();
    }

    static JFrame createUI() {
        // Make focus rectangle very obvious.
        UIManager.put("Button.focus", new ColorUIResource(Color.RED));
        JFrame frame = new JFrame("MetalFocusBug");
        frame.getContentPane().add(new JButton("TOP"), "North");
        frame.getContentPane().add(new JButton(""), "Center");
        frame.setSize(300, 300);
        return frame;
    }
}
