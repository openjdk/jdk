/*
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/*
 * @test
 * @bug 4490692
 * @summary [Linux] Test for KEY_PRESS event for accented characters.
 * @requires (os.family == "linux")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4490692
 */

public class bug4490692 {
    private static final String INSTRUCTIONS = """
            This test is for unix platforms only.
            Before the test, you need to modify the keyboard mapping for
            Tab by issuing the following command:

            xmodmap -e 'keycode 23 = aacute'  (this is for Linux)
            xmodmap -e 'keycode 60 = aacute'  (this is for Solaris Sparc)

            This command lets you type 'a with acute (à)' character when you press
            'Tab' key in the JTextField provided below the logging area.
            After the test, please DO NOT fail to restore the original
            key mapping by doing the following.

            xmodmap -e 'keycode 23 = Tab'  (this is for Linux)
            xmodmap -e 'keycode 60 = Tab'  (this is for Solaris Sparc)

            CASE 1: This is a manual check and for SOLARIS SPARC keyboard
            only. Check whether the key sequence ("Compose", "a", " ' ")
            generates a-acute character in en_US locale.

            CASE 2: This step is automated and applicable for both
            keyboards - LINUX & SOLARIS SPARC.
            When Tab key is pressed it should generate a-acute (à)
            character, this test automatically passes if the correct character
            is generated on keypress else fails.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(45)
                .testTimeOut(10)
                .splitUIBottom(bug4490692::createUI)
                .logArea(8)
                .build()
                .awaitAndCheck();
    }

    private static JComponent createUI() {
        JPanel panel = new JPanel();
        JTextField textField = new JTextField("", 20);
        panel.add(new JLabel("Text field:"));

        textField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                PassFailJFrame.log(e.paramString());
                if (e.getKeyCode() == 23 || e.getKeyCode() == 60
                        || e.paramString().contains("rawCode=23")
                        || e.paramString().contains("rawCode=60")) {
                    if (e.getKeyChar() == 0x00e1) {
                        PassFailJFrame.forcePass();
                    } else {
                        PassFailJFrame.forceFail("Tab keypress DID NOT"
                                + " produce the expected accented character - aacute");
                    }
                }
            }
        });

        panel.add(textField);
        return panel;
    }
}
