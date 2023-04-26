/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4185654
  @summary tests that the text insertion caret is positioned before the first character
  @key headful
*/

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.TextField;
import java.awt.TextArea;

public class InitialInsertionCaretPositionTest {
    static TextField textField;
    static TextArea textArea;
    static Frame frame;

    public static void main(String[] args) throws Exception {
        try {
            EventQueue.invokeAndWait(() -> {
                frame = new Frame("Caret Initial Position");
                textArea = new TextArea("abcdefghij\nabcdefghij");
                textField = new TextField("abcdefghij");
                frame.add(textField);
                frame.setSize(200, 200);
                frame.pack();
                frame.setVisible(true);

                if (textField.getCaretPosition() != 0) {
                    throw new RuntimeException("The text insertion caret for " +
                            "the text field is not " +
                            "initially set before the first character");
                }

                if (textArea.getCaretPosition() != 0) {
                    throw new RuntimeException("The text insertion caret for" +
                            " the text area is not initially set before the " +
                            "first character");
                }

                System.out.println("The test passed.");
            });
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
