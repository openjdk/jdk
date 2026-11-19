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

/*
 * @test
 * @key headful
 * @bug 8358813
 * @summary Password fields' InputMap should not include any word-related action.
 *
 * @run main PasswordFieldInputMapWordTest
 */

import java.util.Collection;
import java.util.Set;

import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JPasswordField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.text.DefaultEditorKit;

public class PasswordFieldInputMapWordTest {
    public static void main(String[] args) throws Exception {
        for (UIManager.LookAndFeelInfo laf :
                UIManager.getInstalledLookAndFeels()) {
            System.out.println("Testing LAF: " + laf.getClassName());
            SwingUtilities.invokeAndWait(() -> {
                if (setLookAndFeel(laf)) {
                    runTest();
                }
            });
        }
    }

    private static boolean setLookAndFeel(UIManager.LookAndFeelInfo laf) {
        try {
            UIManager.setLookAndFeel(laf.getClassName());
            return true;
        } catch (UnsupportedLookAndFeelException e) {
            System.err.println("Skipping unsupported look and feel:");
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static int[] inputMapConditions = new int[] {
            JComponent.WHEN_IN_FOCUSED_WINDOW,
            JComponent.WHEN_FOCUSED,
            JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
    };

    /**
     * These are all the actions with "word" in their field name.
     */
    static Collection<String> wordActions = Set.of(
            DefaultEditorKit.deleteNextWordAction,
            DefaultEditorKit.deletePrevWordAction,
            DefaultEditorKit.beginWordAction,
            DefaultEditorKit.endWordAction,
            DefaultEditorKit.selectionBeginWordAction,
            DefaultEditorKit.selectionEndWordAction,
            DefaultEditorKit.previousWordAction,
            DefaultEditorKit.nextWordAction,
            DefaultEditorKit.selectionPreviousWordAction,
            DefaultEditorKit.selectionNextWordAction
    );

    private static void runTest() {
        JPasswordField field = new JPasswordField();

        boolean testPassed = true;
        for (int condition : inputMapConditions) {
            InputMap inputMap = field.getInputMap(condition);
            if (inputMap.allKeys() == null) {
                continue;
            }
            for (KeyStroke keyStroke : inputMap.allKeys()) {
                Object actionBinding = inputMap.get(keyStroke);
                if (wordActions.contains(actionBinding)) {
                    if (testPassed) {
                        System.err.println("The following inputs/actions should not be available in a JPasswordField:");
                    }
                    System.err.println(inputMap.get(keyStroke) + " (try typing " + keyStroke + ")");
                    testPassed = false;
                }
            }
        }

        if (!testPassed) {
            throw new RuntimeException("One or more input/action binding was observed for a JPasswordField.");
        }
    }
}
