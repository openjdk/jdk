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
 * @bug 8354646
 * @summary The AquaTextPasswordFieldUI's ActionMap needs to replace
 * DefaultEditorKit.selectLineAction with DefaultEditorKit.selectWordAction.
 * When we failed to do this: the user could double-click words and
 * identify spaces in the password.
 *
 * @requires (os.family == "mac")
 * @run main PasswordSelectionWordTest
 */

import javax.swing.Action;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import javax.swing.text.DefaultEditorKit;
import java.awt.event.ActionEvent;

public class PasswordSelectionWordTest {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            String str = "one two three";
            JPasswordField field = new JPasswordField(str);

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
                throw new Error("selectionStart = " + selectionStart +
                        " and selectionEnd = " + selectionEnd);
            }
        });
    }
}
