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

import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.html.FormView;

/*
 * @test
 * @bug 4210307 4210308
 * @summary Tests that FormView button text is internationalized
 */

public class bug4210307 {
    private static final String RESET_PROPERTY = "TEST RESET";
    private static final String SUBMIT_PROPERTY = "TEST SUBMIT";

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Object oldReset = UIManager.put("FormView.resetButtonText",
                    RESET_PROPERTY);
            Object oldSubmit = UIManager.put("FormView.submitButtonText",
                    SUBMIT_PROPERTY);

            try {
                JEditorPane ep = new JEditorPane("text/html",
                        "<html><input type=\"submit\"></html>");
                Document doc = ep.getDocument();
                Element elem = findInputElement(doc.getDefaultRootElement());
                TestView view = new TestView(elem);
                view.test(SUBMIT_PROPERTY);

                ep = new JEditorPane("text/html",
                        "<html><input type=\"reset\"></html>");
                doc = ep.getDocument();
                elem = findInputElement(doc.getDefaultRootElement());
                view = new TestView(elem);
                view.test(RESET_PROPERTY);
            } finally {
                UIManager.put("FormView.resetButtonText", oldReset);
                UIManager.put("FormView.submitButtonText", oldSubmit);
            }
        });
    }

    private static Element findInputElement(Element root) {
        for (int i = 0; i < root.getElementCount(); i++) {
            Element elem = root.getElement(i);
            if (elem.getName().equals("input")) {
                return elem;
            } else {
                Element e = findInputElement(elem);
                if (e != null) return e;
            }
        }
        return null;
    }

    static class TestView extends FormView {
        public TestView(Element elem) {
            super(elem);
        }

        public void test(String caption) {
            JButton comp = (JButton) createComponent();
            if (!comp.getText().equals(caption)) {
                throw new RuntimeException("Failed: '" + comp.getText() +
                        "' instead of `" + caption + "'");
            }
        }
    }
}
