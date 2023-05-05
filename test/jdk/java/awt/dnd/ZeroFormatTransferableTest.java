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

/*
  @test
  @bug 4388802
  @summary tests that getting clipboard data doesn't crash when there are no
           formats on the clipboard
  @key headful
  @run main ZeroFormatTransferableTest
*/

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;


public class ZeroFormatTransferableTest {
    public static void main(String[] args) throws InterruptedException, IOException {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new ZeroFormatTransferable(), null);

        String javaPath = System.getProperty("java.home", "");

        Process process = new ProcessBuilder(
                javaPath + File.separator + "bin" + File.separator + "java",
                "-cp",
                System.getProperty("test.classes", "."),
                "ZeroFormatTransferableTest").start();
        process.waitFor();

        InputStream errorStream = process.getErrorStream();
        int count = errorStream.available();
        if (count > 0) {
            byte[] b = new byte[count];
            errorStream.read(b);
            System.err.println("========= Child VM System.err ========");
            System.err.print(new String(b));
            System.err.println("======================================");
        }
    }
}

class ZeroFormatTransferable implements Transferable {
    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[] {};
    }

    public boolean isDataFlavorSupported(DataFlavor df) {
        return false;
    }

    public Object getTransferData(DataFlavor df)
      throws UnsupportedFlavorException {
        throw new UnsupportedFlavorException(df);
    }
}
