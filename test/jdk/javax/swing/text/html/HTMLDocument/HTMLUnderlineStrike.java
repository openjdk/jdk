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

import java.io.StringReader;

import javax.swing.text.AttributeSet;
import javax.swing.text.Element;
import javax.swing.text.html.CSS;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

/*
 * @test
 * @bug 8323801 8326734
 * @summary Tests that '<u><s>' produce underlined and struck-through text
 */
public final class HTMLUnderlineStrike {
    private static final String HTML = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <title>Strike-through text</title>
            </head>
            <body>
            <p><u><s>struck?</s></u></p>
            <p><span style='text-decoration: underline'><s>struck?</s></span></p>

            <p><u><strike>struck?</strike></u></p>
            <p><span style='text-decoration: underline'><strike>struck?</strike></span></p>
            </body>
            </html>
            """;

    public static void main(String[] args) throws Exception {
        HTMLEditorKit kit = new HTMLEditorKit();
        HTMLDocument doc = new HTMLDocument();

        try (StringReader reader = new StringReader(HTML)) {
            kit.read(reader, doc, 0);
        }

        StringBuilder errors = new StringBuilder();

        Element root = doc.getDefaultRootElement();
        Element body = root.getElement(1);
        for (int i = 0; i < body.getElementCount(); i++) {
            Element p = body.getElement(i);
            Element content = p.getElement(0);
            AttributeSet attr = content.getAttributes();
            Object decoration = attr.getAttribute(CSS.Attribute.TEXT_DECORATION);
            String strDecoration = decoration.toString();
            System.out.println(i + ": " + decoration);
            if (!strDecoration.contains("line-through")
                || !strDecoration.contains("underline")) {
                errors.append("<p>[")
                      .append(i)
                      .append("], ");
            }
        }

        if (!errors.isEmpty()) {
            errors.delete(errors.length() - 2, errors.length());
            throw new RuntimeException(errors + " must have both "
                                       + "'line-through' and 'underline' in "
                                       + "'text-decoration'");
        }
    }
}
