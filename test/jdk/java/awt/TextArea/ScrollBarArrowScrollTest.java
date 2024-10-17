/*
 * Copyright (c) 2004, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6175401
 * @summary Keeping the left arrow pressedon horiz scrollbar
 *          does not scroll the text in TextArea, XToolkit
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ScrollBarArrowScrollTest
*/


public class ScrollBarArrowScrollTest extends Frame {
    private static final String INSTRUCTIONS = """
                1) Make sure, that the TextArea component has focus.
                2) Press 'END' key in order to keep cursor at the end
                   of the text of the TextArea component.
                3) Click on the left arrow on the horizontal scrollbar
                   of the TextArea component and keep it pressed.
                4) If the text just scrolls once and stops, the test failed.
                   Otherwise, the test passed.
                """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("ScrollBarArrowScrollTest")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(ScrollBarArrowScrollTest::new)
                .build()
                .awaitAndCheck();
    }

    public ScrollBarArrowScrollTest() {
        TextArea textarea = new TextArea("Very very very long string !!!! ", 10, 3);
        add(textarea);
        pack();

    }
}
