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
import javax.swing.text.JTextComponent;

public class AccessibleJTextAfterIndexTest {
    private static volatile JTextField jTextField;
    private static volatile JTextArea jTextArea;
    private static volatile JEditorPane jEditorPane;
    private static volatile String actualAccessibleText;

    public static void doTest() throws Exception {
        SwingUtilities.invokeAndWait(() -> createGUI());

        if (!checkAccessibleText(jTextField, AccessibleText.CHARACTER, 5,
            "T")) {
            throw new RuntimeException(
                "JTextField -" + "getAfterIndex() CHARACTER parameter"
                    + "expected: t, actual: " + actualAccessibleText);
        }
        if (!checkAccessibleText(jTextField, AccessibleText.WORD, 5, "Test2")) {
            throw new RuntimeException(
                "JTextField - " + "getAfterIndex() WORD parameter"
                    + "expected: Test2, actual: " + actualAccessibleText);
        }
        if (!checkAccessibleText(jTextField, AccessibleText.SENTENCE, 5,
            "Test4 Test5. ")) {
            throw new RuntimeException("JTextField - "
                + "getAfterIndex() SENTENCE parameter"
                + "expected: Test4 Test5. , actual: " + actualAccessibleText);
        }
        if (!checkAccessibleText(jTextArea, AccessibleText.CHARACTER, 5, "T")) {
            throw new RuntimeException(
                "JTextArea - " + "getAfterIndex() CHARACTER parameter"
                    + "expected: T , actual: " + actualAccessibleText);
        }
        if (!checkAccessibleText(jTextArea, AccessibleText.WORD, 5, "Test2")) {
            throw new RuntimeException(
                "JTextArea - " + "getAfterIndex() WORD parameter"
                    + "expected: Test2 , actual: " + actualAccessibleText);
        }
        if (!checkAccessibleText(jTextArea, AccessibleText.SENTENCE, 5,
            "Test4 Test5\n")) {
            throw new RuntimeException("JTextArea - "
                + "getAfterIndex() SENTENCE parameter"
                + "expected: Test4 Test5\n, actual: " + actualAccessibleText);
        }
        if (!checkAccessibleText(jEditorPane, AccessibleText.CHARACTER, 5,
            "T")) {
            throw new RuntimeException(
                "JTextArea - " + "getAfterIndex() CHARACTER parameter"
                    + "expected: T, actual: " + actualAccessibleText);
        }
        if (!checkAccessibleText(jEditorPane, AccessibleText.WORD, 5,
            "Test2")) {
            throw new RuntimeException(
                "JEditorPane - " + "getAfterIndex() WORD parameter"
                    + "expected: Test2, actual: " + actualAccessibleText);
        }
        if (!checkAccessibleText(jEditorPane, AccessibleText.SENTENCE, 5,
            "Test4 Test5\n")) {
            throw new RuntimeException("JEditorPane - "
                + "getAfterIndex() Sentence parameter"
                + "expected: Test4 Test5\n, actual: " + actualAccessibleText);
        }
    }

    private static void createGUI() {
        jTextField = new JTextField("Test1 Test2 Test3. Test4 Test5. Test6");
        jTextArea = new JTextArea("Test1 Test2 Test3.\nTest4 Test5");
        jEditorPane =
            new JEditorPane("text/plain", "Test1 Test2 Test3.\nTest4 Test5");
    }

    private static boolean checkAccessibleText(JTextComponent jTextComponent,
        int character, int characterPosition, String expectedString)
            throws Exception {
        SwingUtilities.invokeAndWait(() -> actualAccessibleText =
            jTextComponent.getAccessibleContext().getAccessibleText()
            .getAfterIndex(character, characterPosition));

        return actualAccessibleText.equals(expectedString);
    }

    public static void main(String[] args) throws Exception {
        doTest();
        System.out.println("Test Passed");
    }
}
