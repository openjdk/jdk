/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.Platform;
import java.awt.GridLayout;
import java.awt.TextArea;
import java.awt.TextField;
import javax.swing.JPanel;

/*
 * @test
 * @bug 8000423 7197619 8025649
 * @summary Check if diacritical signs could be typed for TextArea and TextField
 * @requires (os.family == "windows" | os.family == "linux")
 * @library /java/awt/regtesthelpers /test/lib
 * @build PassFailJFrame jdk.test.lib.Platform
 * @run main/manual DiacriticsTest
 */

public class DiacriticsTest {

    private static final String INSTRUCTIONS_WIN = """
    Test run requires the following keyboard layouts to be installed:
    - Hungarian
    - A keyboard layout having compose function or compose-like key. Programmer
    Dvorak (http://www.kaufmann.no/roland/dvorak/) is suggested to use.

    To the right are a text area and a text field, you should check the behavior
    for both of them.

    To test the JDK-7197619 fix:
    Please switch to Hungarian keyboard layout and try to type diacritics
    (Ctrl+Alt+2 e; Ctrl+Alt+2 E)

    To test the JDK-8139189 fix:
    Please switch to Programmer Dvorak keyboard layout try to type diacritics
    using compose combinations (Compose+z+d, Compose+z+Shift+d).

    The Compose key in the Programmer Dvorak layout is OEM102, the key located
    between the and Z keys on a standard 102-key keyboard.
    If you do not have this key on your keyboard, you can skip this part of the test.

    If you can do that then the test is passed; otherwise failed.
    """;

    private static final String INSTRUCTIONS_LIN = """
    Test run requires the following keyboard layouts to be installed:
    - English (US, alternative international), aka English (US, alt. intl.)
    - A keyboard layout having compose function or compose-like key. Programmer
    Dvorak (http://www.kaufmann.no/roland/dvorak/) is suggested to use.

    To the right are a text area and a text field, you should check the behavior
    for both of them.

    To test the JDK-8000423 fix:
    Please switch to US alternative international layout and try to type diacritics
    (using the following combinations: `+e; `+u; etc.)

    To test the JDK-8139189 fix:
    Please switch to Programmer Dvorak keyboard layout try to type diacritics
    using compose combinations (Compose+z+d, Compose+z+Shift+d)..

    The Compose key in the Programmer Dvorak layout is OEM102, the key located
    between the and Z keys on a standard 102-key keyboard.

    If the above key does not work in the Gnome shell,
    it can be overridden in the system preferences:
    System > Keyboard > Special character entry > Compose key
    and set it to another key(e.g. menu key or scroll lock.)

    If you can do that then the test is passed; otherwise failed.
    """;

    public static void main(String[] args) throws Exception {
        String instructions = Platform.isWindows()
                ? INSTRUCTIONS_WIN
                : INSTRUCTIONS_LIN;

        PassFailJFrame
                .builder()
                .title("DiacriticsTest Instructions")
                .instructions(instructions)
                .splitUIRight(DiacriticsTest::createPanel)
                .testTimeOut(10)
                .rows((int) instructions.lines().count() + 2)
                .columns(50)
                .build()
                .awaitAndCheck();
    }

    public static JPanel createPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(2, 1));

        TextArea txtArea = new TextArea();
        panel.add(txtArea);

        TextField txtField = new TextField();
        panel.add(txtField);

        return panel;
    }
}
