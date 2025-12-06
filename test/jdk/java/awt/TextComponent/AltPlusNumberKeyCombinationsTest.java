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
 * @bug 4737679 4623376 4501485 4740906 4708221
 * @requires (os.family == "windows")
 * @summary Alt+Left/right/up/down generate characters in JTextArea
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual AltPlusNumberKeyCombinationsTest
 */

import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.TextArea;
import java.awt.TextField;

public class AltPlusNumberKeyCombinationsTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                [WINDOWS PLATFORM ONLY]
                Please do the following steps for both TextField and TextArea:
                1. Hold down ALT and press a NON-NUMPAD right arrow, then release
                   ALT key. If any symbol is typed the test failed.
                2. Hold down ALT and press one after another the following
                   NUMPAD keys: 0, 1, 2, 8. Release ALT key. If the Euro symbol
                   is not typed the test failed
                3. Hold down ALT and press one after another the following
                   NUMPAD keys: 0, 2, 2, 7. Release ALT key. If nothing or
                   the blank symbol is typed the test failed
                 If all the steps are done successfully the test PASSed,
                 else test FAILS.
                """;
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(initialize())
                .build()
                .awaitAndCheck();
    }

    public static Frame initialize() {
        Frame f = new Frame("key combination test");
        f.setLayout(new FlowLayout());
        TextField tf = new TextField("TextField");
        f.add(tf);
        TextArea ta = new TextArea("TextArea");
        f.add(ta);
        f.setSize(200, 200);
        return f;
    }
}
