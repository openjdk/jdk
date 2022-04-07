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
import javax.swing.text.JTextComponent;

public class AccessibleJTextBeforeIndexTest {
    private static volatile JTextField jTextField;
    private static volatile JTextArea jTextArea;
    private static volatile JEditorPane jEditorPane;
    private static volatile String actualAccessibleText;

    public static void doTest() throws Exception {
        SwingUtilities.invokeAndWait(() -> createGUI());

        if (!checkAccessibleText(jTextField, AccessibleText.CHARACTER, 5,
            "1")) {
            throw new RuntimeException(
                "JTextField -" + "getBeforeIndex() CHARACTER parameter"
                    + "expected: 1, actual: " + actualAccessibleText);
        }
        if (!checkAccessibleText(jTextField, AccessibleText.WORD, 5, "Test1")) {
            throw new RuntimeException(
                "JTextField - " + "getBeforeIndex() WORD parameter"
                    + "expected: Test1, actual: " + actualAccessibleText);
        }
        if (!checkAccessibleText(jTextField, AccessibleText.SENTENCE, 20,
            "Test1 Test2 Test3. ")) {
            throw new RuntimeException(
                "JTextField - " + "getBeforeIndex() SENTENCE parameter"
                    + "expected: Test1 Test2 Test3. , " + "actual: "
                    + actualAccessibleText);
        }
        if (!checkAccessibleText(jTextArea, AccessibleText.CHARACTER, 5, "1")) {
            throw new RuntimeException(
                "JTextArea - " + "getBeforeIndex() CHARACTER parameter"
                    + "expected: 1 , actual: " + actualAccessibleText);
        }
        if (!checkAccessibleText(jTextArea, AccessibleText.WORD, 5, "Test1")) {
            throw new RuntimeException(
                "JTextArea - " + "getBeforeIndex() WORD parameter"
                    + "expected: Test1 , actual: " + actualAccessibleText);
        }
        if (!checkAccessibleText(jTextArea, AccessibleText.SENTENCE, 20,
            "Test1 Test2 Test3.\n")) {
            throw new RuntimeException(
                "JTextArea - " + "getBeforeIndex() SENTENCE parameter"
                    + "expected: Test1 Test2 Test3.\n, actual: "
                    + actualAccessibleText);
        }
        if (!checkAccessibleText(jEditorPane, AccessibleText.CHARACTER, 5,
            "1")) {
            throw new RuntimeException(
                "JTextArea - " + "getBeforeIndex() CHARACTER parameter"
                    + "expected: 1, actual: " + actualAccessibleText);
        }
        if (!checkAccessibleText(jEditorPane, AccessibleText.WORD, 5,
            "Test1")) {
            throw new RuntimeException(
                "JEditorPane - " + "getBeforeIndex() WORD parameter"
                    + "expected: Test1, actual: " + actualAccessibleText);
        }
        if (!checkAccessibleText(jEditorPane, AccessibleText.SENTENCE, 20,
            "Test1 Test2 Test3.\n")) {
            throw new RuntimeException(
                "JEditorPane - " + "getBeforeIndex() Sentence parameter"
                    + "expected: Test1 Test2 Test3.\n, actual: "
                    + actualAccessibleText);
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
            .getBeforeIndex(character, characterPosition));

        return actualAccessibleText.equals(expectedString);
    }

    public static void main(String[] args) throws Exception {
        doTest();
        System.out.println("Test Passed");
    }
}
