/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 4357975
 * @summary  Tests if InsertUnorderedListItem generates the proper tag sequence
 * @run main bug4357975
 */

import java.awt.event.ActionEvent;

import javax.swing.Action;
import javax.swing.JEditorPane;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.HTMLDocument;

public class bug4357975 {

    public static void main(String[] args) throws Exception {
        JEditorPane jep = new JEditorPane();
        HTMLEditorKit kit = new HTMLEditorKit();
        jep.setEditorKit(kit);
        jep.setDocument(kit.createDefaultDocument());

        HTMLDocument doc = (HTMLDocument) jep.getDocument();

        DocumentListener l = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                int offset = e.getOffset();
                HTMLDocument doc = (HTMLDocument)e.getDocument();

                Element el = doc.getCharacterElement(offset + 1);
                AttributeSet attrs = el.getAttributes();
                Object name = attrs.getAttribute(StyleConstants.NameAttribute);
                boolean passed = (name == HTML.Tag.CONTENT);

                el = el.getParentElement();
                attrs = el.getAttributes();
                name = attrs.getAttribute(StyleConstants.NameAttribute);
                passed = (passed && (name == HTML.Tag.IMPLIED));

                el = el.getParentElement();
                attrs = el.getAttributes();
                name = attrs.getAttribute(StyleConstants.NameAttribute);
                passed = (passed && (name == HTML.Tag.LI));

                el = el.getParentElement();
                attrs = el.getAttributes();
                name = attrs.getAttribute(StyleConstants.NameAttribute);
                passed = (passed && (name == HTML.Tag.UL));
                if (!passed) {
                    throw new RuntimeException("Test failed");
                }
            }

            @Override
            public void changedUpdate(DocumentEvent e) {}
            @Override
            public void removeUpdate(DocumentEvent e) {}
        };
        doc.addDocumentListener(l);

        Action[] actions = kit.getActions();
        for (int i = 0; i < actions.length; i++){
            Action a = actions[i];
            if (a.getValue(Action.NAME) == "InsertUnorderedListItem") {
                a.actionPerformed(new ActionEvent(jep,
                                                  ActionEvent.ACTION_PERFORMED,
                                                  (String) a.getValue(Action.ACTION_COMMAND_KEY)));
                break;
            }
        }

    }
}
