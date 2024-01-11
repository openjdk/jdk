/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @bug 4260109
  @summary tests that the text limit is set to the maximum possible value
  @key headful
*/

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.TextArea;

public class TextLimitTest {
    static Frame frame;
    static TextArea textarea;

    public static void main(String[] args) throws Exception {
        try {
            EventQueue.invokeAndWait(() -> {
                StringBuffer buffer = new StringBuffer();
                frame = new Frame("Text Limit Test");
                textarea = new TextArea(3, 10);
                frame.setLayout(new BorderLayout());
                frame.add(textarea);
                frame.setSize(200, 200);
                frame.pack();
                frame.setVisible(true);

                /*
                 * The magic number 0xF700 was choosen because of the two reasons:
                 *  - it shouldn't be greater since on win95 (even in native win32 apps)
                 *    adding more than 0xF800 symbols to a textarea doesn't always work,
                 *  - it shouldn't be less since in this case we won't run in the stack
                 *    overflow on Win95 even if we use W2A allocating memory on the stack.
                 */
                for (int i = 0; i < 0xF700; i += 0x10) {
                    buffer.append("0123456789abcdef");
                }

                textarea.setText(buffer.toString());
                System.out.println("Text length before append: " +
                        Integer.toString(textarea.getText().length(), 16));

                textarea.append("0123456789abcdef");

                int len = textarea.getText().length();
                System.out.println("Text length after append: " +
                        Integer.toString(len, 16));
                if (len != 0xF710) {
                    throw new RuntimeException("Test failed: textarea has " +
                            "wrong text limit!");
                }
            });
            System.out.println("Test pass");
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
