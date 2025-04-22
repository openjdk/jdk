/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4415505
 * @requires (os.family == "windows")
 * @summary Tests JButton appearance under Windows LAF
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4415505
 */

import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JToggleButton;
import javax.swing.UIManager;

public class bug4415505 {
    private static final String INSTRUCTIONS = """
            <html>
            <p>This test is for Windows LaF.
            Press the button named "Button" using mouse and check that it has
            "pressed" look. It should look like the selected toggle button
            near it.</p>

            <p>If above is true press PASS else FAIL.</p>
            <html>
            """;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .rows(5)
                .columns(40)
                .testUI(bug4415505::createAndShowUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createAndShowUI() {
        JButton button = new JButton("Button");
        button.setFocusPainted(false);
        JToggleButton tbutton = new JToggleButton("ToggleButton");
        tbutton.setSelected(true);

        JFrame jFrame = new JFrame("bug4415505");
        jFrame.setLayout(new FlowLayout(FlowLayout.CENTER));
        jFrame.add(button);
        jFrame.add(tbutton);
        jFrame.setSize(300, 100);
        return jFrame;
    }
}
