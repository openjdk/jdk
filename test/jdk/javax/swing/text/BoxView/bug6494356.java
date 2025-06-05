/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 6494356
 * @key headful
 * @summary Test that BoxView.layout() is not called with negative arguments
 * @run main bug6494356
 */

import java.awt.Dimension;
import java.awt.Toolkit;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.ParagraphView;

public class bug6494356 {
    static JEditorPane ep;
    private static final CountDownLatch latch = new CountDownLatch(1);

    public static void main(final String[] args) throws Exception {
        final Path file = Path.of("bug6494356.html");
        try (Writer writer = Files.newBufferedWriter(file)) {
            writer.write("<p>Paragraph</p>");
        }
        try {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    ep = new JEditorPane();
                    ep.setEditorKitForContentType("text/html", new MyEditorKit());
                    ep.addPropertyChangeListener("page", new PropertyChangeListener() {
                        public void propertyChange(PropertyChangeEvent pce) {
                            if (pce.getPropertyName().equals("page")) {
                                latch.countDown();
                            }
                        }
                    });
                    JFrame f = new JFrame();
                    f.setTitle("6494356");
                    f.setSize(new Dimension(
                        Toolkit.getDefaultToolkit().getScreenSize().width, 600));
                    f.setContentPane(ep);
                    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    f.setVisible(true);
                    try {
                        ep.setPage("file:" + file);
                    } catch (Exception ex) {
                        testPassed = false;
                        throw new RuntimeException(ex);
                    }
                }
            });

            latch.await();
            if (!testPassed) {
                throw new RuntimeException("test failed.");
            }
        } finally {
            Files.delete(file);
        }
        System.out.println("6494356 OK");
    }

    static volatile boolean testPassed = true;

    static class MyEditorKit extends HTMLEditorKit {
        static class MyViewFactory extends HTMLFactory {
            public View create(Element elem) {
                HTML.Tag tag = (HTML.Tag) elem.getAttributes().getAttribute(
                        StyleConstants.NameAttribute);
                if ((tag != null) && (tag == HTML.Tag.P)) {
                    return new MyParagraphView(elem);
                } else {
                    return super.create(elem);
                }
            }

            static class MyParagraphView extends ParagraphView {
                MyParagraphView(Element elem) {
                    super(elem);
                }

                protected void layout(int width, int height) {
                    if ((width < 0) || (height < 0)) {
                        testPassed = false;
                        throw new RuntimeException("w=" + width + " h=" + height);
                    }
                    super.layout(width, height);
                }
            }

        }

        final ViewFactory viewFactory = new MyViewFactory();

        public ViewFactory getViewFactory() {
            return viewFactory;
        }
    }

}

