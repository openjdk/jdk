/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 5100200
  @summary IAE in X11 text field peer code
  @key headful
*/

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.TextField;

public class CorrectSetCaretPositionDuringInitPeerTest
{
    static TextField tf;
    static Frame frame;

    public static void main(String[] args) throws Exception {
        try{
            EventQueue.invokeAndWait(() -> {
                frame = new Frame("Caret Position test");
                tf = new TextField("Very very very long string");
                tf.setSelectionStart(10);
                tf.setText("Short"); // now TextField.length() less than 10
                frame.add(tf);

                frame.pack();
                frame.setVisible(true);
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
