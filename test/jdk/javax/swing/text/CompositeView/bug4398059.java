/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Robot;
import java.awt.Shape;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.Position;
import javax.swing.text.StyleConstants;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.ParagraphView;

/*
 * @test
 * @bug 4398059
 * @key headful
 * @summary Tests that CompositeView doesn't throw NPE.
 */

public class bug4398059 {
    private static JFrame jFrame;

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            SwingUtilities.invokeAndWait(bug4398059::createAndShowUI);
            robot.waitForIdle();
            robot.delay(1000);
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (jFrame != null) {
                    jFrame.dispose();
                }
            });
        }
    }

    public static void createAndShowUI() {
        String text = "<H1>text";
        jFrame = new JFrame("CompositeView Test");
        JEditorPane jep = new JEditorPane();
        jep.setEditorKit(new MyHTMLEditorKit());
        jep.setText(text);

        Document doc = jep.getDocument();
        jep.setCaretPosition(doc.getLength() - 1);

        jFrame.getContentPane().add(jep);
        jFrame.setSize(200,200);
        jFrame.setVisible(true);
    }

    static class MyHTMLEditorKit extends HTMLEditorKit {
        private static final ViewFactory defaultFactory = new MyHTMLFactory();

        public ViewFactory getViewFactory() {
            return defaultFactory;
        }

        static class MyHTMLFactory extends HTMLEditorKit.HTMLFactory {
            public View create(Element elem) {
                Object obj = elem.getAttributes().getAttribute(StyleConstants.NameAttribute);
                if (obj instanceof HTML.Tag kind) {
                    if (kind == HTML.Tag.H1) {
                        return new MyParagraphView(elem);
                    }
                }
                return super.create(elem);
            }
        }

        static class MyParagraphView extends ParagraphView {
            public MyParagraphView(Element elem) {
                super(elem);
            }

            public Shape getChildAllocation(int index, Shape a) {
                return null;
            }

            public Shape modelToView(int pos, Shape a, Position.Bias b)
                                      throws BadLocationException {
                return super.modelToView(pos, a, b);
            }
        }
    }
}
