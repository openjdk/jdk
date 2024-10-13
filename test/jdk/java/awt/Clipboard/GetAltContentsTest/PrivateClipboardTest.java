/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4287795 4790833
  @summary tests new Clipboard methods: getAvailableDataFlavors,
           isDataFlavorAvailable, getData
  @run main PrivateClipboardTest
*/

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class PrivateClipboardTest {

    public static void main(String[] args) {
        boolean failed = false;
        final Clipboard clipboard = new Clipboard("local");

        if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
            failed = true;
            System.err.println("FAILURE: isDataFlavorAvailable() returns " +
                               "true for empty clipboard");
        }

        try {
            clipboard.getData(DataFlavor.stringFlavor);
            failed = true;
            System.err.println("FAILURE: getData() does not throw " +
                    "UnsupportedFlavorException for empty clipboard");
        } catch (UnsupportedFlavorException exc) {
            System.err.println("getData() for empty clipboard throw " +
                               "UnsupportedFlavorException correctly: " + exc);
        } catch (IOException exc) {
            failed = true;
            exc.printStackTrace();
        }

        if (clipboard.getAvailableDataFlavors() == null ||
                clipboard.getAvailableDataFlavors().length != 0) {
            failed = true;
            System.err.println("FAILURE: getAvailableDataFlavors() does not " +
                    "return zero-length array for empty clipboard: " +
                    Arrays.toString(clipboard.getAvailableDataFlavors()));
        }

        final String contentsText = "contents text";

        clipboard.setContents(new StringSelection(contentsText), null);

        Transferable contents = clipboard.getContents(null);
        Set<DataFlavor> flavorsT = new HashSet<>(
                Arrays.asList(contents.getTransferDataFlavors()));
        Set<DataFlavor> flavorsA = new HashSet<>(
                Arrays.asList(clipboard.getAvailableDataFlavors()));
        System.err.println("getAvailableDataFlavors(): " + flavorsA);
        if (!flavorsA.equals(flavorsT)) {
            failed = true;
            System.err.println(
                    "FAILURE: getAvailableDataFlavors() returns incorrect " +
                    "DataFlavors: " + flavorsA + "\nwhile getContents()." +
                    "getTransferDataFlavors() return: " + flavorsT);
        }

        if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
            failed = true;
            System.err.println(
                    "FAILURE: isDataFlavorAvailable(DataFlavor.stringFlavor) " +
                               "returns false");
        }

        Object data = null;
        try {
            data = clipboard.getData(DataFlavor.stringFlavor);
        } catch (UnsupportedFlavorException exc) {
            failed = true;
            exc.printStackTrace();
        } catch (IOException exc) {
            failed = true;
            exc.printStackTrace();
        }
        System.err.println("getData(): " + data);
        if (!contentsText.equals(data)) {
            failed = true;
            System.err.println("FAILURE: getData() returns: " + data +
                               ", that is not equal to: \"" + contentsText + "\"");
        }

        if (failed) {
            throw new RuntimeException("test failed, for details see output above");
        }
     }
}
