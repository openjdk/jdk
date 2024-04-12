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

import java.io.StringReader;
import java.io.StringWriter;
import javax.swing.text.Document;
import javax.swing.text.html.HTMLEditorKit;

/*
 * @test
 * @bug 4214848
 * @summary Tests whether  HTMLEditorKit.read(...)
 *          creates Document for html with empty BODY
 */

public class bug4214848 {
    public static void main (String[] args) throws Exception {
        StringWriter sw = new StringWriter();
        String test = "<HTML><BODY></BODY></HTML>";
        HTMLEditorKit kit = new HTMLEditorKit();
        Document doc = kit.createDefaultDocument();
        kit.read(new StringReader(test), doc, 0); // prepare test document
        kit.write(sw, doc, 0, 10);
        String out = sw.toString().toLowerCase();
        if (out.indexOf("<body>") != out.lastIndexOf("<body>")) {
            throw new RuntimeException("Test failed: extra <body> section generated");
        }
    }
}
