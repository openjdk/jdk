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
    static String failureMessage = "";

    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(() -> {
            textArea = new TextArea("abcdefghij\nabcdefghij");
            textField = new TextField("abcdefghij");

            boolean textFieldPassed = (textField.getCaretPosition() == 0);
            boolean textAreaPassed = (textArea.getCaretPosition() == 0);

            if (!textFieldPassed) {
                failureMessage += "   The text insertion caret for the text field is not\n";
                failureMessage += "   initially set before the first character.\n";
            }
            if (!textAreaPassed) {
                failureMessage += "   The text insertion caret for the text area is not\n";
                failureMessage += "   initially set before the first character.\n";
            }
            if (textAreaPassed && textFieldPassed) {
                System.out.println("The test passed.");
            } else {
                System.out.println("The test failed:");
                System.out.println(failureMessage);
            }
            if (!textAreaPassed || !textFieldPassed) {
                throw new RuntimeException(failureMessage);
            }
        });
    }
}
