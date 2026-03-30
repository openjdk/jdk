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


import javax.swing.JTextPane;
import javax.swing.event.DocumentListener;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

/*
 * @test
 * @bug 8380790
 * @summary make sure getAccessibleText() doesn't add DocumentListeners
 * @run main GetAccessibleTextAddsDocumentListeners
 */

public class GetAccessibleTextAddsDocumentListeners {
    public static void main(String[] args) throws Exception{
        JTextPane textPane = new JTextPane();
        textPane.setContentType("text/html");
        HTMLDocument doc1 = (HTMLDocument) textPane.getDocument();
        int listenerCountA = log("A", doc1.getDocumentListeners());
        for (int a = 0; a < 10_000; a++) {
            textPane.getAccessibleContext().getAccessibleText();
        }
        int listenerCountB = log("B", doc1.getDocumentListeners());
        if (listenerCountB > listenerCountA + 1_000 ||
            listenerCountB == listenerCountA) {
            throw new Exception();
        }
        HTMLEditorKit kit = (HTMLEditorKit) textPane.getEditorKit();
        HTMLDocument doc2 = (HTMLDocument) kit.createDefaultDocument();
        textPane.setDocument(doc2);
        int listenerCountC = log("C", doc2.getDocumentListeners());
        for (int a = 0; a < 10_000; a++) {
            textPane.getAccessibleContext().getAccessibleText();
        }
        int listenerCountD = log("D", doc2.getDocumentListeners());
        if (listenerCountD > listenerCountC + 1_000 ||
                listenerCountD == listenerCountC) {
            throw new Exception();
        }
    }

    private static int log(String name, DocumentListener[] listeners) {
        System.out.println(listeners.length + " listeners  at \"" +
                name  + "\"");
        for (DocumentListener l : listeners) {
            System.out.println("\t" + l.getClass().getName() + " 0x" +
                    Long.toHexString(System.identityHashCode(l)));
        }
        return listeners.length;
    }
}
