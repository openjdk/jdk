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
  @bug 4118247
  @summary Make sure bounds are enforced correctly on
           TextComponent.Select(int, int)
  @key headful
*/

import java.awt.EventQueue;
import java.awt.TextArea;
import java.awt.TextComponent;

public class SelectionBounds {
    public static TextComponent tc;

    public static int[][] index = {
        {0, 0},     // 0 = selectionStart = selectionEnd
        {5, 5},     // selectionStart = selectionEnd
        {5, 7},     // 0 < selectionStart < selectionEnd < textLength
        {-50, 7},   // selectionStart < 0 < selectionEnd < textLength
        {-50, 50},  // selectionStart < 0 < textLength < selectionEnd
        {5, 50},    // 0 < selectionStart < textLength < selectionEnd
        {40, 50},   // 0 < textLength < selectionStart < selectionEnd
        {-50, -40}, // selectionStart < selectionEnd < 0 < textLength
        {7, 5},     // 0 < selectionEnd < selectionStart < textLength
        {7, -50},   // selectionEnd < 0 < selectionStart < textLength
        {50, -50},  // selectionEnd < 0 < textLength < selectionStart
        {50, 5},    // 0 < selectionEnd < textLength < selectionStart
        {50, 40},   // 0 < textLength < selectionEnd < selectionStart
        {-40, -50}  // selectionEnd < selectionStart < 0 < textLength
    };

    public static String[] selections = {
        "",
        "",
        "56",
        "0123456",
        "0123456789",
        "56789",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        ""
    };


    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(() -> {
            tc = new TextArea("0123456789");
            runTheTest();
        });
    }

    private static void runTheTest() {
        int i;
        String str1;

        for (i=0; i<index.length; i++) {
            tc.select(index[i][0], index[i][1]);
            str1 = tc.getSelectedText();

            if (!str1.equals(selections[i])) {
                System.out.println("Test " + i + " FAILED:  " + str1 +
                    " != " + selections[i]);
                System.out.println("Test " + i + " FAILED:  " + str1 +
                    " != " + selections[i]);
                throw new RuntimeException("Test " + i + " FAILED:  " + str1 +
                    " != " + selections[i]);
            }
            else {
                System.out.println("Test " + i + " PASSED:  " + str1 +
                    " = " + selections[i]);
                System.out.println("Test " + i + " PASSED:  " + str1 +
                    " = " + selections[i]);
            }
        }

        System.out.println("\nAll tests PASSED.");
    }
}
