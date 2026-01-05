/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4655996
 * @summary tests that getting the system clipboard contents doesn't cause
 *          IOException if there is no clipboard owner or the owner doesn't
 *          export any target types
 * @key headful
 * @library /test/lib
 * @run main NoOwnerNoTargetsTest
 */

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class NoOwnerNoTargetsTest implements ClipboardOwner {

    final Clipboard clipboard =
        Toolkit.getDefaultToolkit().getSystemClipboard();
    public static final int CLIPBOARD_DELAY = 1000;

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            NoOwnerNoTargetsTest test = new NoOwnerNoTargetsTest();
            test.execute();
            return;
        }

        new NoOwnerNoTargetsTest().start();
    }

    public void execute() {
        final ClipboardOwner clipboardOwner = new ClipboardOwner() {
                public void lostOwnership(Clipboard clip,
                                          Transferable contents) {
                    System.exit(0);
                }
            };
        final Transferable emptyTransferable = new Transferable() {
                public DataFlavor[] getTransferDataFlavors() {
                    return new DataFlavor[0];
                }
                public boolean isDataFlavorSupported(DataFlavor df) {
                    return false;
                }
                public Object getTransferData(DataFlavor df)
                  throws UnsupportedFlavorException {
                    throw new UnsupportedFlavorException(df);
                }
            };

        clipboard.setContents(emptyTransferable, clipboardOwner);
        final Object o = new Object();
        synchronized (o) {
            try {
                o.wait();
            } catch (InterruptedException ie) {
                ie.printStackTrace();
            }
        }
    }

    public void start() throws Exception {
        clipboard.getContents(null);

        Transferable transferable = new StringSelection("TEXT");
        clipboard.setContents(transferable, this);

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                NoOwnerNoTargetsTest.class.getName(),
                "child"
        );

        Process process = ProcessTools.startProcess("Child", pb);
        OutputAnalyzer outputAnalyzer = new OutputAnalyzer(process);

        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new TimeoutException("Timed out waiting for Child");
        }

        outputAnalyzer.shouldHaveExitValue(0);
    }

    public void lostOwnership(Clipboard clip, Transferable contents) {
        final Transferable transferable = new StringSelection("TEXT");
        final Runnable r = () -> {
            try {
                Thread.sleep(CLIPBOARD_DELAY);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            clipboard.getContents(null);
            clipboard.setContents(transferable, null);
        };
        final Thread t = new Thread(r);
        t.start();
    }
}
