/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
  @bug 8332271
  @summary tests that concurrent access to the clipboard does not crash the JVM
  @key headful
  @requires (os.family == "windows")
  @run main ConcurrentClipboardAccessTest
 */
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;

public class ConcurrentClipboardAccessTest {

    public static void main(String[] args) {
        Thread clipboardLoader1 = new Thread(new ClipboardLoader());
        clipboardLoader1.setDaemon(true);
        clipboardLoader1.start();
        Thread clipboardLoader2 = new Thread(new ClipboardLoader());
        clipboardLoader2.setDaemon(true);
        clipboardLoader2.start();
        long start = System.currentTimeMillis();
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
            long now = System.currentTimeMillis();
            if ((now - start) > (10L * 1000L)) {
                break;
            }
        }
        // Test is considered successful if the concurrent repeated reading
        // from clipboard succeeds for the allotted time and the JVM does not
        // crash.
        System.out.println("Shutdown normally");
    }

    public static class ClipboardLoader implements Runnable {

        @Override
        public void run() {
            final Clipboard systemClipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            while (true) {
                try {
                    if (systemClipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                        systemClipboard.getData(DataFlavor.stringFlavor);
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }
}
