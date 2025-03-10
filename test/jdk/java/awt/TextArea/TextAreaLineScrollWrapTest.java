/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Frame;
import java.awt.TextArea;

/*
 * @test
 * @bug 4776535
 * @summary Regression: line should not wrap around into multi lines in TextArea.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TextAreaLineScrollWrapTest
 */

public class TextAreaLineScrollWrapTest {
    private static final String INSTRUCTIONS = """
            You should see a frame "TextAreaLineScrollWrapTest" with
            a TextArea that contains a very long line.
            If the line is wrapped the test is failed.

            Insert a lot of text lines and move a caret to the last one.
            If a caret hides and a content of the TextArea
            does not scroll the test is failed
            else the test is passed.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("TextAreaLineScrollWrapTest")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(TextAreaLineScrollWrapTest::createGUI)
                .build()
                .awaitAndCheck();
    }

    public static Frame createGUI() {
        Frame f = new Frame("TextAreaLineScrollWrapTest");
        f.add(new TextArea("long long long long long long long line...........",
                3, 4));
        f.setSize(100, 100);
        return f;
    }
}
