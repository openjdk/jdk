/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4308606
 * @summary Tests whether the keys on the numeric keyboard work
 *            correctly under French input locale.
 * @key i18n
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FrenchKeyboard
 */

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.TextField;
import java.lang.reflect.InvocationTargetException;

public class FrenchKeyboard extends Frame {
    static String INSTRUCTIONS = """
           This test is intended for computers with French input method. If French
           input method can not be enabled or your keyboard does not have a numeric
           keypad press "Pass" to skip the test.
           Make sure that French input method is active and the NumLock is on.
           Click on the text field in the window called "Check your keys"
           and type once of each of the following keys on the numeric keypad:
           /*-+1234567890
           If all the expected characters are displayed exactly once press "Pass".
           If any characters do not display or display multiple times press "Fail".
           """;

    public FrenchKeyboard() {
        super("Check your keys");
        setLayout(new BorderLayout());
        TextField tf = new TextField(30);
        add(tf, BorderLayout.CENTER);
        pack();
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        PassFailJFrame.builder()
                .title("FrenchKeyboard Instructions")
                .instructions(INSTRUCTIONS)
                .testUI(FrenchKeyboard::new)
                .build()
                .awaitAndCheck();
    }
}

