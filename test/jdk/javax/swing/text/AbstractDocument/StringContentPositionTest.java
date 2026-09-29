/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.text.BadLocationException;
import javax.swing.text.Position;
import javax.swing.text.StringContent;

/*
 * @test
 * @summary test that StringContent Position APIs behave as expected.
 */

public class StringContentPositionTest {

    static final int SIZE = 20;
    static final String TEXT = "hello";
    static final int LEN = TEXT.length();
    static final StringContent SC = new StringContent();

    public static void main(String[] args) throws BadLocationException {

        for (int i = 0; i < 1000; i++) {
            test();
            System.gc();
        }
    }

    static void test() throws BadLocationException {

        Position[] positions = new Position[SIZE];

        for (int i = 0; i < SIZE; i++) {
            SC.insertString(0, TEXT);
            positions[i] = SC.createPosition(LEN);
        }
        for (int i = 0; i < SIZE; i++) {
           int expected = ((SIZE - i) * LEN);
           if (positions[i].getOffset() != expected) {
               throw new RuntimeException("insert: Bad offset i=" + i + " off=" + positions[i].getOffset());
           }
        }
        SC.remove(0, SIZE * LEN);
        for (int i = 0; i < SIZE; i++) {
            if (positions[i].getOffset() != 0) {
               throw new RuntimeException("remove: Bad offset i=" + i + " off=" + positions[i].getOffset());
            }
        }
     }
}
