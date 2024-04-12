/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.text.AttributeSet;
import javax.swing.text.View;
import javax.swing.text.html.CSS;
import javax.swing.text.html.HTMLEditorKit;

/*
 * @test
 * @bug 4687405
 * @summary  Tests if HTMLDocument very first paragraph doesn't have top margin.
 */

public class bug4687405 {
    private static JEditorPane jep;
    private static volatile boolean passed = false;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(bug4687405::createHTMLEditor);
        Thread.sleep(200);

        SwingUtilities.invokeAndWait(bug4687405::testEditorPane);
        Thread.sleep(500);

        if (!passed) {
            throw new RuntimeException("Test failed!!" +
                    " Top margin present in HTMLDocument");
        }
    }

    public static void createHTMLEditor() {
        jep = new JEditorPane();
        jep.setEditorKit(new HTMLEditorKit());
        jep.setEditable(false);
    }

    private static void testEditorPane() {
        View v = jep.getUI().getRootView(jep);
        while (!(v instanceof javax.swing.text.html.ParagraphView)) {
            int n = v.getViewCount();
            v = v.getView(n - 1);
        }
        AttributeSet attrs = v.getAttributes();
        String marginTop = attrs.getAttribute(CSS.Attribute.MARGIN_TOP).toString();
        // MARGIN_TOP of the very first paragraph of the default html
        // document should be 0.
        passed = "0".equals(marginTop);
    }
}
