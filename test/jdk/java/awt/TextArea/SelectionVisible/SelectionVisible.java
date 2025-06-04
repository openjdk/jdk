/*
 * Copyright (c) 1999, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.InvocationTargetException;

import static java.awt.EventQueue.invokeAndWait;

/*
  @test
  @key headful
  @bug 4082144 7150100
  @summary  Ensures that TextArea.select() works when called
            before setVisible()
  @run main SelectionVisible
*/

public class SelectionVisible {

    private static TextArea ta;
    private static Frame frame;

    public static void createTestUI() {
        frame = new Frame("Test 4082144 7150100");
        ta = new TextArea(4, 20);
        ta.setText("01234\n56789");
        ta.select(3, 9);

        frame.add(ta);
        frame.setLocationRelativeTo(null);
        frame.pack();
        frame.setVisible(true);

        ta.requestFocus();
    }

    public static void test() throws InterruptedException,
            InvocationTargetException {
        String selectedText = ta.getSelectedText();
        System.out.println("selectedText : " + selectedText);
        invokeAndWait(SelectionVisible::disposeFrame);
        if (!selectedText.equals("34\n567")) {
            throw new RuntimeException("Expected '34\n567' to be " +
                    "selected text, but got " + selectedText);
        }
        System.out.println("Test passed");
    }

    public static void disposeFrame() {
        if (frame != null) {
            frame.dispose();
        }
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        invokeAndWait(SelectionVisible::createTestUI);
        test();
    }

}
