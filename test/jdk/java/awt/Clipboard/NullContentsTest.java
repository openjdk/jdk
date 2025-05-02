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
  @test
  @bug 4378007 4250859
  @summary Verifies that setting the contents of the system Clipboard to null
           throws a NullPointerException
  @key headful
  @run main NullContentsTest
*/

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;

public class NullContentsTest {

    public static void main(String[] args) {
        // Clipboard.setContents(null, foo) should throw an NPE, but
        // Clipboard.setContents(bar, foo), where bar.getTransferData(baz)
        // returns null, should not.
        Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
        try {
            clip.setContents(null, null);
        } catch (NullPointerException e) {
            StringSelection ss = new StringSelection(null);
            try {
                clip.setContents(ss, null);
            } catch (NullPointerException ee) {
                throw new RuntimeException("test failed: null transfer data");
            }
            System.err.println("test passed");
            return;
        }
        throw new RuntimeException("test failed: null Transferable");
    }
}
