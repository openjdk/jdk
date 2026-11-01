/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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


import javax.accessibility.AccessibleHypertext;
import javax.swing.JTextPane;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Document;
import javax.swing.text.html.HTMLDocument;

/*
 * @test
 * @bug 8380790
 * @summary make sure getAccessibleText() doesn't add DocumentListeners
 * @run main GetAccessibleTextAddsDocumentListeners testOriginalComplaint
 * @run main GetAccessibleTextAddsDocumentListeners testSetNewHTMLDocument
 * @run main GetAccessibleTextAddsDocumentListeners testSetExistingHTMLDocument
 * @run main GetAccessibleTextAddsDocumentListeners testDocumentListeners
 */

public class GetAccessibleTextAddsDocumentListeners {
    public static void main(String[] args) throws Exception {
        GetAccessibleTextAddsDocumentListeners.class.
                getMethod(args[0]).invoke(null);
    }

    public static void testOriginalComplaint() throws Exception {
        JTextPane textPane = new JTextPane();
        textPane.setContentType("text/html");
        for (int a = 0; a < 10_000; a++) {
            textPane.getAccessibleContext().getAccessibleText();
        }
        HTMLDocument doc = (HTMLDocument) textPane.getDocument();
        if (doc.getDocumentListeners().length > 1000) {
            throw new Exception("too many DocumentListeners");
        }
    }

    /**
     * This makes sure getAccessibleText().getLinkCount() is based on the
     * current Document (instead of based on an old/stale Document).
     *
     * see https://github.com/openjdk/jdk/pull/30401#issuecomment-4144874584
     */
    public static void testSetNewHTMLDocument() throws Exception {
        JTextPane textPane = new JTextPane();
        textPane.setContentType("text/html");

        // test some baseline expectations:
        testLinkCount(textPane);
        // now change the document
        textPane.setDocument(new HTMLDocument());

        testLinkCount(textPane);
    }

    /**
     * Test hyperlink count after calling `p.setDocument(p.getDocument());`
     */
    public static void testSetExistingHTMLDocument() throws Exception {
        JTextPane textPane = new JTextPane();
        textPane.setContentType("text/html");
        testLinkCount(textPane);

        textPane.setDocument(textPane.getDocument());
        testLinkCount(textPane);
    }

    /**
     * This tests AccessibleHypertext.getLinkCount() when a text pane is given
     * 0, 1, and 2 link tags.
     *
     * By calling `getAccessibleText().getLinkCount()` we also trigger code
     * that installs listeners in the JTextPane.
     */
    private static void testLinkCount(JTextPane textPane) throws Exception {
        textPane.setText("");
        assertEquals(0, ((AccessibleHypertext) textPane.
                getAccessibleContext().getAccessibleText()).getLinkCount());

        textPane.setText("<a href=\"x\">y</a>");
        assertEquals(1, ((AccessibleHypertext) textPane.
                getAccessibleContext().getAccessibleText()).getLinkCount());

        textPane.setText("<a href=\"x\">y</a> <a href=\"x\">y</a>");
        assertEquals(2, ((AccessibleHypertext) textPane.
                getAccessibleContext().getAccessibleText()).getLinkCount());
    }

    /**
     * This switches between a DefaultStyledDocument and an HTMLDocument
     * several times and tests whether we ended up with too many
     * DocumentListeners
     *
     * see https://github.com/openjdk/jdk/pull/30401#discussion_r3025612299
     */
    public static void testDocumentListeners() throws Exception {
        JTextPane textPane = new JTextPane();

        // each call to setContentType replaces textPane.getDocument()
        textPane.setContentType("text/html");

        for (int a = 0; a < 100; a++) {
            textPane.setContentType("text/plain");
            assertTrue(!(textPane.getAccessibleContext().getAccessibleText()
                    instanceof AccessibleHypertext));

            textPane.setContentType("text/html");
            testLinkCount(textPane);
        }

        int docListenerCount = log("testDocumentListeners_simpleCase",
                textPane.getDocument());
        assertTrue(docListenerCount < 10);
    }

    private static void assertEquals(int expected, int actual)
            throws Exception {
        if (expected != actual) {
            throw new Exception("expected: " + expected + ", actual: " + actual);
        }
    }

    private static void assertTrue(boolean b) throws Exception {
        if (!b) {
            throw new Exception("expected: true, actual: false");
        }
    }

    /**
     * This returns the number of DocumentListeners, and it writes them
     * to System.out.
     */
    private static int log(String name, Document doc) {
        DocumentListener[] docListeners;
        if (doc instanceof HTMLDocument) {
            HTMLDocument htmlDoc = (HTMLDocument) doc;
            docListeners = htmlDoc.getDocumentListeners();
        } else {
            DefaultStyledDocument styledDoc = (DefaultStyledDocument) doc;
            docListeners = styledDoc.getDocumentListeners();
        }

        System.out.println(docListeners.length + " listeners  at \"" +
                name  + "\"");
        for (DocumentListener l : docListeners) {
            System.out.println("\t" + l.getClass().getName() + " 0x" +
                    Long.toHexString(System.identityHashCode(l)));
        }
        return docListeners.length;
    }
}
