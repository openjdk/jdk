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

import java.awt.event.ActionEvent;
import javax.swing.Action;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;

/*
 * @test
 * @bug 4251593
 * @summary Tests that hyperlinks can be inserted into JTextPane
 *          via InsertHTMLTextAction.
 */

public class bug4251593 {
    private static JTextPane editor;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            editor = new JTextPane();
            editor.setContentType("text/html");
            editor.setEditable(true);

            int beforeLen = editor.getDocument().getLength();

            String href = "<a HREF=\"https://java.sun.com\">javasoft </a>";
            Action a = new HTMLEditorKit.InsertHTMLTextAction("Tester", href, HTML.Tag.BODY, HTML.Tag.A);
            a.actionPerformed(new ActionEvent(editor, 0, null));

            int afterLen = editor.getDocument().getLength();
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if ((afterLen - beforeLen) < 8) {
                throw new RuntimeException("Test Failed: link not inserted!!");
            }
        });
    }
}
