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


import javax.accessibility.AccessibleContext;
import javax.swing.JTextPane;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.html.HTMLDocument;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/*
 * @test
 * @bug 8385367
 * @summary make sure setDocument() doesn't reattach obsolete DocumentListener
 * @run main TestAddingOldAccessibleContextAsDocumentListener
 */

public class TestAddingOldAccessibleContextAsDocumentListener {
    public static void main(String[] args) throws Exception {
        JTextPane textPane = new JTextPane();

        // This installs an HTMLDocument
        textPane.setContentType("text/html");
        HTMLDocument htmlDoc =
                (HTMLDocument) textPane.getDocument();

        // this instantiates the AccessibleContext
        AccessibleContext htmlAXContext = textPane.getAccessibleContext();

        // this replaces the Document:
        textPane.setContentType("text/plain");
        DefaultStyledDocument plainDoc =
                (DefaultStyledDocument) textPane.getDocument();

        if (htmlAXContext == textPane.getAccessibleContext()) {
            throw new RuntimeException(
                    "this test assumes the AccessibleContext changed when " +
                            "the document changed");
        }

        List<DocumentListener> docListeners = Arrays.asList(
                plainDoc.getDocumentListeners());
        if (docListeners.contains(htmlAXContext)) {
            throw new Exception("outdated listener added to new document");
        }

        AccessibleContext pax = textPane.getAccessibleContext();
        // in this scenario we've call doc.addDocumentListener(pax) twice;
        // let's be sure it's only listed once.
        if (Collections.frequency(docListeners, pax) != 1) {
            throw new Exception("new listener was added multiple times");
        }

        // this is optional (we don't care about the HTMLDocument anymore).
        // setDocument() automatically removes the old AX context listener
        List<DocumentListener> htmlDocListeners = Arrays.asList(
                htmlDoc.getDocumentListeners());
        if (Collections.frequency(docListeners, htmlAXContext) != 0) {
            throw new Exception("outdated listener was not removed");
        }

        System.out.println("test passed");
    }
}