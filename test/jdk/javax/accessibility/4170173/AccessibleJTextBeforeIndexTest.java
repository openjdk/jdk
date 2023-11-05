/*
 * Copyright (c) 2010, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @key headful
 * @bug 4170173
 * @summary AccessibleJTextComponent.getBeforeIndex works incorrectly
 * @run main AccessibleJTextBeforeIndexTest
 */

import javax.accessibility.AccessibleText;
import javax.swing.JEditorPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class AccessibleJTextBeforeIndexTest {

    public static void doTest() {
        JTextField jTextField =
            new JTextField("Test1 Test2 Test3. Test4 Test5. Test6");
        JTextArea jTextArea = new JTextArea("Test1 Test2 Test3.\nTest4 Test5");
        JEditorPane jEditorPane =
            new JEditorPane("text/plain", "Test1 Test2 Test3.\nTest4 Test5");

        String actualAccessibleText = jTextField.getAccessibleContext()
            .getAccessibleText().getBeforeIndex(AccessibleText.CHARACTER, 5);
        if (!(actualAccessibleText.equals("1"))) {
            throw new RuntimeException(
                "JTextField -" + "getBeforeIndex() CHARACTER parameter"
                    + " expected:--1--, actual:--" + actualAccessibleText + "--");
        }

        actualAccessibleText = jTextField.getAccessibleContext()
            .getAccessibleText().getBeforeIndex(AccessibleText.WORD, 5);
        if (!(actualAccessibleText.equals("Test1"))) {
            throw new RuntimeException(
                "JTextField -" + "getBeforeIndex() WORD parameter"
                    + " expected:--Test1--, actual:--" + actualAccessibleText + "--");
        }

        actualAccessibleText = jTextField.getAccessibleContext()
            .getAccessibleText().getBeforeIndex(AccessibleText.SENTENCE, 20);
        if (!(actualAccessibleText.equals("Test1 Test2 Test3. "))) {
            throw new RuntimeException(
                "JTextField -" + "getBeforeIndex() SENTENCE parameter"
                    + " expected:--Test1 Test2 Test3. --, actual:--"
                    + actualAccessibleText + "--");
        }

        actualAccessibleText = jTextArea.getAccessibleContext()
            .getAccessibleText().getBeforeIndex(AccessibleText.CHARACTER, 5);
        if (!(actualAccessibleText.equals("1"))) {
            throw new RuntimeException(
                "JTextArea -" + "getBeforeIndex() CHARACTER parameter"
                    + " expected:--1--, actual:--" + actualAccessibleText + "--");
        }

        actualAccessibleText = jTextArea.getAccessibleContext()
            .getAccessibleText().getBeforeIndex(AccessibleText.WORD, 5);
        if (!(actualAccessibleText.equals("Test1"))) {
            throw new RuntimeException("JTextArea -"
                + "getBeforeIndex() WORD parameter"
                + " expected:--Test1--, actual:--" + actualAccessibleText + "--");
        }

        actualAccessibleText = jTextArea.getAccessibleContext()
            .getAccessibleText().getBeforeIndex(AccessibleText.SENTENCE, 20);
        if (!(actualAccessibleText.equals("Test1 Test2 Test3.\n"))) {
            throw new RuntimeException(
                "JTextArea -" + "getBeforeIndex() SENTENCE parameter"
                    + " expected: Test1 Test2 Test3.\n--, actual:--"
                    + actualAccessibleText + "--");
        }

        actualAccessibleText = jEditorPane.getAccessibleContext()
            .getAccessibleText().getBeforeIndex(AccessibleText.CHARACTER, 5);
        if (!(actualAccessibleText.equals("1"))) {
            throw new RuntimeException(
                "JEditorPane -" + "getBeforeIndex() CHARACTER parameter"
                    + " expected:--1--, actual:--" + actualAccessibleText + "--");
        }

        actualAccessibleText = jEditorPane.getAccessibleContext()
            .getAccessibleText().getBeforeIndex(AccessibleText.WORD, 5);
        if (!(actualAccessibleText.equals("Test1"))) {
            throw new RuntimeException(
                "JEditorPane -" + "getBeforeIndex() WORD parameter"
                    + " expected:--Test1--, actual:--" + actualAccessibleText + "--");
        }

        actualAccessibleText = jEditorPane.getAccessibleContext()
            .getAccessibleText().getBeforeIndex(AccessibleText.SENTENCE, 20);
        if (!(actualAccessibleText.equals("Test1 Test2 Test3.\n"))) {
            throw new RuntimeException(
                "JEditorPane -" + "getBeforeIndex() SENTENCE parameter"
                    + " expected:--Test1 Test2 Test3.\n--, actual:--"
                    + actualAccessibleText + "--");
        }
    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> doTest());
        System.out.println("Test Passed");
    }
}
