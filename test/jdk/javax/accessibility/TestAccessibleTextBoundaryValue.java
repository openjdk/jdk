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

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleText;

import javax.swing.JTextField;

/*
 * @test
 * @bug 8360070
 * @summary Verifies that AccesibleText's getBeforeIndex method doesn't return
 *          null for last character
 * @run main TestAccessibleTextBoundaryValue
 */

public class TestAccessibleTextBoundaryValue {
    public static void main(String[] args) throws Exception {
        JTextField tf = new JTextField("Test1 Test2 Test3. Test4 Test5. Test6");
        AccessibleContext ac = tf.getAccessibleContext();
        AccessibleText at = ac.getAccessibleText();

        if (at != null) {
            String text = tf.getText();
            int textLength = text.length();

            System.out.println("Text: \"" + text + "\"");
            System.out.println("Text Length: " + textLength);

            System.out.println("Call: getBeforeIndex(CHARACTER, " + textLength + ")");
            String result = at.getBeforeIndex(AccessibleText.CHARACTER, textLength);
            verifyResult("6", result);

            System.out.println("Call: getBeforeIndex(WORD, " + textLength + ")");
            result = at.getBeforeIndex(AccessibleText.WORD, textLength);
            verifyResult("Test6", result);

            System.out.println("Call: getBeforeIndex(SENTENCE, " + textLength + ")");
            result = at.getBeforeIndex(AccessibleText.SENTENCE, textLength);
            verifyResult("Test4 Test5. ", result);
        }
    }

    private static void verifyResult(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new RuntimeException("Result doesn't match: '" + actual
                                        + "' vs. '" + expected + "'");
        }
    }
}
