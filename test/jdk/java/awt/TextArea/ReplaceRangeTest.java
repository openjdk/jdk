/*
 * Copyright (c) 2004, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 5025532
  @requires (os.family == "windows")
  @summary Tests that textarea replaces text correctly if the text contains
   line separators
  @key headful
*/

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.TextArea;

import java.lang.reflect.InvocationTargetException;

public class ReplaceRangeTest {
    static Frame f;

    public static void main(String[] args) throws InterruptedException, InvocationTargetException {
        try {
            EventQueue.invokeAndWait(() -> {
                f = new Frame("Test frame");
                f.setSize(400, 400);
                f.setLayout(new GridLayout(3, 1));

                TextArea textArea1 = new TextArea(5, 80);
                TextArea textArea2 = new TextArea(5, 80);
                TextArea textArea3 = new TextArea(5, 80);
                f.add(textArea1);
                f.add(textArea2);
                f.add(textArea3);
                f.setVisible(true);

                textArea1.setText("01234");
                textArea1.replaceRange("X", 3, 4);
                textArea2.setText("0\r\n234");
                textArea2.replaceRange("X", 3, 4);
                textArea3.setText("0\n\n34");
                textArea3.replaceRange("X", 3, 4);

                if (textArea1.getText().equals("012X4") &&
                        textArea2.getText().equals("0\r\n2X4") &&
                        textArea3.getText().equals("0\n\nX4")) {
                    System.out.println("Test Pass");
                    return;
                } else {
                    throw new RuntimeException("Test FAILED");
                }
            });
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (f != null) {
                    f.dispose();
                }
            });
        }
    }
}
