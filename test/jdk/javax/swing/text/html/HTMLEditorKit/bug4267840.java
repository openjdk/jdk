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

/* @test
   @bug 4267840
   @summary Tests how HTMLEditorKit.write() works on small documents
   @run main bug4267840
*/

import javax.swing.JTextPane;
import javax.swing.text.EditorKit;
import javax.swing.SwingUtilities;
import java.io.File;
import java.io.FileOutputStream;

public class bug4267840 {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            final JTextPane textpane = new JTextPane();
            textpane.setContentType("text/html");
            final EditorKit kit = textpane.getEditorKit();

            textpane.setText("A word");
            File file = new File("bug4267840.out");
            try {
                FileOutputStream out = new FileOutputStream(file);
                kit.write(out, textpane.getDocument(), 0,
                          textpane.getDocument().getLength());
                out.close();
            } catch (Exception e) {}
            try {
                if (file.length() < 6) {  // simply can't be
                    throw new RuntimeException("Failed: " +
                                          " HTMLEditorKit.write() is broken");
                }
            } finally {
                file.delete();
            }
        });
    }
}
