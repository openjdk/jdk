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
 * @bug 4648702
 * @summary TextArea horizontal scrollbar behavior is incorrect
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TextAreaHScrollbarTest
 */

public class TextAreaHScrollbarTest {
    private static final String INSTRUCTIONS = """
                Please look at the frame.
                If the vertical and horizontal scrollbars are visible
                the test passed else failed.
                """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("TextAreaHScrollbarTest")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(TextAreaHScrollbarTest::createGUI)
                .build()
                .awaitAndCheck();
    }

    public static Frame createGUI() {
        Frame test = new Frame();
        test.add(new TextArea("TextAreaHScrollbarTest", 5, 60,
                TextArea.SCROLLBARS_BOTH));
        test.setSize(200, 100);
        return test;
    }
}
