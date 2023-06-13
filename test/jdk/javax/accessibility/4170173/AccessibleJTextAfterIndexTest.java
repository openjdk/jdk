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
 * @summary AccessibleJTextComponent.getAfterIndex works incorrectly
 * @run main AccessibleJTextAfterIndexTest
 */

import javax.accessibility.AccessibleText;
import javax.swing.JEditorPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class AccessibleJTextAfterIndexTest {

    public static void doTest() {
        JTextField jTextField =
            new JTextField("Test1 Test2 Test3. Test4 Test5. Test6");
        JTextArea jTextArea = new JTextArea("Test1 Test2 Test3.\nTest4 Test5");
        JEditorPane jEditorPane =
            new JEditorPane("text/plain", "Test1 Test2 Test3.\nTest4 Test5");

        String actualAccessibleText = jTextField.getAccessibleContext()
            .getAccessibleText().getAfterIndex(AccessibleText.CHARACTER, 5);
        if (!(actualAccessibleText.equals("T"))) {
            throw new RuntimeException(
                "JTextField -" + "getAfterIndex() CHARACTER parameter"
                    + " expected:--T--, actual:--" + actualAccessibleText + "--");
        }

        actualAccessibleText = jTextField.getAccessibleContext()
            .getAccessibleText().getAfterIndex(AccessibleText.WORD, 5);
        if (!(actualAccessibleText.equals("Test2"))) {
            throw new RuntimeException(
                "JTextField - " + "getAfterIndex() WORD parameter"
                    + " expected:--Test2--, actual:--" + actualAccessibleText + "--");
        }

        actualAccessibleText = jTextField.getAccessibleContext()
            .getAccessibleText().getAfterIndex(AccessibleText.SENTENCE, 5);
        if (!(actualAccessibleText.equals("Test4 Test5. "))) {
            throw new RuntimeException("JTextField - "
                + "getAfterIndex() SENTENCE parameter"
                + " expected:--Test4 Test5. --, actual:--" + actualAccessibleText + "--");
        }

        actualAccessibleText = jTextArea.getAccessibleContext()
            .getAccessibleText().getAfterIndex(AccessibleText.CHARACTER, 5);
        if (!(actualAccessibleText.equals("T"))) {
            throw new RuntimeException(
                "JTextArea - " + "getAfterIndex() CHARACTER parameter"
                    + " expected:--T--, actual:--" + actualAccessibleText + "--");
        }

        actualAccessibleText = jTextArea.getAccessibleContext()
            .getAccessibleText().getAfterIndex(AccessibleText.WORD, 5);
        if (!(actualAccessibleText.equals("Test2"))) {
            throw new RuntimeException(
                "JTextArea - " + "getAfterIndex() WORD parameter"
                    + " expected:--Test2--, actual:--" + actualAccessibleText + "--");
        }

        actualAccessibleText = jTextArea.getAccessibleContext()
            .getAccessibleText().getAfterIndex(AccessibleText.SENTENCE, 5);
        if (!(actualAccessibleText.equals("Test4 Test5\n"))) {
            throw new RuntimeException("JTextArea - "
                + "getAfterIndex() SENTENCE parameter"
                + " expected:--Test4 Test5\n--, actual:--" + actualAccessibleText + "--");
        }

        actualAccessibleText = jEditorPane.getAccessibleContext()
            .getAccessibleText().getAfterIndex(AccessibleText.CHARACTER, 5);
        if (!(actualAccessibleText.equals("T"))) {
            throw new RuntimeException(
                "JEditorPane - " + "getAfterIndex() CHARACTER parameter"
                    + " expected:--T--, actual:--" + actualAccessibleText +"--");
        }

        actualAccessibleText = jEditorPane.getAccessibleContext()
            .getAccessibleText().getAfterIndex(AccessibleText.WORD, 5);
        if (!(actualAccessibleText.equals("Test2"))) {
            throw new RuntimeException(
                "JEditorPane - " + "getAfterIndex() WORD parameter"
                    + " expected:--Test2--, actual:--" + actualAccessibleText + "--");
        }

        actualAccessibleText = jEditorPane.getAccessibleContext()
            .getAccessibleText().getAfterIndex(AccessibleText.SENTENCE, 5);
        if (!(actualAccessibleText.equals("Test4 Test5\n"))) {
            throw new RuntimeException("JEditorPane - "
                + "getAfterIndex() Sentence parameter"
                + " expected:--Test4 Test5\n--, actual:--" + actualAccessibleText +"--");
        }
    }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> doTest());
        System.out.println("Test Passed");
    }
}
