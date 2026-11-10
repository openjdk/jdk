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

/*
 * @test
 * @bug 8393918
 * @summary Verifies BasicListUI exports literal list text
 *          escaping HTML special characters when copying
 * @run main BasicListTransferHandlerBug
 */

import javax.swing.JList;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.plaf.basic.BasicListUI;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;

public class BasicListTransferHandlerBug {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            String actual = null, expected = null;
            try {
                JList<String> list = new JList<>(new String[]{"<b>literal</b>&"});
                list.setUI(new BasicListUI());
                list.setSelectedIndex(0);
                Clipboard clipboard = new Clipboard("test");
                list.getTransferHandler().exportToClipboard(list, clipboard, TransferHandler.COPY);
                actual = (String) clipboard.getData(
                         new DataFlavor("text/html;class=java.lang.String", "HTML"));
                expected = "&lt;b&gt;literal&lt;/b&gt;&amp;";
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            if (!actual.contains(expected)) {
                throw new RuntimeException("Expected: " + expected + "\nActual: " + actual);
            }
        });
    }
}
