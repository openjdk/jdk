/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

import java.awt.EventQueue;
import java.util.Arrays;

import javax.swing.JPasswordField;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;
import javax.swing.text.PlainDocument;

/**
 * @test
 * @bug 8296878
 * @summary can the old password be accessed in the DocumentFilter
 */
public final class OldPasswordInDocumentFilter {

    public static void main(String[] args) throws Exception {
        EventQueue.invokeAndWait(OldPasswordInDocumentFilter::test);
    }

    private static void test() {
        JPasswordField test = new JPasswordField();
        PlainDocument document = (PlainDocument) test.getDocument();
        document.setDocumentFilter(new DocumentFilter() {
            @Override
            public void replace(FilterBypass fb, int offset,
                                int length, String text, AttributeSet attrs)
                    throws BadLocationException
            {
                Document doc = fb.getDocument();
                String string = doc.getText(0, doc.getLength()) + text;
                if (string.length() <= 6 && string.matches("[0-9]+")) {
                    super.replace(fb, offset, length, text, attrs);
                }
            }
        });
        test.setText("123456");
        test.setText("");

        char[] password = test.getPassword();
        if (password.length != 0) {
            throw new RuntimeException(Arrays.toString(password));
        }
    }
}
