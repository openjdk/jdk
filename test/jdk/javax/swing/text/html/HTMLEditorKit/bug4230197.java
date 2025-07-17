/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.StringWriter;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

/*
 * @test
 * @bug 4230197
 * @summary Tests if HTMLEditorKit.insertHTML() works for font/phrase tags
 */

public class bug4230197 {

    public static void main(String[] args) throws Exception {
        HTMLEditorKit kit = new HTMLEditorKit();
        StringWriter sw = new StringWriter();
        HTMLDocument doc = (HTMLDocument) kit.createDefaultDocument();
        kit.insertHTML(doc, doc.getLength(), "<sub>0</sub>", 0, 0, HTML.Tag.SUB);
        kit.insertHTML(doc, doc.getLength(), "<sup>0</sup>", 0, 0, HTML.Tag.SUP);
        kit.insertHTML(doc, doc.getLength(), "<b>0</b>", 0, 0, HTML.Tag.B);
        kit.insertHTML(doc, doc.getLength(), "<i>0</i>", 0, 0, HTML.Tag.I);
        kit.insertHTML(doc, doc.getLength(), "<code>0</code>", 0, 0, HTML.Tag.CODE);
        kit.write(sw, doc, 0, doc.getLength());

        String out = sw.toString().toLowerCase();
        if ((!out.contains("<sub>0</sub>"))
                || (!out.contains("<sup>0</sup>"))
                || (!out.contains("<code>0</code>"))
                || (!out.contains("<b>0</b>"))
                || (!out.contains("<i>0</i>"))) {
            throw new RuntimeException("Test failed: HTMLEditorKit.insertHTML()" +
                    " doesn't work for font/phrase tags");
        }
    }
}
