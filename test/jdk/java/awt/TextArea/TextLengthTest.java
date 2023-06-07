/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4072264
  @summary REGRESSION:Test to verify getSelectedText,
  getSelectedStart/End in TextArea class
  @key headful
*/

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Robot;
import java.awt.TextArea;

public class TextLengthTest {
    static final int MY_SIZE = 100;
    static final int MY_START = 13;
    static final int MY_END = 47;
    TextArea ta;
    Frame f;
    int mySize;
    int myStart;
    int myEnd;

    public static void main(String[] args) throws Exception {
        TextLengthTest textLengthTest = new TextLengthTest();
        textLengthTest.init();
        textLengthTest.start();
    }

    public void init() throws Exception {
        EventQueue.invokeAndWait(() -> {
            f = new Frame("TextLengthTest");
            ta = new TextArea(15, 30);
            f.add(ta);
            f.setSize(400, 400);
            f.setVisible(true);
        });
    }

    public void start() throws Exception {
        try {
            Robot r = new Robot();
            r.delay(1000);
            r.waitForIdle();
            EventQueue.invokeAndWait(() -> {
                StringBuffer bigStringBuffer = new StringBuffer();

                for (int i = 1; i <= 10; i++) {
                    bigStringBuffer.append("abcdefghi\n");
                }

                ta.setText(bigStringBuffer.toString());

                mySize = bigStringBuffer.toString().length();
                System.out.println("String size = " + mySize);

                if (mySize != MY_SIZE) {
                    throw new Error("The string size is " +
                            mySize + "but it should be " + MY_SIZE);
                }

                ta.select(MY_START, MY_END);

                String str = new String(ta.getSelectedText());
                str = str.toUpperCase();

                myStart = ta.getSelectionStart();
                myEnd = ta.getSelectionEnd();
                System.out.println("Selected string start = " + myStart);
                System.out.println("Selected string end = " + myEnd);

                if (myStart != MY_START) {
                    throw new Error("The selected text starts at " +
                            mySize + "but it should start at " + MY_START);
                }

                if (myEnd != MY_END) {
                    throw new Error("The selected text ends at " +
                            myEnd + "but it should end at " + MY_END);
                }

                ta.replaceRange(str, myStart, myEnd);
            });
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (f != null) {
                    f.dispose();
                }
            });
        }
        System.out.println("Test Pass");
    }
}
