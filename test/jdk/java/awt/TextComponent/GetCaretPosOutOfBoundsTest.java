/*
 * Copyright (c) 2006, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4995931
  @summary java.awt.TextComponent caret position should be within the text bounds
  @key headful
*/

import java.awt.EventQueue;
import java.awt.TextField;

public class GetCaretPosOutOfBoundsTest {
    static TextField tf;
    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(() -> {
            tf = new TextField("1234567890");
            tf.setCaretPosition(100);
            int pos = tf.getCaretPosition();
            if (pos > 10) {
                throw new RuntimeException("Wrong caret position:" + pos + " instead of 10");
            }
            tf.setText("12345");
            if (tf.getCaretPosition() > 5) {
                throw new RuntimeException("Wrong caret position:" + pos + " instead of 5");
            }
        });
    }
}
