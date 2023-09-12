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

import javax.swing.Action;
import javax.swing.JEditorPane;
import javax.swing.text.AttributeSet;
import javax.swing.text.Caret;
import javax.swing.text.Element;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.event.ActionEvent;
import java.io.StringReader;

/*
 * @test
 * @bug 4253334
 * @summary Tests that bold attribute unsets properly
 */

public class bug4253334 {

    public static void main(String[] args) throws Exception {
        JEditorPane ep = new JEditorPane();
        ep.setEditable(true);
        ep.setContentType("text/html");

        HTMLEditorKit kit = (HTMLEditorKit)ep.getEditorKit();
        HTMLDocument doc = (HTMLDocument)kit.createDefaultDocument();
        ep.setDocument(doc);
        String text = "<html><body>somesampletext</body></html>";
        kit.read(new StringReader(text), doc, 0);

        // make some text bold & italic
        MutableAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setBold(attrs, true);
        StyleConstants.setItalic(attrs, true);
        doc.setCharacterAttributes(3, 9, attrs, false);

        Action[] as = kit.getActions();
        Action boldAction = null;

        for (Action a : as) {
            String s = (String) (a.getValue(Action.NAME));
            if (s.equals("font-bold")) {
                boldAction = a;
            }
        }
        Caret caret = ep.getCaret();
        ActionEvent event = new ActionEvent(ep, ActionEvent.ACTION_PERFORMED,
                                            "font-bold");
        caret.setDot(3);
        caret.moveDot(7);
        boldAction.actionPerformed(event);
        caret.setDot(7);
        caret.moveDot(12);
        boldAction.actionPerformed(event);

        Element elem = doc.getCharacterElement(9);
        AttributeSet at = elem.getAttributes();
        if (StyleConstants.isBold(at)) {
            throw new RuntimeException("Test Failed: bold attribute set");
        }
    }
}
