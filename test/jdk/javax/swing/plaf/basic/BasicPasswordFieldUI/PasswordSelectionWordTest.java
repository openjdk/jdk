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

/**
 * @test
 * @key headful
 * @bug 4231444 8354646
 * @summary Password fields' ActionMap needs to replace
 *          DefaultEditorKit.selectWordAction with
 *          DefaultEditorKit.selectLineAction.
 *
 * @run main PasswordSelectionWordTest
 */

import javax.swing.Action;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.plaf.basic.BasicTextUI;
import javax.swing.text.DefaultEditorKit;
import java.awt.event.ActionEvent;

public class PasswordSelectionWordTest {
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
        } catch (UnsupportedLookAndFeelException  e) {
            System.err.println("Skipping unsupported look and feel:");
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void runTest() {
        String str = "one two three";
        JPasswordField field = new JPasswordField(str);
        if (!(field.getUI() instanceof BasicTextUI)) {
            throw new RuntimeException("Unexpected condition: JPasswordField UI was " + field.getUI());
        }
        System.out.println("Testing " + field.getUI());

        // do something (anything) to initialize the Views:
        field.setSize(100, 100);
        field.addNotify();

        Action action = field.getActionMap().get(
                DefaultEditorKit.selectWordAction);
        action.actionPerformed(new ActionEvent(field, 0, ""));
        int selectionStart = field.getSelectionStart();
        int selectionEnd = field.getSelectionEnd();
        System.out.println("selectionStart = " + selectionStart);
        System.out.println("selectionEnd = " + selectionEnd);
        if (selectionStart != 0 || selectionEnd != str.length()) {
            throw new RuntimeException("selectionStart = " + selectionStart +
                    " and selectionEnd = " + selectionEnd);
        }
    }
}
