/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;

import javax.swing.JEditorPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;

/*
 * @test
 * @bug 8328953
 * @summary Verifies JEditorPane.read doesn't throw ChangedCharSetException
            but handles it and reads HTML in the specified encoding
 * @run main EditorPaneCharset
 */

public final class EditorPaneCharset {
    private static final String CYRILLIC_TEXT =
            "\u041F\u0440\u0438\u0432\u0435\u0442, \u043C\u0438\u0440!";
    private static final String HTML_CYRILLIC =
            "<html lang=\"ru\">\n" +
            "<head>\n" +
            "    <meta http-equiv=\"Content-Type\" " +
            "          content=\"text/html; charset=windows-1251\">\n" +
            "</head><body>\n" +
            "<p>" + CYRILLIC_TEXT + "</p>\n" +
            "</body></html>\n";

    public static void main(String[] args) throws IOException, BadLocationException {
        JEditorPane editorPane = new JEditorPane();
        editorPane.setContentType("text/html");
        Document document = editorPane.getDocument();

        // Shouldn't throw ChangedCharSetException
        editorPane.read(
                new ByteArrayInputStream(
                        HTML_CYRILLIC.getBytes(
                                Charset.forName("windows-1251"))),
                document);

        Element root = document.getDefaultRootElement();
        Element body = root.getElement(1);
        Element p = body.getElement(0);
        String pText = document.getText(p.getStartOffset(),
                                        p.getEndOffset() - p.getStartOffset() - 1);
        if (!CYRILLIC_TEXT.equals(pText)) {
            throw new RuntimeException("Text doesn't match");
        }
    }
}
