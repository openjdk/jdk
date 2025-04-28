/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4122687 4209844
 * @summary Characters typed with AltGr have Alt bit set on
 *                 KEY_TYPED events
 * @requires (os.family == "windows")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual AltGrTest
 */

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.TextField;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.lang.reflect.InvocationTargetException;

public class AltGrTest extends Frame implements KeyListener {
    static String INSTRUCTIONS = """
            Switch to German (Germany) keyboard layout and type
            few characters using <AltGr> key.
            Note: on windows keyboards without an AltGr key,
            you should use Ctrl-Alt to synthesize AltGr.
            For example, on German keyboards, `@' is AltGr-Q
            `{' is AltGr-7 and '[' is AltGr-8
            If you see the corresponding symbols appear in the text field
            and there are no entries in log area starting with word "FAIL:"
            press "Pass", otherwise press "Fail".
            """;

    public AltGrTest() {
        setLayout(new BorderLayout());
        TextField entry = new TextField();
        entry.addKeyListener(this);
        add(entry, BorderLayout.CENTER);
        pack();
    }

    public void keyTyped(KeyEvent e) {
        PassFailJFrame.log("----");
        PassFailJFrame.log("Got " + e);

        if (e.isControlDown() || e.isAltDown()) {
            PassFailJFrame.log("FAIL: character typed has following modifiers bits set:");
            PassFailJFrame.log((e.isControlDown() ? " Control" : "")
                             + (e.isAltDown() ? " Alt" : ""));
        }

        if (!(e.isAltGraphDown())) {
            PassFailJFrame.log("FAIL: AltGraph modifier is missing");
        }
    }

    public void keyPressed(KeyEvent ignore)  {}
    public void keyReleased(KeyEvent ignore) {}

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .logArea(10)
                .testUI(AltGrTest::new)
                .build()
                .awaitAndCheck();
    }
}
