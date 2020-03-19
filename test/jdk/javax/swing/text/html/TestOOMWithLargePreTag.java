/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8241078
 * @summary Tests OOM error parsing HTML with large <pre> Tag text
 * @run main/othervm -Xmx64M TestOOMWithLargePreTag
 */
import java.io.StringReader;
import javax.swing.text.html.HTMLEditorKit;

public class TestOOMWithLargePreTag {
    public static void main(String[] args) throws Exception {
        StringBuilder html = new StringBuilder();
        html.append("<html><body><pre>");
        for (int i = 0; i < 10_000; i++) {
            html.append("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
                .append("\n");
        }
        html.append("</pre></body></html>");

        HTMLEditorKit kit = new HTMLEditorKit();
        kit.read(new StringReader(html.toString()), kit.createDefaultDocument(), 0);
    }
}
