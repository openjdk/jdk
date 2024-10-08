/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import jtreg.SkippedException;
import sun.awt.OSInfo;

/*
 * @test
 * @bug 8154816
 * @summary Caps Lock doesn't work as expected when using Pinyin Simplified input method
 * @requires (os.family == "mac")
 * @modules java.desktop/sun.awt
 * @library /java/awt/regtesthelpers /test/lib
 * @build PassFailJFrame jtreg.SkippedException Util
 * @run main/manual PinyinIMCapsTest
 */

public class PinyinIMCapsTest {
    private static final String INSTRUCTIONS = """
            The test verifies if the Caps Lock key works properly with Pinyin
            input method, (i.e. if Caps Lock is pressed, input should be
            switched to lowercase latin letters).

            Test settings:
            Go to "System Preferences -> Keyboard -> Input Sources" and
            add "Pinyin – Traditional" or "Pinyin – Simplified" IM from Chinese language group.
            Set current IM to "Pinyin".

            1. Set focus to the text field shown below and press Caps Lock key on the keyboard.
            2. Press "a" character on the keyboard
            3. If "a" character is displayed in the text field, press "Pass",
               if "A" character is displayed, press "Fail".
            """;

    public static void main(String[] args) throws Exception {
        if (OSInfo.getOSType() != OSInfo.OSType.MACOSX) {
            throw new SkippedException("This test is for MacOS only");
        }
        PassFailJFrame.builder()
                      .title("Test Pinyin Input Method")
                      .instructions(INSTRUCTIONS)
                      .rows((int) INSTRUCTIONS.lines().count() + 2)
                      .columns(45)
                      .splitUIBottom(PinyinIMCapsTest::createUI)
                      .testTimeOut(10)
                      .build()
                      .awaitAndCheck();
    }

    private static JComponent createUI() {
        JPanel panel = new JPanel();
        JTextField input = new JTextField(20);
        panel.add(new JLabel("Text field:"));
        panel.add(input);
        return panel;
    }
}

